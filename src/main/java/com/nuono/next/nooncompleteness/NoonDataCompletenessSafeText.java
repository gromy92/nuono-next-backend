package com.nuono.next.nooncompleteness;

import java.util.regex.Pattern;

public final class NoonDataCompletenessSafeText {
    private static final Pattern COOKIE_PATTERN = Pattern.compile("(?i)(cookie\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)(\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s;]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern DOWNLOAD_URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);

    private NoonDataCompletenessSafeText() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String result = COOKIE_PATTERN.matcher(text).replaceAll("$1[REDACTED]");
        result = PASSWORD_PATTERN.matcher(result).replaceAll("$1$2[REDACTED]");
        result = BEARER_PATTERN.matcher(result).replaceAll("$1[REDACTED]");
        result = API_KEY_PATTERN.matcher(result).replaceAll("$1[REDACTED]");
        result = DOWNLOAD_URL_PATTERN.matcher(result).replaceAll("[REDACTED_URL]");
        return result;
    }
}

