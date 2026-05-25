package com.nuono.next.noonpull;

public class NoonSalesRetentionCapability {
    private final int retentionDays;
    private final String source;

    private NoonSalesRetentionCapability(int retentionDays, String source) {
        this.retentionDays = retentionDays;
        this.source = source;
    }

    public static NoonSalesRetentionCapability defaultCapability() {
        return new NoonSalesRetentionCapability(90, "default");
    }

    public static NoonSalesRetentionCapability configured(int retentionDays) {
        return new NoonSalesRetentionCapability(retentionDays, "configured");
    }

    public static NoonSalesRetentionCapability providerDiscovered(int retentionDays) {
        return new NoonSalesRetentionCapability(retentionDays, "provider_discovered");
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public String getSource() {
        return source;
    }
}
