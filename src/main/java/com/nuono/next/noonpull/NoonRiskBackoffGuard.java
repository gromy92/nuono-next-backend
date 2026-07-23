package com.nuono.next.noonpull;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class NoonRiskBackoffGuard {
    private static final int INITIAL_BACKOFF_MINUTES = 2;
    private static final int MAX_BACKOFF_MINUTES = 16;

    private final NoonRiskBackoffRepository repository;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public NoonRiskBackoffGuard(ObjectProvider<NoonRiskBackoffRepository> repository) {
        this(repository == null ? null : repository.getIfAvailable(), Clock.systemUTC());
    }

    public NoonRiskBackoffGuard(NoonRiskBackoffRepository repository) {
        this(repository, Clock.systemUTC());
    }

    public NoonRiskBackoffGuard(NoonRiskBackoffRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.enabled = repository != null;
    }

    private NoonRiskBackoffGuard(Clock clock) {
        this.repository = null;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.enabled = false;
    }

    public static NoonRiskBackoffGuard disabled() {
        return new NoonRiskBackoffGuard(Clock.systemUTC());
    }

    public Optional<NoonRiskBackoffHold> currentHold(NoonRiskBackoffScope scope) {
        if (!enabled || scope == null || !StringUtils.hasText(scope.getScopeKey())) {
            return Optional.empty();
        }
        LocalDateTime now = now();
        NoonRiskBackoffHold hold = repository.selectActiveHold(scope.getScopeKey(), now);
        NoonRiskBackoffScope accountWideScope = scope.accountWide();
        NoonRiskBackoffHold accountWideHold = repository.selectActiveAccountWideHold(
                scope.getOwnerUserId(),
                scope.getStoreCode(),
                scope.getSiteCode(),
                accountWideScope == null ? null : accountWideScope.getOperationGroup(),
                now
        );
        if (accountWideHold == null && accountWideScope != null) {
            NoonRiskBackoffHold fallback = repository.selectActiveHold(accountWideScope.getScopeKey(), now);
            if (fallback != null && accountWideScope.acceptsAccountWideSourceDomain(fallback.getSourceDomain())) {
                accountWideHold = fallback;
            }
        }
        hold = moreRestrictive(
                hold,
                accountWideHold
        );
        return Optional.ofNullable(hold == null ? null : hold.copy());
    }

    public NoonRiskBackoffHold recordRiskSignal(
            NoonRiskBackoffScope scope,
            String riskType,
            String sourceDomain,
            Long sourceTaskId,
            LocalDateTime blockedUntil,
            String diagnosticSummary
    ) {
        if (scope == null || !StringUtils.hasText(scope.getScopeKey())) {
            throw new IllegalArgumentException("Noon risk backoff scope is required.");
        }
        LocalDateTime now = now();
        NoonRiskBackoffHold hold = buildHold(
                scope,
                riskType,
                sourceDomain,
                sourceTaskId,
                blockedUntil,
                diagnosticSummary,
                now
        );
        NoonRiskBackoffHold accountWideHold = null;
        NoonRiskBackoffScope accountWideScope = scope.accountWide();
        if (accountWideScope != null && !scope.getScopeKey().equals(accountWideScope.getScopeKey())) {
            accountWideHold = buildHold(
                    accountWideScope,
                    riskType,
                    sourceDomain,
                    sourceTaskId,
                    blockedUntil,
                    diagnosticSummary,
                    now
            );
        }
        return moreRestrictive(hold, accountWideHold).copy();
    }

    @Transactional
    public void recordSuccess(NoonRiskBackoffScope scope, String sourceDomain) {
        if (!enabled || scope == null || !StringUtils.hasText(scope.getScopeKey())) {
            return;
        }
        String normalizedDomain = normalizeDomain(sourceDomain);
        if (!StringUtils.hasText(normalizedDomain)) {
            return;
        }
        LocalDateTime resetAt = now();
        repository.resetAfterSuccess(scope.getScopeKey(), normalizedDomain, resetAt);
        NoonRiskBackoffScope accountWideScope = scope.accountWide();
        if (accountWideScope != null && !scope.getScopeKey().equals(accountWideScope.getScopeKey())) {
            repository.resetAfterSuccess(accountWideScope.getScopeKey(), normalizedDomain, resetAt);
        }
    }

    private NoonRiskBackoffHold buildHold(
            NoonRiskBackoffScope scope,
            String riskType,
            String sourceDomain,
            Long sourceTaskId,
            LocalDateTime blockedUntil,
            String diagnosticSummary,
            LocalDateTime now
    ) {
        NoonRiskBackoffHold previous = enabled ? repository.selectLatestHold(scope.getScopeKey()) : null;
        if (previous != null && !scope.acceptsAccountWideSourceDomain(previous.getSourceDomain())) {
            previous = null;
        }
        int attemptCount = nextAttemptCount(previous);
        NoonRiskBackoffHold hold = new NoonRiskBackoffHold();
        hold.setScopeKey(scope.getScopeKey());
        hold.setScopeType(scope.getScopeType());
        hold.setOwnerUserId(scope.getOwnerUserId());
        hold.setStoreCode(scope.getStoreCode());
        hold.setSiteCode(scope.getSiteCode());
        hold.setOperationGroup(scope.getOperationGroup());
        hold.setRiskType(normalizeCode(riskType));
        hold.setSourceDomain(normalizeDomain(sourceDomain));
        hold.setSourceTaskId(sourceTaskId);
        hold.setAttemptCount(attemptCount);
        hold.setBlockedUntil(blockedUntil == null ? now.plusMinutes(backoffMinutes(attemptCount)) : blockedUntil);
        hold.setDiagnosticSummary(diagnosticSummary);
        hold.setCreatedAt(now);
        hold.setUpdatedAt(now);
        if (enabled) {
            repository.upsert(hold);
        }
        return hold;
    }

    private int nextAttemptCount(NoonRiskBackoffHold previous) {
        if (previous == null || previous.getAttemptCount() == null || previous.getAttemptCount() < 1) {
            return 1;
        }
        return previous.getAttemptCount() + 1;
    }

    private int backoffMinutes(int attemptCount) {
        int exponent = Math.max(0, Math.min(3, attemptCount - 1));
        return Math.min(MAX_BACKOFF_MINUTES, INITIAL_BACKOFF_MINUTES << exponent);
    }

    private NoonRiskBackoffHold moreRestrictive(NoonRiskBackoffHold first, NoonRiskBackoffHold second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        if (first.getBlockedUntil() == null) {
            return second;
        }
        if (second.getBlockedUntil() == null) {
            return first;
        }
        return second.getBlockedUntil().isAfter(first.getBlockedUntil()) ? second : first;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), clock.getZone());
    }

    private static String normalizeCode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDomain(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
