package com.nuono.next.noon;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.util.StringUtils;

/** Bounded retry timing for legacy read transport; one-shot DP calls bypass it. */
final class NoonReadRetryPolicy {

    private static final int MAX_RATE_LIMIT_RETRIES = 4;
    private static final long INITIAL_RATE_LIMIT_DELAY_MILLIS = 2000L;
    private static final int MAX_TRANSIENT_READ_RETRIES = 2;
    private static final long INITIAL_TRANSIENT_RETRY_DELAY_MILLIS = 700L;

    boolean shouldRetryRateLimit(int statusCode, String responseBody, int attempt) {
        return attempt <= MAX_RATE_LIMIT_RETRIES
                && isRateLimitedResponse(statusCode, responseBody);
    }

    void sleepForRateLimit(int attempt) throws InterruptedException {
        long delay = Math.min(
                INITIAL_RATE_LIMIT_DELAY_MILLIS * (1L << Math.max(attempt - 1, 0)),
                12000L
        );
        delay += ThreadLocalRandom.current().nextLong(200L, 801L);
        Thread.sleep(delay);
    }

    boolean shouldRetryTransientResponse(
            boolean retryEnabled,
            int statusCode,
            int attempt
    ) {
        return retryEnabled
                && attempt <= MAX_TRANSIENT_READ_RETRIES
                && isTransientResponseStatus(statusCode);
    }

    boolean shouldRetryTransientException(boolean retryEnabled, int attempt) {
        return retryEnabled && attempt <= MAX_TRANSIENT_READ_RETRIES;
    }

    void sleepForTransientFailure(int attempt) throws InterruptedException {
        long delay = Math.min(
                INITIAL_TRANSIENT_RETRY_DELAY_MILLIS * (1L << Math.max(attempt - 1, 0)),
                4000L
        );
        delay += ThreadLocalRandom.current().nextLong(100L, 501L);
        Thread.sleep(delay);
    }

    private boolean isTransientResponseStatus(int statusCode) {
        return statusCode == 408
                || statusCode == 500
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;
    }

    private boolean isRateLimitedResponse(int statusCode, String responseBody) {
        if (statusCode == 429 || statusCode == 418) {
            return true;
        }
        if (!StringUtils.hasText(responseBody)) {
            return false;
        }
        String normalized = responseBody.toLowerCase(Locale.ROOT);
        return normalized.contains("too many requests")
                || normalized.contains("ip_channel")
                || normalized.contains("teapot");
    }
}
