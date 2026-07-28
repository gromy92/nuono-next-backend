package com.nuono.next.competitoranalysis;

import java.time.LocalDateTime;
import org.springframework.util.StringUtils;

/**
 * Retry schedule owned by one product-detail target.
 */
final class CompetitorDetailRetryState {
    private final CompetitorProductDetailTarget target;
    private final int retryAttempt;
    private final LocalDateTime retryNotBefore;
    private final String errorCode;
    private final String errorMessage;

    CompetitorDetailRetryState(
            CompetitorProductDetailTarget target,
            int retryAttempt,
            LocalDateTime retryNotBefore,
            String errorCode,
            String errorMessage
    ) {
        this.target = target;
        this.retryAttempt = Math.max(0, retryAttempt);
        this.retryNotBefore = retryNotBefore;
        this.errorCode = normalize(errorCode);
        this.errorMessage = normalize(errorMessage);
    }

    CompetitorDetailRetryState copy() {
        return new CompetitorDetailRetryState(
                target,
                retryAttempt,
                retryNotBefore,
                errorCode,
                errorMessage
        );
    }

    CompetitorDetailRetryState delayedUntil(LocalDateTime holdUntil) {
        LocalDateTime delayed = retryNotBefore;
        if (holdUntil != null && (delayed == null || holdUntil.isAfter(delayed))) {
            delayed = holdUntil;
        }
        return new CompetitorDetailRetryState(
                target,
                retryAttempt,
                delayed,
                errorCode,
                errorMessage
        );
    }

    boolean isReadyAt(LocalDateTime now) {
        return retryNotBefore == null || (now != null && !now.isBefore(retryNotBefore));
    }

    String identityKey() {
        return target == null ? "" : target.identityKey();
    }

    CompetitorProductDetailTarget getTarget() {
        return target;
    }

    int getRetryAttempt() {
        return retryAttempt;
    }

    LocalDateTime getRetryNotBefore() {
        return retryNotBefore;
    }

    String getErrorCode() {
        return errorCode;
    }

    String getErrorMessage() {
        return errorMessage;
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
