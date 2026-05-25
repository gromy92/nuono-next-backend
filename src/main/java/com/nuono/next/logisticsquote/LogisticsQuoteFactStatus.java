package com.nuono.next.logisticsquote;

public enum LogisticsQuoteFactStatus {
    ACTIVE("ACTIVE"),
    SUPERSEDED("SUPERSEDED"),
    DISABLED("DISABLED"),
    PENDING_MANUAL_CONFIRM("PENDING_MANUAL_CONFIRM"),
    CONFLICT("CONFLICT");

    private final String value;

    LogisticsQuoteFactStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
