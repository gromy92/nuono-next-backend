package com.nuono.next.intransit.autosync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.logistics-auto-sync")
public class LogisticsAutoSyncProperties {
    private String credentialCipherSecret;
    private Scheduler scheduler = new Scheduler();
    private Chic chic = new Chic();
    private Et et = new Et();
    private Yite yite = new Yite();

    public String getCredentialCipherSecret() {
        return credentialCipherSecret;
    }

    public void setCredentialCipherSecret(String credentialCipherSecret) {
        this.credentialCipherSecret = credentialCipherSecret;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler == null ? new Scheduler() : scheduler;
    }

    public Chic getChic() {
        return chic;
    }

    public void setChic(Chic chic) {
        this.chic = chic == null ? new Chic() : chic;
    }

    public Et getEt() {
        return et;
    }

    public void setEt(Et et) {
        this.et = et == null ? new Et() : et;
    }

    public Yite getYite() {
        return yite;
    }

    public void setYite(Yite yite) {
        this.yite = yite == null ? new Yite() : yite;
    }

    public static class Scheduler {
        private boolean enabled;
        private String cron = "0 */30 * * * *";
        private String zone = "Asia/Shanghai";
        private int maxAccountsPerTick = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }

        public int getMaxAccountsPerTick() {
            return maxAccountsPerTick;
        }

        public void setMaxAccountsPerTick(int maxAccountsPerTick) {
            this.maxAccountsPerTick = maxAccountsPerTick;
        }
    }

    public static class Chic {
        private boolean enabled;
        private String baseUrl = "https://erp.chicexpressglobal.com";
        private String loginPath;
        private String loginAccountField = "username";
        private String loginPasswordField = "password";
        private int timeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getLoginPath() {
            return loginPath;
        }

        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLoginAccountField() {
            return loginAccountField;
        }

        public void setLoginAccountField(String loginAccountField) {
            this.loginAccountField = loginAccountField;
        }

        public String getLoginPasswordField() {
            return loginPasswordField;
        }

        public void setLoginPasswordField(String loginPasswordField) {
            this.loginPasswordField = loginPasswordField;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Et {
        private boolean enabled;
        private String baseUrl = "https://wl.et-global.cn";
        private String loginPath;
        private String loginAccountField = "username";
        private String loginPasswordField = "password";
        private String loginPayloadType = "form";
        private int timeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getLoginPath() {
            return loginPath;
        }

        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLoginAccountField() {
            return loginAccountField;
        }

        public void setLoginAccountField(String loginAccountField) {
            this.loginAccountField = loginAccountField;
        }

        public String getLoginPasswordField() {
            return loginPasswordField;
        }

        public void setLoginPasswordField(String loginPasswordField) {
            this.loginPasswordField = loginPasswordField;
        }

        public String getLoginPayloadType() {
            return loginPayloadType;
        }

        public void setLoginPayloadType(String loginPayloadType) {
            this.loginPayloadType = loginPayloadType;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    public static class Yite {
        private boolean enabled;
        private String baseUrl = "https://ywyite.nextsls.com";
        private String loginPath;
        private String loginAccountField = "username";
        private String loginPasswordField = "password";
        private String loginPayloadType = "json";
        private int timeoutSeconds = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getLoginPath() {
            return loginPath;
        }

        public void setLoginPath(String loginPath) {
            this.loginPath = loginPath;
        }

        public String getLoginAccountField() {
            return loginAccountField;
        }

        public void setLoginAccountField(String loginAccountField) {
            this.loginAccountField = loginAccountField;
        }

        public String getLoginPasswordField() {
            return loginPasswordField;
        }

        public void setLoginPasswordField(String loginPasswordField) {
            this.loginPasswordField = loginPasswordField;
        }

        public String getLoginPayloadType() {
            return loginPayloadType;
        }

        public void setLoginPayloadType(String loginPayloadType) {
            this.loginPayloadType = loginPayloadType;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
