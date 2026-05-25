package com.nuono.next.nooncompleteness;

import java.time.LocalDate;

public class NoonDataAuditCommand {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private LocalDate auditDate;

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

    public LocalDate getAuditDate() {
        return auditDate;
    }

    public static class Builder {
        private final NoonDataAuditCommand command = new NoonDataAuditCommand();

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

        public Builder auditDate(LocalDate auditDate) {
            command.auditDate = auditDate;
            return this;
        }

        public NoonDataAuditCommand build() {
            return command;
        }
    }
}
