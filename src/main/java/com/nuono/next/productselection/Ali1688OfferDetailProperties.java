package com.nuono.next.productselection;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "nuono.product-selection.ali1688.offer-detail")
public class Ali1688OfferDetailProperties {

    private boolean enabled;
    private String primaryEndpointUrl;
    private String fallbackEndpointUrl;
    private int timeoutSeconds = 20;
    private int maxCandidates = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrimaryEndpointUrl() {
        return primaryEndpointUrl;
    }

    public void setPrimaryEndpointUrl(String primaryEndpointUrl) {
        this.primaryEndpointUrl = primaryEndpointUrl;
    }

    public String getFallbackEndpointUrl() {
        return fallbackEndpointUrl;
    }

    public void setFallbackEndpointUrl(String fallbackEndpointUrl) {
        this.fallbackEndpointUrl = fallbackEndpointUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxCandidates() {
        return maxCandidates;
    }

    public void setMaxCandidates(int maxCandidates) {
        this.maxCandidates = maxCandidates;
    }
}
