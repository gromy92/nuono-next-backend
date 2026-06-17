package com.nuono.next.noonlog;

public class NoonHttpCallLogContext {

    public String sourceModule;
    public String operation;
    public Long ownerUserId;
    public String storeCode;
    public String siteCode;
    public String projectCode;
    public String partnerId;
    public String businessType;
    public String businessId;
    public String businessRef;
    public String requestSummaryJson;

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final NoonHttpCallLogContext context = new NoonHttpCallLogContext();

        public Builder sourceModule(String value) {
            context.sourceModule = value;
            return this;
        }

        public Builder operation(String value) {
            context.operation = value;
            return this;
        }

        public Builder ownerUserId(Long value) {
            context.ownerUserId = value;
            return this;
        }

        public Builder storeCode(String value) {
            context.storeCode = value;
            return this;
        }

        public Builder siteCode(String value) {
            context.siteCode = value;
            return this;
        }

        public Builder projectCode(String value) {
            context.projectCode = value;
            return this;
        }

        public Builder partnerId(String value) {
            context.partnerId = value;
            return this;
        }

        public Builder businessType(String value) {
            context.businessType = value;
            return this;
        }

        public Builder businessId(String value) {
            context.businessId = value;
            return this;
        }

        public Builder businessRef(String value) {
            context.businessRef = value;
            return this;
        }

        public Builder requestSummaryJson(String value) {
            context.requestSummaryJson = value;
            return this;
        }

        public NoonHttpCallLogContext build() {
            return context;
        }
    }
}
