package com.nuono.next.noonpull;

import java.util.regex.Pattern;

final class NoonPullSafeText {
    private static final Pattern COOKIE_PATTERN = Pattern.compile("(?i)(cookie\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("(?i)(password|passwd|pwd)(\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern BEARER_PATTERN = Pattern.compile("(?i)(authorization\\s*:\\s*bearer\\s+)[^\\s;]+");
    private static final Pattern API_KEY_PATTERN = Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)[^\\s;]+");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s;]+");

    private NoonPullSafeText() {
    }

    static String redact(String value) {
        if (value == null) {
            return null;
        }
        String redacted = COOKIE_PATTERN.matcher(value).replaceAll("$1[redacted]");
        redacted = PASSWORD_PATTERN.matcher(redacted).replaceAll("$1$2[redacted]");
        redacted = BEARER_PATTERN.matcher(redacted).replaceAll("$1[redacted]");
        redacted = API_KEY_PATTERN.matcher(redacted).replaceAll("$1[redacted]");
        redacted = URL_PATTERN.matcher(redacted).replaceAll("[redacted-url]");
        return redacted.replaceAll("token-[^\\s;]+", "[redacted]");
    }
}
