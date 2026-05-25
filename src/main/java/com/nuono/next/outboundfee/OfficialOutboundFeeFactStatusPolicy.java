package com.nuono.next.outboundfee;

import java.util.Map;

public class OfficialOutboundFeeFactStatusPolicy {

    public String initialStatus(Map<String, Object> payload) {
        return bool(payload == null ? null : payload.get("manualConfirmRequired"))
                ? OfficialOutboundFeeFactStatus.PENDING_MANUAL_CONFIRM.value()
                : OfficialOutboundFeeFactStatus.ACTIVE.value();
    }

    public String conflictStatus() {
        return OfficialOutboundFeeFactStatus.CONFLICT.value();
    }

    public String supersededStatus() {
        return OfficialOutboundFeeFactStatus.SUPERSEDED.value();
    }

    public String withdrawnStatus() {
        return OfficialOutboundFeeFactStatus.DISABLED.value();
    }

    public boolean isBusinessReadable(String status) {
        return OfficialOutboundFeeFactStatus.ACTIVE.value().equals(status);
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String text = value == null ? "" : String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }
}
