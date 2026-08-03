package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.MAX_SEALED_CHECK_ATTEMPTS;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.inRange;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonFailureStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonPostAppointmentStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonReadyForScheduleStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonRebookableStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.isNoonScheduledStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.matchesTimeRange;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.normalize;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.parseAcceptedHours;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.rescheduleAndWaitUntilReady;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.scheduleAndConfirm;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentExecution.sleepBeforeNextSealedCheck;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentScheduleMatch.automaticDecision;
import static com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentScheduleMatch.selectedDecision;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public class OfficialWarehouseAppointmentRunner {

    private final Clock clock;

    public OfficialWarehouseAppointmentRunner(Clock clock) {
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }
    public RunResult runOnce(AppointmentTask task, NoonAppointmentClient client) {
        if (task == null) {
            return RunResult.failed("VALIDATION", "缺少约仓任务。");
        }
        if (client == null) {
            return RunResult.failed("VALIDATION", "缺少 Noon 约仓客户端。");
        }
        AsnDetail detail = client.queryAsnDetail(task);
        String status = normalize(detail == null ? null : detail.status);
        if (isNoonFailureStatus(status)) {
            return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
        }
        if (isNoonPostAppointmentStatus(status)) {
            return RunResult.failed("NOON_ASN_" + status, "Noon ASN 已进入收货流程，不能重新约仓：" + status);
        }
        boolean oldScheduleReleased = false;
        if (isNoonRebookableStatus(status)) {
            RunResult existingSchedule = automaticDecision(task, detail);
            if (existingSchedule != null) {
                return existingSchedule;
            }
            RunResult releaseReadiness = rescheduleAndWaitUntilReady(task, client);
            if (releaseReadiness != null) {
                return releaseReadiness;
            }
            status = "SEALED";
            oldScheduleReleased = true;
        }
        boolean warehousesChanged = !isNoonReadyForScheduleStatus(status);
        RunResult readiness = warehousesChanged ? setWarehousesAndWaitUntilReady(task, client, false) : null;
        if (readiness != null) {
            return readiness;
        }
        try {
            List<LocalDate> capacityDates = client.queryDayCapacity(task).stream()
                    .map(OfficialWarehouseAppointmentRunner::parseDate)
                    .filter(date -> date != null)
                    .filter(date -> inRange(task, date))
                    .filter(date -> task.availableToday || !date.equals(LocalDate.now(clock)))
                    .collect(Collectors.toList());
            Set<Integer> acceptedHours = parseAcceptedHours(task.apTimeRange);
            for (LocalDate capacityDate : capacityDates) {
                List<SlotCapacity> slots = new ArrayList<>(client.querySlotCapacity(task, capacityDate));
                slots.sort(Comparator.comparingInt((SlotCapacity slot) -> slot.idSlot == null ? -1 : slot.idSlot).reversed());
                for (SlotCapacity slot : slots) {
                    if (matchesTimeRange(slot, acceptedHours)) {
                        return scheduleAndConfirm(task, client, capacityDate, slot);
                    }
                }
            }
        } catch (RuntimeException exception) {
            if (!warehousesChanged && !oldScheduleReleased) {
                throw exception;
            }
            if (oldScheduleReleased) {
                return RunResult.reconciliationRequired(
                        "RESCHEDULE_FOLLOW_UP",
                        "Noon 已释放原预约，但后续仓位读取失败，请先在 Noon 后台核对。"
                );
            }
            return RunResult.reconciliationRequired("SET_WAREHOUSES_FOLLOW_UP", "Noon 已接受设置仓库，但后续读取失败，请先在 Noon 后台核对。");
        }
        return RunResult.failed("NO_CAPACITY", "没有匹配的 Noon 可约仓日期或时段。");
    }
    public List<AvailableSlot> queryAvailability(AppointmentTask task, NoonAppointmentClient client) {
        if (task == null || client == null) {
            return List.of();
        }
        AsnDetail detail = client.queryAsnDetail(task);
        String status = normalize(detail == null ? null : detail.status);
        if (isNoonFailureStatus(status)) {
            return List.of();
        }
        if (!isNoonReadyForScheduleStatus(status) && !isNoonScheduledStatus(status)) {
            return List.of();
        }
        List<LocalDate> capacityDates = client.queryDayCapacity(task).stream()
                .map(OfficialWarehouseAppointmentRunner::parseDate)
                .filter(date -> date != null)
                .filter(date -> inRange(task, date))
                .filter(date -> task.availableToday || !date.equals(LocalDate.now(clock)))
                .collect(Collectors.toList());
        Set<Integer> acceptedHours = parseAcceptedHours(task.apTimeRange);
        List<AvailableSlot> availableSlots = new ArrayList<>();
        for (LocalDate capacityDate : capacityDates) {
            List<SlotCapacity> slots = new ArrayList<>(client.querySlotCapacity(task, capacityDate));
            slots.sort(Comparator.comparingInt((SlotCapacity slot) -> slot.idSlot == null ? -1 : slot.idSlot).reversed());
            for (SlotCapacity slot : slots) {
                if (matchesTimeRange(slot, acceptedHours)) {
                    availableSlots.add(new AvailableSlot(capacityDate, slot.idSlot, slot.name));
                }
            }
        }
        return availableSlots;
    }
    public RunResult scheduleSelectedSlot(
            AppointmentTask task,
            NoonAppointmentClient client,
            LocalDate appointmentDate,
            SlotCapacity slot
    ) {
        if (task == null) {
            return RunResult.failed("VALIDATION", "缺少约仓任务。");
        }
        if (client == null) {
            return RunResult.failed("VALIDATION", "缺少 Noon 约仓客户端。");
        }
        if (appointmentDate == null || slot == null || slot.idSlot == null) {
            return RunResult.failed("VALIDATION", "请选择可用仓位时段。");
        }
        AsnDetail detail = client.queryAsnDetail(task);
        String status = normalize(detail == null ? null : detail.status);
        if (isNoonFailureStatus(status)) {
            return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
        }
        if (isNoonPostAppointmentStatus(status)) {
            return RunResult.failed("NOON_ASN_" + status, "Noon ASN 已进入收货流程，不能重新约仓：" + status);
        }
        if (isNoonRebookableStatus(status)) {
            RunResult existingSchedule = selectedDecision(task, detail, appointmentDate, slot);
            if (existingSchedule != null) {
                return existingSchedule;
            }
            RunResult rescheduleReadiness = rescheduleAndWaitUntilReady(task, client);
            if (rescheduleReadiness != null) {
                return rescheduleReadiness;
            }
            status = "SEALED";
        }
        RunResult readiness = isNoonReadyForScheduleStatus(status) ? null
                : setWarehousesAndWaitUntilReady(task, client, true);
        if (readiness != null) {
            return readiness;
        }
        return scheduleAndConfirm(task, client, appointmentDate, slot);
    }
    private static RunResult waitUntilReadyForSchedule(AppointmentTask task,
            NoonAppointmentClient client, boolean selectedSlot) {
        for (int attempt = 0; attempt < MAX_SEALED_CHECK_ATTEMPTS; attempt++) {
            AsnDetail detail = client.queryAsnDetail(task);
            String status = normalize(detail == null ? null : detail.status);
            if (isNoonFailureStatus(status)) {
                return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
            }
            if (isNoonReadyForScheduleStatus(status)) {
                return null;
            }
            if (isNoonScheduledStatus(status)) {
                return selectedSlot
                        ? RunResult.reconciliationRequired("NOON_ALREADY_SCHEDULED_DURING_PREPARATION",
                            "等待 Noon 仓库准备期间 ASN 已被约仓，请核对后再显式改约。")
                        : RunResult.alreadyScheduled(detail);
            }
            if (attempt + 1 < MAX_SEALED_CHECK_ATTEMPTS) {
                sleepBeforeNextSealedCheck();
            }
        }
        return RunResult.failed("ASN_NOT_SEALED", "Noon 已设置仓库，但 ASN 尚未 sealed，稍后再点立即约仓。");
    }
    private static RunResult setWarehousesAndWaitUntilReady(AppointmentTask task,
            NoonAppointmentClient client, boolean selectedSlot) {
        try {
            if (!client.setWarehouses(task)) {
                return RunResult.reconciliationRequired("SET_WAREHOUSES", "Noon 设置仓库请求已发出，但结果未确认，请先在 Noon 后台核对。");
            }
            client.onWarehousesSet(task);
            RunResult readiness = waitUntilReadyForSchedule(task, client, selectedSlot);
            if (readiness != null && !readiness.alreadyScheduled) {
                readiness.reconciliationRequired = true;
            }
            return readiness;
        } catch (RuntimeException exception) {
            return RunResult.reconciliationRequired("SET_WAREHOUSES", "Noon 设置仓库请求已发出，但结果未确认，请先在 Noon 后台核对。");
        }
    }
    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception exception) {
            return null;
        }
    }

    public interface NoonAppointmentClient {
        AsnDetail queryAsnDetail(AppointmentTask task);
        List<String> queryDayCapacity(AppointmentTask task);
        List<SlotCapacity> querySlotCapacity(AppointmentTask task, LocalDate capacityDate);
        boolean setWarehouses(AppointmentTask task);
        default void onWarehousesSet(AppointmentTask task) {
        }
        boolean reschedule(AppointmentTask task);
        boolean schedule(AppointmentTask task, LocalDate capacityDate, SlotCapacity slot);
    }

    public static class AppointmentTask {
        public Long appointmentId;
        public Long asnId;
        public String noonAsnNr;
        public Integer totalUnits;
        public String warehouseTo;
        public String warehouseToCode;
        public LocalDate apStartDate;
        public LocalDate apEndDate;
        public String apTimeRange;
        public boolean availableToday;
        public boolean rebookingRequested;
        public LocalDate previousAppointmentDate;
        public String previousAppointmentTime;
    }

    public static class AsnDetail {
        public final String status; public final LocalDate appointmentDate; public final String appointmentTime;
        public AsnDetail(String status) { this(status, null, null); }
        public AsnDetail(String status, LocalDate appointmentDate, String appointmentTime) {
            this.status = status; this.appointmentDate = appointmentDate; this.appointmentTime = appointmentTime;
        }
    }

    public static class SlotCapacity {
        public final Integer idSlot; public final String name;
        public SlotCapacity(Integer idSlot, String name) {
            this.idSlot = idSlot; this.name = name;
        }
    }

    public static class AvailableSlot {
        public final LocalDate capacityDate; public final Integer slotId;
        public final String name;
        public AvailableSlot(LocalDate capacityDate, Integer slotId, String name) {
            this.capacityDate = capacityDate; this.slotId = slotId; this.name = name;
        }
    }

    public static class RunResult {
        public String status; public LocalDate appointmentDate;
        public Integer slotId; public String appointmentTime;
        public String failureType; public String errorMessage;
        public boolean alreadyScheduled, reconciliationRequired;
        static RunResult scheduled(LocalDate appointmentDate, Integer slotId, String appointmentTime) {
            RunResult result = new RunResult();
            result.status = "SCHEDULED"; result.appointmentDate = appointmentDate;
            result.slotId = slotId; result.appointmentTime = appointmentTime;
            return result;
        }
        static RunResult alreadyScheduled(AsnDetail detail) {
            RunResult result = scheduled(detail.appointmentDate, null, detail.appointmentTime);
            result.alreadyScheduled = true; return result;
        }
        static RunResult failed(String failureType, String errorMessage) {
            RunResult result = new RunResult();
            result.status = "FAILED"; result.failureType = failureType;
            result.errorMessage = errorMessage;
            return result;
        }
        static RunResult reconciliationRequired(String failureType, String errorMessage) {
            RunResult result = failed(failureType, errorMessage);
            result.reconciliationRequired = true; return result;
        }
    }
}
