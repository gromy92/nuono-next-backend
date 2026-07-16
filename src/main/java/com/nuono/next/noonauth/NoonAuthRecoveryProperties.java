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
    private int maxSendAttemptsPerRecovery = 2;
    private int maxSendsPerHour = 3;
    private int maxSendsPerDay = 6;
    private String projectAllowlist;
    private String trustedSenderDomains;

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

    public int getMaxSendAttemptsPerRecovery() {
        return Math.max(1, Math.min(2, maxSendAttemptsPerRecovery));
    }

    public void setMaxSendAttemptsPerRecovery(int maxSendAttemptsPerRecovery) {
        this.maxSendAttemptsPerRecovery = maxSendAttemptsPerRecovery;
    }

    public int getMaxSendsPerHour() {
        return Math.max(1, Math.min(3, maxSendsPerHour));
    }

    public void setMaxSendsPerHour(int maxSendsPerHour) {
        this.maxSendsPerHour = maxSendsPerHour;
    }

    public int getMaxSendsPerDay() {
        return Math.max(getMaxSendsPerHour(), Math.min(6, maxSendsPerDay));
    }

    public void setMaxSendsPerDay(int maxSendsPerDay) {
        this.maxSendsPerDay = maxSendsPerDay;
    }

    public String getProjectAllowlist() {
        return projectAllowlist;
    }

    public void setProjectAllowlist(String projectAllowlist) {
        this.projectAllowlist = projectAllowlist;
    }

    public boolean allowsProject(String projectCode) {
        Set<String> allowlist = normalizedProjectAllowlist();
        if (allowlist.isEmpty()) {
            return true;
        }
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
