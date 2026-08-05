package com.nuono.next.noon;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.util.StringUtils;

/** Parses a Retry-After value into a secret-free scheduling hint. */
public final class NoonRetryAfterParser {

    private NoonRetryAfterParser() {
    }

    public static Duration parse(String value) {
        return parse(value, Instant.now());
    }

    static Duration parse(String value, Instant now) {
        if (!StringUtils.hasText(value) || now == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.matches("[0-9]+")) {
            try {
                return Duration.ofSeconds(Long.parseLong(normalized));
            } catch (RuntimeException invalidDeltaSeconds) {
                return null;
            }
        }
        try {
            Instant retryAt = ZonedDateTime.parse(
                    normalized,
                    DateTimeFormatter.RFC_1123_DATE_TIME
            ).toInstant();
            Duration delay = Duration.between(now, retryAt);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (RuntimeException invalidHttpDate) {
            return null;
        }
    }
}
