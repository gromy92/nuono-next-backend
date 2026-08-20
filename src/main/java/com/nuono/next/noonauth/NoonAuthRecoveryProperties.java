package com.nuono.next.noonauth;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "nuono.noon.auth-recovery")
public class NoonAuthRecoveryProperties {
    private boolean enabled;
    private long schedulerInitialDelayMs = 30_000L;
    private long schedulerFixedDelayMs = 10_000L;
    private int coalesceSeconds = 15;
    private int leaseSeconds = 600;
    private int minResendSeconds = 60;
    private int minSendIntervalSeconds = 300;
    private int rateLimitRetrySeconds = 1_800;
    private int maxSendAttemptsPerRecovery = 2;
    private boolean allProjectsEnabled;
    private boolean sessionAuditEnabled = true;
    private boolean startupAuditEnabled;
    private long startupAuditDelayMs = 30_000L;
    private long startupAuditPollMs = 10_000L;
    private String projectAllowlist;
    private String trustedSenderDomains;
    private String checkpointCipherSecret;
    private String checkpointKeyVersion = "v1";
    private int checkpointTtlSeconds = 600;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getSchedulerInitialDelayMs() {
        return Math.max(1_000L, schedulerInitialDelayMs);
    }

    public void setSchedulerInitialDelayMs(long schedulerInitialDelayMs) {
        this.schedulerInitialDelayMs = schedulerInitialDelayMs;
    }

    public long getSchedulerFixedDelayMs() {
        return Math.max(1_000L, schedulerFixedDelayMs);
    }

    public void setSchedulerFixedDelayMs(long schedulerFixedDelayMs) {
        this.schedulerFixedDelayMs = schedulerFixedDelayMs;
    }

    public Duration coalesceDuration() {
        return Duration.ofSeconds(Math.max(0, coalesceSeconds));
    }

    public int getCoalesceSeconds() {
        return coalesceSeconds;
    }

    public void setCoalesceSeconds(int coalesceSeconds) {
        this.coalesceSeconds = coalesceSeconds;
    }

    public Duration leaseDuration() {
        return Duration.ofSeconds(Math.max(600, leaseSeconds));
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public void setLeaseSeconds(int leaseSeconds) {
        this.leaseSeconds = leaseSeconds;
    }

    public Duration minResendDelay() {
        return Duration.ofSeconds(Math.max(0, minResendSeconds));
    }

    public int getMinResendSeconds() {
        return minResendSeconds;
    }

    public void setMinResendSeconds(int minResendSeconds) {
        this.minResendSeconds = minResendSeconds;
    }

    public Duration minSendInterval() {
        return Duration.ofSeconds(Math.max(300, minSendIntervalSeconds));
    }

    public int getMinSendIntervalSeconds() {
        return minSendIntervalSeconds;
    }

    public void setMinSendIntervalSeconds(int minSendIntervalSeconds) {
        this.minSendIntervalSeconds = minSendIntervalSeconds;
    }

    public Duration rateLimitRetryDelay() {
        return Duration.ofSeconds(Math.max(1_800, rateLimitRetrySeconds));
    }

    public int getRateLimitRetrySeconds() {
        return rateLimitRetrySeconds;
    }

    public void setRateLimitRetrySeconds(int rateLimitRetrySeconds) {
        this.rateLimitRetrySeconds = rateLimitRetrySeconds;
    }

    public int getMaxSendAttemptsPerRecovery() {
        return Math.max(1, Math.min(2, maxSendAttemptsPerRecovery));
    }

    public void setMaxSendAttemptsPerRecovery(int maxSendAttemptsPerRecovery) {
        this.maxSendAttemptsPerRecovery = maxSendAttemptsPerRecovery;
    }

    public boolean isAllProjectsEnabled() {
        return allProjectsEnabled;
    }

    public void setAllProjectsEnabled(boolean allProjectsEnabled) {
        this.allProjectsEnabled = allProjectsEnabled;
    }

    public boolean isSessionAuditEnabled() {
        return sessionAuditEnabled;
    }

    public void setSessionAuditEnabled(boolean sessionAuditEnabled) {
        this.sessionAuditEnabled = sessionAuditEnabled;
    }

    public boolean isStartupAuditEnabled() {
        return startupAuditEnabled;
    }

    public void setStartupAuditEnabled(boolean startupAuditEnabled) {
        this.startupAuditEnabled = startupAuditEnabled;
    }

    public long getStartupAuditDelayMs() {
        return Math.max(1_000L, startupAuditDelayMs);
    }

    public void setStartupAuditDelayMs(long startupAuditDelayMs) {
        this.startupAuditDelayMs = startupAuditDelayMs;
    }

    public long getStartupAuditPollMs() {
        return Math.max(1_000L, startupAuditPollMs);
    }

    public void setStartupAuditPollMs(long startupAuditPollMs) {
        this.startupAuditPollMs = startupAuditPollMs;
    }

    public String projectScopeMode() {
        if (allProjectsEnabled) {
            return "ALL";
        }
        return normalizedProjectAllowlist().isEmpty() ? "NONE" : "ALLOWLIST";
    }

    public String getProjectAllowlist() {
        return projectAllowlist;
    }

    public void setProjectAllowlist(String projectAllowlist) {
        this.projectAllowlist = projectAllowlist;
    }

    public boolean allowsProject(String projectCode) {
        if (allProjectsEnabled) {
            return StringUtils.hasText(projectCode);
        }
        Set<String> allowlist = normalizedProjectAllowlist();
        return StringUtils.hasText(projectCode)
                && allowlist.contains(projectCode.trim().toUpperCase(Locale.ROOT));
    }

    public Set<String> normalizedProjectAllowlist() {
        if (!StringUtils.hasText(projectAllowlist)) {
            return Collections.emptySet();
        }
        return Arrays.stream(projectAllowlist.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public String getTrustedSenderDomains() {
        return trustedSenderDomains;
    }

    public void setTrustedSenderDomains(String trustedSenderDomains) {
        this.trustedSenderDomains = trustedSenderDomains;
    }

    public Set<String> normalizedTrustedSenderDomains() {
        if (!StringUtils.hasText(trustedSenderDomains)) {
            return Collections.emptySet();
        }
        TreeSet<String> domains = Arrays.stream(trustedSenderDomains.split(","))
                .map(NoonAuthRecoveryProperties::normalizeTrustedSenderDomain)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(TreeSet::new));
        return domains.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(domains);
    }

    public String getCheckpointCipherSecret() {
        return checkpointCipherSecret;
    }

    public void setCheckpointCipherSecret(String value) {
        checkpointCipherSecret = value;
    }

    public String getCheckpointKeyVersion() {
        return StringUtils.hasText(checkpointKeyVersion) ? checkpointKeyVersion.trim() : "v1";
    }

    public void setCheckpointKeyVersion(String value) {
        checkpointKeyVersion = value;
    }

    public Duration checkpointTtl() {
        return Duration.ofSeconds(Math.max(120, checkpointTtlSeconds));
    }

    public int getCheckpointTtlSeconds() {
        return checkpointTtlSeconds;
    }

    public void setCheckpointTtlSeconds(int value) {
        checkpointTtlSeconds = value;
    }

    public boolean allowsTrustedSenderDomain(String senderDomain) {
        String normalizedSenderDomain = normalizeTrustedSenderDomain(senderDomain);
        if (!StringUtils.hasText(normalizedSenderDomain)) {
            return false;
        }
        return normalizedTrustedSenderDomains().stream().anyMatch(allowedDomain ->
                normalizedSenderDomain.equals(allowedDomain)
                        || normalizedSenderDomain.endsWith("." + allowedDomain)
        );
    }

    static String normalizeTrustedSenderDomain(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)
                || normalized.startsWith(".")
                || normalized.contains("..")
                || !normalized.matches("[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) {
            return null;
        }
        return normalized;
    }
}
