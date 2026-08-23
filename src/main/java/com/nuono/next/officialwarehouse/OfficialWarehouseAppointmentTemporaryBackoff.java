package com.nuono.next.officialwarehouse;

import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

final class OfficialWarehouseAppointmentTemporaryBackoff {
    static final String STAGE = "NOON_TEMPORARY_BACKOFF";
    private static final String SOURCE = "OFFICIAL_WAREHOUSE_APPOINTMENT";

    private final NoonRiskBackoffGuard guard;

    OfficialWarehouseAppointmentTemporaryBackoff(NoonRiskBackoffGuard guard) {
        this.guard = guard == null ? NoonRiskBackoffGuard.disabled() : guard;
    }

    NoonRiskBackoffHold activeHold(AppointmentRecord appointment) {
        String failureType = failureType(appointment == null ? null : appointment.failureType);
        if (!OfficialWarehouseAppointmentRetryPolicy.isRetryableNoonCallFailure(failureType)) {
            return null;
        }
        return guard.currentExactHold(scope(appointment, failureType)).orElse(null);
    }

    int nextRetrySeconds(AppointmentRecord appointment, String failureType, String errorMessage) {
        if (OfficialWarehouseAppointmentRetryPolicy.isNoCapacity(failureType)) {
            return 0;
        }
        NoonRiskBackoffHold hold = guard.recordScopedSignal(
                scope(appointment, failureType),
                failureType,
                SOURCE,
                appointment == null ? null : appointment.id,
                diagnostic(appointment, errorMessage)
        );
        return retrySeconds(hold);
    }

    void resetAfterSuccess(AppointmentRecord appointment) {
        String failureType = failureType(appointment == null ? null : appointment.failureType);
        if (OfficialWarehouseAppointmentRetryPolicy.isRetryableNoonCallFailure(failureType)) {
            guard.recordScopedSuccess(scope(appointment, failureType), SOURCE);
        }
    }

    int retrySeconds(NoonRiskBackoffHold hold) {
        if (hold == null || hold.getBlockedUntil() == null) {
            return 120;
        }
        long milliseconds = Duration.between(
                LocalDateTime.now(Clock.systemUTC()), hold.getBlockedUntil()
        ).toMillis();
        return milliseconds <= 0 ? 1 : (int) Math.min(Integer.MAX_VALUE, (milliseconds + 999L) / 1000L);
    }

    String failureType(String value) {
        return OfficialWarehouseAppointmentRetryPolicy.failureType("NOON_CALL", value, null);
    }

    String message(NoonRiskBackoffHold hold) {
        String type = hold == null || hold.getRiskType() == null ? "temporary_failure" : hold.getRiskType();
        String until = hold == null || hold.getBlockedUntil() == null ? "" : "，冷却至 " + hold.getBlockedUntil();
        String diagnostic = hold == null || hold.getDiagnosticSummary() == null ? "" : "，原因：" + hold.getDiagnosticSummary();
        String message = "Noon 临时故障退避中：" + type + until + diagnostic;
        return message.length() > 900 ? message.substring(0, 900) : message;
    }

    private NoonRiskBackoffScope scope(AppointmentRecord appointment, String failureType) {
        return NoonRiskBackoffScope.officialWarehouseTemporaryFailure(
                appointment == null ? null : appointment.ownerUserId,
                appointment == null ? null : appointment.storeCode,
                appointment == null ? null : appointment.siteCode,
                failureType
        );
    }

    private String diagnostic(AppointmentRecord appointment, String errorMessage) {
        String id = appointment == null ? "?" : String.valueOf(appointment.id);
        String asn = appointment == null ? "?" : String.valueOf(appointment.noonAsnNr);
        return "appointmentId=" + id + " asn=" + asn + " failed=" + errorMessage;
    }
}
