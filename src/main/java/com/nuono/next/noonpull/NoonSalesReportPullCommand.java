package com.nuono.next.noonpull;

import java.time.LocalDate;

public class NoonSalesReportPullCommand {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private LocalDate date;
    private NoonPullTriggerMode triggerMode;

    public static Builder builder() {
        return new Builder();
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public LocalDate getDate() {
        return date;
    }

    public NoonPullTriggerMode getTriggerMode() {
        return triggerMode;
    }

    public static class Builder {
        private final NoonSalesReportPullCommand command = new NoonSalesReportPullCommand();

        public Builder ownerUserId(Long ownerUserId) {
            command.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            command.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            command.siteCode = siteCode;
            return this;
        }

        public Builder date(LocalDate date) {
            command.date = date;
            return this;
        }

        public Builder triggerMode(NoonPullTriggerMode triggerMode) {
            command.triggerMode = triggerMode;
            return this;
        }

        public NoonSalesReportPullCommand build() {
            return command;
        }
    }
}
