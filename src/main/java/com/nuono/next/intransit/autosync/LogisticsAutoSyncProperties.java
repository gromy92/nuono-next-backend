package com.nuono.next.intransit.autosync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.logistics-auto-sync")
public class LogisticsAutoSyncProperties {
    private String credentialCipherSecret;
    private Scheduler scheduler = new Scheduler();

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
}
