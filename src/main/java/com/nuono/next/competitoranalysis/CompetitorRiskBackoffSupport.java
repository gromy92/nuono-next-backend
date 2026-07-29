package com.nuono.next.competitoranalysis;

import com.nuono.next.noonpull.NoonRiskBackoffGuard;
import com.nuono.next.noonpull.NoonRiskBackoffHold;
import com.nuono.next.noonpull.NoonRiskBackoffScope;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

final class CompetitorRiskBackoffSupport {
    private static final String RATE_LIMITED = "RATE_LIMITED";
    private static final String BLOCKED_BY_RISK_CONTROL = "BLOCKED_BY_RISK_CONTROL";
    private static final String CAPTCHA_REQUIRED = "CAPTCHA_REQUIRED";

    private final NoonRiskBackoffGuard guard;

    CompetitorRiskBackoffSupport(NoonRiskBackoffGuard guard) {
        this.guard = guard == null ? NoonRiskBackoffGuard.disabled() : guard;
    }

    void rejectActive(Long ownerUserId, String storeCode, String siteCode) {
        if (guard.currentHold(scope(ownerUserId, storeCode, siteCode)).isPresent()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "NOON_RISK_BACKOFF");
        }
    }

    Optional<NoonRiskBackoffHold> current(CompetitorWatchProductRow watchProduct) {
        if (watchProduct == null) {
            return Optional.empty();
        }
        return guard.currentHold(scope(
                watchProduct.getOwnerUserId(),
                watchProduct.getStoreCode(),
                watchProduct.getSiteCode()
        ));
    }

    NoonRiskBackoffHold record(
            CompetitorWatchProductRow watchProduct,
            Long taskId,
            String errorCode,
            String errorMessage
    ) {
        return guard.recordRiskSignal(
                scope(
                        watchProduct == null ? null : watchProduct.getOwnerUserId(),
                        watchProduct == null ? null : watchProduct.getStoreCode(),
                        watchProduct == null ? null : watchProduct.getSiteCode()
                ),
                riskType(errorCode),
                "PUBLIC_SEARCH",
                taskId,
                null,
                firstNonBlank(errorMessage, errorCode)
        );
    }

    void recordSuccess(CompetitorWatchProductRow watchProduct) {
        if (watchProduct != null) {
            guard.recordSuccess(
                    scope(
                            watchProduct.getOwnerUserId(),
                            watchProduct.getStoreCode(),
                            watchProduct.getSiteCode()
                    ),
                    "PUBLIC_SEARCH"
            );
        }
    }

    boolean isRiskFailure(String errorCode) {
        String normalized = normalize(errorCode);
        return RATE_LIMITED.equals(normalized)
                || BLOCKED_BY_RISK_CONTROL.equals(normalized)
                || CAPTCHA_REQUIRED.equals(normalized);
    }

    String message(NoonRiskBackoffHold hold) {
        return "竞品监控触发 Noon 风控退避："
                + (hold == null ? "unknown" : hold.getRiskType())
                + "，冷却至 "
                + (hold == null ? "unknown" : hold.getBlockedUntil())
                + "。";
    }

    private NoonRiskBackoffScope scope(Long ownerUserId, String storeCode, String siteCode) {
        return NoonRiskBackoffScope.publicSearch(ownerUserId, storeCode, siteCode);
    }

    private String riskType(String errorCode) {
        String normalized = normalize(errorCode);
        if (RATE_LIMITED.equals(normalized)) {
            return "rate_limited";
        }
        if (BLOCKED_BY_RISK_CONTROL.equals(normalized)) {
            return "blocked_by_risk_control";
        }
        if (CAPTCHA_REQUIRED.equals(normalized)) {
            return "captcha_required";
        }
        return normalized == null ? "risk_control" : normalized.toLowerCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
