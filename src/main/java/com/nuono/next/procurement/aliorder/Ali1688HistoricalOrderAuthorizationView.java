package com.nuono.next.procurement.aliorder;

public class Ali1688HistoricalOrderAuthorizationView {

    public static class StartView {
        private boolean configured;
        private String providerCode;
        private String authorizationUrl;
        private String message;

        public boolean isConfigured() {
            return configured;
        }

        public void setConfigured(boolean configured) {
            this.configured = configured;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getAuthorizationUrl() {
            return authorizationUrl;
        }

        public void setAuthorizationUrl(String authorizationUrl) {
            this.authorizationUrl = authorizationUrl;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public static class CompleteView {
        private Long authorizationId;
        private String providerCode;
        private String providerAccountId;
        private String accountLabel;
        private String message;

        public Long getAuthorizationId() {
            return authorizationId;
        }

        public void setAuthorizationId(Long authorizationId) {
            this.authorizationId = authorizationId;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getProviderAccountId() {
            return providerAccountId;
        }

        public void setProviderAccountId(String providerAccountId) {
            this.providerAccountId = providerAccountId;
        }

        public String getAccountLabel() {
            return accountLabel;
        }

        public void setAccountLabel(String accountLabel) {
            this.accountLabel = accountLabel;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
