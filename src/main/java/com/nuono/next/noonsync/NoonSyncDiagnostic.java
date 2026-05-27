package com.nuono.next.noonsync;

public class NoonSyncDiagnostic {

    private final String providerTraceId;
    private final String safeSummary;

    private NoonSyncDiagnostic(String providerTraceId, String safeSummary) {
        this.providerTraceId = providerTraceId;
        this.safeSummary = safeSummary;
    }

    public static NoonSyncDiagnostic safe(String providerTraceId, String summary) {
        return new NoonSyncDiagnostic(providerTraceId, sanitize(summary));
    }

    public String getProviderTraceId() {
        return providerTraceId;
    }

    public String getSafeSummary() {
        return safeSummary;
    }

    private static String sanitize(String summary) {
        if (summary == null || summary.isBlank()) {
            return "Noon sync diagnostic unavailable.";
        }
        return summary
                .replaceAll("(?i)cookie=[^;\\s]+", "cookie=[redacted]")
                .replaceAll("(?i)api[_-]?key=[^;\\s]+", "api_key=[redacted]")
                .replaceAll("(?i)password=[^;\\s]+", "password=[redacted]")
                .replaceAll("(?i)authorization:\\s*bearer\\s+[^;\\s]+", "Authorization: Bearer [redacted]")
                .replaceAll("(?i)raw=\\{[^}]*}", "raw=[redacted]");
    }
}
