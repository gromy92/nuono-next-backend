package com.nuono.next.intransit.autosync;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.logistics-auto-sync")
public class LogisticsAutoSyncProperties {
    private String credentialCipherSecret;

    public String getCredentialCipherSecret() {
        return credentialCipherSecret;
    }

    public void setCredentialCipherSecret(String credentialCipherSecret) {
        this.credentialCipherSecret = credentialCipherSecret;
    }
}
