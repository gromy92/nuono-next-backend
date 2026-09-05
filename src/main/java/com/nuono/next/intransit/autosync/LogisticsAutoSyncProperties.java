package com.nuono.next.intransit.autosync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.logistics-auto-sync")
public class LogisticsAutoSyncProperties {
    private String credentialCipherSecret;
    private Scheduler scheduler = new Scheduler();
    private Scheduler freightBillScheduler = new Scheduler();
    private Chic chic = new Chic();
    private Et et = new Et();
    private Yite yite = new Yite();
    private Zd zd = new Zd();

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

    public Scheduler getFreightBillScheduler() {
        return freightBillScheduler;
    }

    public void setFreightBillScheduler(Scheduler value) {
        this.freightBillScheduler = value == null ? new Scheduler() : value;
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

    public Zd getZd() {
        return zd;
    }

    public void setZd(Zd zd) {
        this.zd = zd == null ? new Zd() : zd;
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
        private String loginPath = "/api/login";
        private String loginAccountField = "username";
        private String loginPasswordField = "password";
        private int timeoutSeconds = 30;
        private String freightBillPath = "/api/order/report/list?customerName=&orderSn=&warehousingSn=&shippingNo=&status=&country=&transportMode=&statusTime=&page=1&rows=50";

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

        public String getFreightBillPath() { return freightBillPath; }
        public void setFreightBillPath(String value) { this.freightBillPath = value; }
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
        private String loginPath = "/rest/tms/wos/auth/login?redirect_url=%2Ftms%2Fwos";
        private String loginAccountField = "username";
        private String loginPasswordField = "password";
        private String loginPayloadType = "json";
        private int timeoutSeconds = 30;
        private String freightBillPath;
        private String freightBillMethod = "GET";

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

        public String getFreightBillPath() { return freightBillPath; }
        public void setFreightBillPath(String value) { this.freightBillPath = value; }
        public String getFreightBillMethod() { return freightBillMethod; }
        public void setFreightBillMethod(String value) { this.freightBillMethod = value; }
    }

    public static class Zd {
        private boolean enabled;
        private String baseUrl = "http://www.erpzd.com";
        private String loginPath = "/api/v1/login";
        private String expressPath = "/api/v1/customer/wuliu/express/integral/q";
        private String boxPath = "/api/v1/customer/wuliu/box/q";
        private int lookbackDays = 59;
        private int lookaheadDays = 1;
        private int timeoutSeconds = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getLoginPath() { return loginPath; }
        public void setLoginPath(String loginPath) { this.loginPath = loginPath; }
        public String getExpressPath() { return expressPath; }
        public void setExpressPath(String expressPath) { this.expressPath = expressPath; }
        public String getBoxPath() { return boxPath; }
        public void setBoxPath(String boxPath) { this.boxPath = boxPath; }
        public int getLookbackDays() { return lookbackDays; }
        public void setLookbackDays(int lookbackDays) { this.lookbackDays = lookbackDays; }
        public int getLookaheadDays() { return lookaheadDays; }
        public void setLookaheadDays(int lookaheadDays) { this.lookaheadDays = lookaheadDays; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}
