package com.nuono.next.outboundfee;

public enum OfficialOutboundFeeFactStatus {
    ACTIVE("ACTIVE"),
    SUPERSEDED("SUPERSEDED"),
    DISABLED("DISABLED"),
    PENDING_MANUAL_CONFIRM("PENDING_MANUAL_CONFIRM"),
    CONFLICT("CONFLICT");

    private final String value;

    OfficialOutboundFeeFactStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
