package com.nuono.next.nooncompleteness;

import java.time.LocalDate;

public class NoonDataCompletenessSeedCommand {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private boolean productProjectionPresent;
    private LocalDate seedDate;

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

    public boolean isProductProjectionPresent() {
        return productProjectionPresent;
    }

    public LocalDate getSeedDate() {
        return seedDate;
    }

    public static class Builder {
        private final NoonDataCompletenessSeedCommand command = new NoonDataCompletenessSeedCommand();

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

        public Builder productProjectionPresent(boolean productProjectionPresent) {
            command.productProjectionPresent = productProjectionPresent;
            return this;
        }

        public Builder seedDate(LocalDate seedDate) {
            command.seedDate = seedDate;
            return this;
        }

        public NoonDataCompletenessSeedCommand build() {
            return command;
        }
    }
}
