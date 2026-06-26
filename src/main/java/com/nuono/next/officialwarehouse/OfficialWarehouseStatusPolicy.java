package com.nuono.next.officialwarehouse;

import java.util.Locale;
import org.springframework.util.StringUtils;

final class OfficialWarehouseStatusPolicy {

    private OfficialWarehouseStatusPolicy() {
    }

    static String normalizeNoonAsnStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    static boolean isNoonAsnScheduledStatus(String status) {
        String normalized = normalizeNoonAsnStatus(status);
        return "SCHEDULED".equals(normalized)
                || "HANDED_OVER".equals(normalized)
                || "RECEIVING".equals(normalized)
                || "GRN_COMPLETED".equals(normalized);
    }

    static boolean isNoonAsnReadyForAppointmentStatus(String status) {
        return "SEALED".equals(normalizeNoonAsnStatus(status));
    }

    static boolean isNoonAsnFailureStatus(String status) {
        String normalized = normalizeNoonAsnStatus(status);
        return "EXPIRED".equals(normalized)
                || "CANCELED".equals(normalized)
                || "CANCELLED".equals(normalized);
    }

    static String localAsnStatusFromNoon(String status) {
        String normalized = normalizeNoonAsnStatus(status);
        if (isNoonAsnFailureStatus(normalized)) {
            return "FAILED";
        }
        if ("CREATED".equals(normalized)) {
            return "ASN_CREATED";
        }
        return "LINES_CREATED";
    }

    static String normalizeAppointmentCorrectionStatus(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("请选择订正状态。");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("SCHEDULED".equals(normalized)
                || "FAILED".equals(normalized)
                || "CANCELED".equals(normalized)
                || "PENDING".equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("约仓订正状态不支持：" + normalized);
    }
}
