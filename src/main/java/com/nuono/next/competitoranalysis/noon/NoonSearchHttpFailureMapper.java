package com.nuono.next.competitoranalysis.noon;

import com.nuono.next.noon.NoonRetryAfterParser;
import java.time.Duration;

/** Maps transport status without retaining response bodies, credentials, or signed URLs. */
final class NoonSearchHttpFailureMapper {

    private NoonSearchHttpFailureMapper() {
    }

    static NoonSearchProviderException map(int statusCode, String sourceUrl) {
        return map(statusCode, sourceUrl, null);
    }

    static NoonSearchProviderException map(
            int statusCode,
            String sourceUrl,
            String retryAfterHeader
    ) {
        return mapWithRetryAfter(
                statusCode,
                sourceUrl,
                isRetryable(statusCode)
                        ? NoonRetryAfterParser.parse(retryAfterHeader)
                        : null
        );
    }

    static NoonSearchProviderException mapWithRetryAfter(
            int statusCode,
            String sourceUrl,
            Duration retryAfter
    ) {
        String code;
        if (statusCode == 401) {
            code = "AUTH_REQUIRED";
        } else if (statusCode == 403 || statusCode == 418) {
            code = "BLOCKED_BY_RISK_CONTROL";
        } else if (statusCode == 429) {
            code = "RATE_LIMITED";
        } else if (statusCode == 408 || statusCode >= 500) {
            code = "PROVIDER_UNAVAILABLE";
        } else {
            code = "PARSE_FAILED";
        }
        return new NoonSearchProviderException(
                code,
                "Noon 前台搜索返回 HTTP " + statusCode + "。",
                statusCode,
                sourceUrl,
                null,
                isRetryable(statusCode) ? retryAfter : null
        );
    }

    private static boolean isRetryable(int statusCode) {
        return statusCode == 403
                || statusCode == 418
                || statusCode == 429
                || statusCode == 408
                || statusCode >= 500;
    }
}
