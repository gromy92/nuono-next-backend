package com.nuono.next.officialwarehouse;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public class OfficialWarehouseAppointmentRunner {

    private static final int MAX_SEALED_CHECK_ATTEMPTS = 5;
    private static final long SEALED_CHECK_INTERVAL_MS = 1200L;

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
        applyAsnWarehouseFrom(task, detail);
        String status = normalize(detail == null ? null : detail.status);
        if (isNoonFailureStatus(status)) {
            return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
        }
        if (isNoonScheduledStatus(status)) {
            return RunResult.alreadyScheduled();
        }
        RunResult readiness = setWarehousesAndWaitUntilReady(task, client);
        if (readiness != null) {
            return readiness;
        }

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
                if (!matchesTimeRange(slot, acceptedHours)) {
                    continue;
                }
                RunResult scheduled = scheduleAndConfirm(task, client, capacityDate, slot);
                if (scheduled != null) {
                    return scheduled;
                }
            }
        }
        return RunResult.failed("NO_CAPACITY", "没有匹配的 Noon 可约仓日期或时段。");
    }

    public List<AvailableSlot> queryAvailability(AppointmentTask task, NoonAppointmentClient client) {
        if (task == null || client == null) {
            return List.of();
        }
        AsnDetail detail = client.queryAsnDetail(task);
        applyAsnWarehouseFrom(task, detail);
        String status = normalize(detail == null ? null : detail.status);
        if (isNoonFailureStatus(status)) {
            return List.of();
        }
        RunResult readiness = setWarehousesAndWaitUntilReady(task, client);
        if (readiness != null) {
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
                    availableSlots.add(new AvailableSlot(capacityDate, slot.idSlot, slot.name, task.warehouseFrom, detail.warehouseFromCode));
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
        applyAsnWarehouseFrom(task, detail);
        String status = normalize(detail == null ? null : detail.status);
        if (isNoonFailureStatus(status)) {
            return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
        }
        if (isNoonScheduledStatus(status) && !client.reschedule(task)) {
            return RunResult.failed("RESCHEDULE_ASN", "Noon 取消当前约仓失败。");
        }
        RunResult readiness = setWarehousesAndWaitUntilReady(task, client);
        if (readiness != null) {
            return readiness;
        }
        RunResult scheduled = scheduleAndConfirm(task, client, appointmentDate, slot);
        return scheduled == null
                ? RunResult.failed("SCHEDULE_APPOINTMENT", "Noon 提交约仓失败。")
                : scheduled;
    }

    private static boolean inRange(AppointmentTask task, LocalDate date) {
        LocalDate start = task.apStartDate;
        LocalDate end = task.apEndDate;
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return end == null || !date.isAfter(end);
    }

    private static void applyAsnWarehouseFrom(AppointmentTask task, AsnDetail detail) {
        if (task != null
                && !StringUtils.hasText(task.warehouseFrom)
                && detail != null
                && StringUtils.hasText(detail.warehouseFrom)) {
            task.warehouseFrom = detail.warehouseFrom.trim();
        }
    }

    private static RunResult waitUntilReadyForSchedule(AppointmentTask task, NoonAppointmentClient client) {
        for (int attempt = 0; attempt < MAX_SEALED_CHECK_ATTEMPTS; attempt++) {
            AsnDetail detail = client.queryAsnDetail(task);
            applyAsnWarehouseFrom(task, detail);
            String status = normalize(detail == null ? null : detail.status);
            if (isNoonFailureStatus(status)) {
                return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可约仓：" + status);
            }
            if (isNoonReadyForScheduleStatus(status) || isNoonScheduledStatus(status)) {
                return null;
            }
            if (attempt + 1 < MAX_SEALED_CHECK_ATTEMPTS) {
                sleepBeforeNextSealedCheck();
            }
        }
        return RunResult.failed("ASN_NOT_SEALED", "Noon 已设置仓库，但 ASN 尚未 sealed，稍后再点立即约仓。");
    }

    private static RunResult setWarehousesAndWaitUntilReady(AppointmentTask task, NoonAppointmentClient client) {
        if (!client.setWarehouses(task)) {
            return RunResult.failed("SET_WAREHOUSES", "Noon 设置约仓仓库失败。");
        }
        client.onWarehousesSet(task);
        return waitUntilReadyForSchedule(task, client);
    }

    private static RunResult scheduleAndConfirm(
            AppointmentTask task,
            NoonAppointmentClient client,
            LocalDate appointmentDate,
            SlotCapacity slot
    ) {
        if (!client.schedule(task, appointmentDate, slot)) {
            return null;
        }
        AsnDetail confirmed = client.queryAsnDetail(task);
        applyAsnWarehouseFrom(task, confirmed);
        String confirmedStatus = normalize(confirmed == null ? null : confirmed.status);
        if (isNoonScheduledStatus(confirmedStatus)) {
            return RunResult.scheduled(appointmentDate, slot.idSlot, slot.name);
        }
        if (isNoonFailureStatus(confirmedStatus)) {
            return RunResult.failed("NOON_ASN_" + confirmedStatus, "Noon ASN 状态不可约仓：" + confirmedStatus);
        }
        return RunResult.failed(
                "SCHEDULE_NOT_CONFIRMED",
                "Noon 返回约仓提交成功，但 ASN 详情尚未确认已约仓，请稍后重试或在 Noon 后台核对。"
        );
    }

    private static void sleepBeforeNextSealedCheck() {
        try {
            Thread.sleep(SEALED_CHECK_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean matchesTimeRange(SlotCapacity slot, Set<Integer> acceptedHours) {
        if (acceptedHours == null || acceptedHours.isEmpty()) {
            return true;
        }
        if (slot == null || !StringUtils.hasText(slot.name)) {
            return false;
        }
        String[] parts = slot.name.split("-");
        if (parts.length != 2) {
            return false;
        }
        Integer start = parseHour(parts[0]);
        Integer end = parseHour(parts[1]);
        if (start == null || end == null) {
            return false;
        }
        int min = acceptedHours.stream().min(Integer::compareTo).orElse(0);
        int max = acceptedHours.stream().max(Integer::compareTo).orElse(23);
        return start >= min && end <= max;
    }

    private static Set<Integer> parseAcceptedHours(String apTimeRange) {
        if (!StringUtils.hasText(apTimeRange)) {
            return Set.of();
        }
        String[] values = apTimeRange.split(",");
        Set<Integer> hours = new LinkedHashSet<>();
        for (String value : values) {
            Integer hour = parseHour(value);
            if (hour != null) {
                hours.add(hour);
            }
        }
        return hours;
    }

    private static Integer parseHour(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("am") || normalized.endsWith("pm")) {
                boolean pm = normalized.endsWith("pm");
                String numberText = normalized.substring(0, normalized.length() - 2).trim();
                int hour = Integer.parseInt(numberText);
                if (hour < 1 || hour > 12) {
                    return null;
                }
                if (!pm) {
                    return hour == 12 ? 0 : hour;
                }
                return hour == 12 ? 12 : hour + 12;
            }
            int hour = Integer.parseInt(normalized);
            return hour >= 0 && hour <= 23 ? hour : null;
        } catch (NumberFormatException exception) {
            return null;
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

    private static boolean isNoonScheduledStatus(String status) {
        return "SCHEDULED".equals(status)
                || "HANDED_OVER".equals(status)
                || "RECEIVING".equals(status)
                || "GRN_COMPLETED".equals(status);
    }

    private static boolean isNoonReadyForScheduleStatus(String status) {
        return "SEALED".equals(status);
    }

    private static boolean isNoonFailureStatus(String status) {
        return "EXPIRED".equals(status)
                || "CANCELED".equals(status)
                || "CANCELLED".equals(status);
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
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
        public String warehouseFrom;
        public LocalDate apStartDate;
        public LocalDate apEndDate;
        public String apTimeRange;
        public boolean availableToday;
    }

    public static class AsnDetail {
        public final String status;
        public final String warehouseFrom;
        public final String warehouseFromCode;

        public AsnDetail(String status) {
            this(status, null, null);
        }

        public AsnDetail(String status, String warehouseFrom) {
            this(status, warehouseFrom, null);
        }

        public AsnDetail(String status, String warehouseFrom, String warehouseFromCode) {
            this.status = status;
            this.warehouseFrom = warehouseFrom;
            this.warehouseFromCode = warehouseFromCode;
        }
    }

    public static class SlotCapacity {
        public final Integer idSlot;
        public final String name;

        public SlotCapacity(Integer idSlot, String name) {
            this.idSlot = idSlot;
            this.name = name;
        }
    }

    public static class AvailableSlot {
        public final LocalDate capacityDate;
        public final Integer slotId;
        public final String name;
        public final String warehouseFrom;
        public final String warehouseFromCode;

        public AvailableSlot(LocalDate capacityDate, Integer slotId, String name) {
            this(capacityDate, slotId, name, null, null);
        }

        public AvailableSlot(LocalDate capacityDate, Integer slotId, String name, String warehouseFrom, String warehouseFromCode) {
            this.capacityDate = capacityDate;
            this.slotId = slotId;
            this.name = name;
            this.warehouseFrom = warehouseFrom;
            this.warehouseFromCode = warehouseFromCode;
        }
    }

    public static class RunResult {
        public String status;
        public LocalDate appointmentDate;
        public Integer slotId;
        public String appointmentTime;
        public String failureType;
        public String errorMessage;
        public boolean alreadyScheduled;

        private static RunResult scheduled(LocalDate appointmentDate, Integer slotId, String appointmentTime) {
            RunResult result = new RunResult();
            result.status = "SCHEDULED";
            result.appointmentDate = appointmentDate;
            result.slotId = slotId;
            result.appointmentTime = appointmentTime;
            return result;
        }

        private static RunResult alreadyScheduled() {
            RunResult result = scheduled(null, null, null);
            result.alreadyScheduled = true;
            return result;
        }

        private static RunResult failed(String failureType, String errorMessage) {
            RunResult result = new RunResult();
            result.status = "FAILED";
            result.failureType = failureType;
            result.errorMessage = errorMessage;
            return result;
        }
    }
}
