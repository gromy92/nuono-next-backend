package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonEgressUnavailableException;
import com.nuono.next.noon.NoonOperationException;
import com.nuono.next.officialwarehouse.OfficialWarehouseRecords.AppointmentRecord;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class OfficialWarehouseAppointmentRetryPolicy {
    private static final int RETRY_CAP_SECONDS = 1800;

    private OfficialWarehouseAppointmentRetryPolicy() {
    }

    static boolean shouldRetry(AppointmentRecord appointment, String failureType, LocalDate today) {
        if (failureType != null && failureType.startsWith("NOON_ASN_")) {
            return false;
        }
        return appointment.apEndDateValue == null || !today.isAfter(appointment.apEndDateValue);
    }

    static int nextRetrySeconds(int baseRetrySeconds, AppointmentRecord appointment) {
        return nextRetrySeconds(
                baseRetrySeconds,
                appointment,
                "SCHEDULE",
                "SCHEDULE_APPOINTMENT",
                null
        );
    }

    static int nextRetrySeconds(
            int baseRetrySeconds,
            AppointmentRecord appointment,
            String errorStage,
            String failureType,
            String errorMessage
    ) {
        if (isNoCapacity(failureType)) {
            return 0;
        }
        int safeBase = baseRetrySeconds <= 0 ? 5 : baseRetrySeconds;
        int previousAttemptCount = appointment == null || appointment.attemptCount == null
                ? 0
                : Math.max(0, appointment.attemptCount);
        int failedAttemptsAfterCurrentRun = previousAttemptCount + 1;
        long multiplier = 1L << Math.min(30, failedAttemptsAfterCurrentRun);
        long seconds = (long) safeBase * multiplier;
        return (int) Math.min(seconds, RETRY_CAP_SECONDS);
    }

    static String failureType(String errorStage, String failureType, String errorMessage) {
        if ("NOON_NO_CAPACITY".equalsIgnoreCase(normalize(failureType))) {
            return "NO_CAPACITY";
        }
        if (NoonEgressUnavailableException.BLOCKED_FAILURE_CODE.equalsIgnoreCase(normalize(failureType))) {
            return "NOON_ACCESS_BLOCKED";
        }
        if (NoonEgressUnavailableException.FAILURE_CODE.equalsIgnoreCase(normalize(failureType))) {
            return "NOON_ACCESS_FAILURE";
        }
        if (isAccessBlocked(errorStage, failureType, errorMessage)) {
            return "NOON_ACCESS_BLOCKED";
        }
        if (isAccessFailure(errorStage, failureType, errorMessage)) {
            return "NOON_ACCESS_FAILURE";
        }
        return failureType;
    }

    static boolean isRetryableNoonCallFailure(String retryFailureType) {
        return isAccessFailureType(retryFailureType);
    }

    static String errorStage(String fallbackStage, String retryFailureType) {
        if (isNoCapacity(retryFailureType)) {
            return "SCHEDULE";
        }
        return isAccessFailureType(retryFailureType) ? "NOON_ACCESS" : fallbackStage;
    }

    static String noonFailureType(Exception exception) {
        if (exception instanceof NoonOperationException) {
            return ((NoonOperationException) exception).getClassification().getCode();
        }
        if (exception instanceof NoonEgressUnavailableException) {
            return ((NoonEgressUnavailableException) exception).getFailureCode();
        }
        return exception == null ? "UNKNOWN" : exception.getClass().getSimpleName();
    }

    static boolean isNoCapacity(String failureType) {
        return "NO_CAPACITY".equalsIgnoreCase(normalize(failureType));
    }

    private static boolean isAccessBlocked(String errorStage, String failureType, String errorMessage) {
        String combined = retryText(errorStage, failureType, errorMessage);
        return combined.contains("noon_egress_blocked")
                || combined.contains("http 407")
                || combined.contains("proxy authentication")
                || combined.contains("tunnel failed");
    }

    private static boolean isAccessFailure(String errorStage, String failureType, String errorMessage) {
        if (isAccessBlocked(errorStage, failureType, errorMessage)) {
            return true;
        }
        String combined = retryText(errorStage, failureType, errorMessage);
        return combined.contains("noon_egress_unavailable")
                || combined.contains("io_exception")
                || combined.contains("connection reset")
                || combined.contains("connection refused")
                || combined.contains("connect timed out")
                || combined.contains("request timed out")
                || combined.contains("read timed out")
                || combined.contains("no route to host")
                || combined.contains("buffer_underflow")
                || combined.contains("header parser received no bytes")
                || combined.contains("with eof")
                || combined.contains("non decrypted")
                || combined.contains("eof reached")
                || combined.contains("unexpected end")
                || combined.contains("connection closed")
                || combined.contains("closed channel")
                || combined.contains("http 408")
                || combined.contains("http 500")
                || combined.contains("http 502")
                || combined.contains("http 503")
                || combined.contains("http 504");
    }

    private static boolean isAccessFailureType(String retryFailureType) {
        return "NOON_ACCESS_BLOCKED".equalsIgnoreCase(normalize(retryFailureType))
                || "NOON_ACCESS_FAILURE".equalsIgnoreCase(normalize(retryFailureType));
    }

    private static String retryText(String errorStage, String failureType, String errorMessage) {
        return (String.valueOf(errorStage) + " " + String.valueOf(failureType) + " " + String.valueOf(errorMessage))
                .toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
