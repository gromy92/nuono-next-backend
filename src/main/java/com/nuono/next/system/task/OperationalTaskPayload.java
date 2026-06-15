package com.nuono.next.system.task;

public class OperationalTaskPayload {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String payloadJson;
    private String message;

    public static OperationalTaskPayload empty() {
        return new OperationalTaskPayload();
    }

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

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getMessage() {
        return message;
    }

    public static final class Builder {
        private final OperationalTaskPayload payload = new OperationalTaskPayload();

        public Builder ownerUserId(Long ownerUserId) {
            payload.ownerUserId = ownerUserId;
            return this;
        }

        public Builder storeCode(String storeCode) {
            payload.storeCode = storeCode;
            return this;
        }

        public Builder siteCode(String siteCode) {
            payload.siteCode = siteCode;
            return this;
        }

        public Builder payloadJson(String payloadJson) {
            payload.payloadJson = payloadJson;
            return this;
        }

        public Builder message(String message) {
            payload.message = message;
            return this;
        }

        public OperationalTaskPayload build() {
            return payload;
        }
    }
}
