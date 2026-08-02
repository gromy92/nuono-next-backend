package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AppointmentTask;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.AsnDetail;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.NoonAppointmentClient;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.RunResult;
import com.nuono.next.officialwarehouse.OfficialWarehouseAppointmentRunner.SlotCapacity;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAppointmentExecution {

    static final int MAX_SEALED_CHECK_ATTEMPTS = 5;
    private static final long SEALED_CHECK_INTERVAL_MS = 1200L;

    private OfficialWarehouseAppointmentExecution() {
    }

    static void applyPersistedState(AppointmentTask task, AppointmentRecord appointment) {
        task.availableToday = Boolean.TRUE.equals(appointment.availableToday);
        task.previousAppointmentDate = StringUtils.hasText(appointment.appointmentDate)
                ? LocalDate.parse(appointment.appointmentDate.trim()) : null;
        task.previousAppointmentTime = appointment.appointmentTime;
        task.rebookingRequested = task.previousAppointmentDate != null
                && StringUtils.hasText(appointment.apSuccessTime);
    }

    static RunResult rescheduleAndWaitUntilReady(
            AppointmentTask task,
            NoonAppointmentClient client
    ) {
        try {
            if (!client.reschedule(task)) {
                return RunResult.reconciliationRequired(
                        "RESCHEDULE_ASN",
                        "Noon 改约请求已发出，但结果未确认，请先在 Noon 后台核对。"
                );
            }
        } catch (RuntimeException exception) {
            return RunResult.reconciliationRequired(
                    "RESCHEDULE_ASN",
                    "Noon 改约请求已发出，但结果未确认，请先在 Noon 后台核对。"
            );
        }
        for (int attempt = 0; attempt < MAX_SEALED_CHECK_ATTEMPTS; attempt++) {
            AsnDetail detail;
            try {
                detail = client.queryAsnDetail(task);
            } catch (RuntimeException exception) {
                return RunResult.reconciliationRequired(
                        "RESCHEDULE_CONFIRMATION",
                        "Noon 已接受改约请求，但无法确认原预约已释放，请先在 Noon 后台核对。"
                );
            }
            String status = normalize(detail == null ? null : detail.status);
            if (isNoonReadyForScheduleStatus(status)) {
                return null;
            }
            if (isNoonFailureStatus(status) || isNoonPostAppointmentStatus(status)) {
                return RunResult.failed("NOON_ASN_" + status, "Noon ASN 状态不可重新约仓：" + status);
            }
            if (attempt + 1 < MAX_SEALED_CHECK_ATTEMPTS) {
                sleepBeforeNextSealedCheck();
            }
        }
        return RunResult.reconciliationRequired(
                "RESCHEDULE_NOT_CONFIRMED",
                "Noon 改约请求已发出，但原预约尚未确认释放，请先在 Noon 后台核对。"
        );
    }

    static boolean shouldReleaseExistingSchedule(AppointmentTask task, AsnDetail detail) {
        if (!task.rebookingRequested) {
            return false;
        }
        if (matchesPreviousAppointment(task, detail)) {
            return true;
        }
        return !matchesRequestedAppointment(task, detail);
    }

    private static boolean matchesPreviousAppointment(AppointmentTask task, AsnDetail detail) {
        if (detail == null
                || task.previousAppointmentDate == null
                || !task.previousAppointmentDate.equals(detail.appointmentDate)) {
            return false;
        }
        return !StringUtils.hasText(task.previousAppointmentTime)
                || sameAppointmentTime(task.previousAppointmentTime, detail.appointmentTime);
    }

    private static boolean matchesRequestedAppointment(AppointmentTask task, AsnDetail detail) {
        if (detail == null || detail.appointmentDate == null || !inRange(task, detail.appointmentDate)) {
            return false;
        }
        return matchesTimeRange(
                new SlotCapacity(null, detail.appointmentTime),
                parseAcceptedHours(task.apTimeRange)
        );
    }

    static RunResult scheduleAndConfirm(
            AppointmentTask task,
            NoonAppointmentClient client,
            LocalDate appointmentDate,
            SlotCapacity slot
    ) {
        boolean accepted;
        try {
            accepted = client.schedule(task, appointmentDate, slot);
        } catch (RuntimeException exception) {
            return RunResult.reconciliationRequired("SCHEDULE_APPOINTMENT", "Noon 约仓请求已发出，但结果未确认，请先在 Noon 后台核对。");
        }
        if (!accepted) {
            return RunResult.reconciliationRequired("SCHEDULE_APPOINTMENT", "Noon 约仓请求已发出，但结果未确认，请先在 Noon 后台核对。");
        }
        AsnDetail confirmed;
        try {
            confirmed = client.queryAsnDetail(task);
        } catch (RuntimeException exception) {
            return RunResult.reconciliationRequired("SCHEDULE_CONFIRMATION", "Noon 已接受约仓，但确认读取失败，请先在 Noon 后台核对。");
        }
        String confirmedStatus = normalize(confirmed == null ? null : confirmed.status);
        if (isNoonScheduledStatus(confirmedStatus)) {
            if (matchesConfirmedAppointment(confirmed, appointmentDate, slot)) {
                return RunResult.scheduled(
                        confirmed.appointmentDate,
                        slot.idSlot,
                        confirmed.appointmentTime
                );
            }
            return RunResult.reconciliationRequired(
                    "SCHEDULE_CONFIRMATION_MISMATCH",
                    "Noon 已显示约仓，但回读日期或时段与本次请求不一致，请先在 Noon 后台核对。"
            );
        }
        if (isNoonFailureStatus(confirmedStatus)) {
            return RunResult.failed("NOON_ASN_" + confirmedStatus, "Noon ASN 状态不可约仓：" + confirmedStatus);
        }
        return RunResult.reconciliationRequired(
                "SCHEDULE_NOT_CONFIRMED",
                "Noon 返回约仓提交成功，但 ASN 详情尚未确认已约仓，请稍后重试或在 Noon 后台核对。"
        );
    }

    static boolean inRange(AppointmentTask task, LocalDate date) {
        LocalDate start = task.apStartDate;
        LocalDate end = task.apEndDate;
        if (start != null && date.isBefore(start)) {
            return false;
        }
        return end == null || !date.isAfter(end);
    }

    static void sleepBeforeNextSealedCheck() {
        try {
            Thread.sleep(SEALED_CHECK_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    static boolean matchesTimeRange(SlotCapacity slot, Set<Integer> acceptedHours) {
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

    static Set<Integer> parseAcceptedHours(String apTimeRange) {
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

    private static boolean matchesConfirmedAppointment(
            AsnDetail confirmed,
            LocalDate appointmentDate,
            SlotCapacity slot
    ) {
        return confirmed != null
                && appointmentDate != null
                && appointmentDate.equals(confirmed.appointmentDate)
                && slot != null
                && sameAppointmentTime(slot.name, confirmed.appointmentTime);
    }

    private static boolean sameAppointmentTime(String expected, String actual) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(actual)) {
            return false;
        }
        return normalizeAppointmentTime(expected).equals(normalizeAppointmentTime(actual));
    }

    private static String normalizeAppointmentTime(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replaceAll("\\s+", "");
    }

    static boolean isNoonScheduledStatus(String status) {
        return "SCHEDULED".equals(status) || "HANDED_OVER".equals(status)
                || "RECEIVING".equals(status) || "GRN_COMPLETED".equals(status);
    }

    static boolean isNoonRebookableStatus(String status) {
        return "SCHEDULED".equals(status);
    }

    static boolean isNoonPostAppointmentStatus(String status) {
        return "HANDED_OVER".equals(status)
                || "RECEIVING".equals(status)
                || "GRN_COMPLETED".equals(status);
    }

    static boolean isNoonReadyForScheduleStatus(String status) {
        return "SEALED".equals(status);
    }

    static boolean isNoonFailureStatus(String status) {
        return "EXPIRED".equals(status)
                || "CANCELED".equals(status)
                || "CANCELLED".equals(status);
    }

    static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
