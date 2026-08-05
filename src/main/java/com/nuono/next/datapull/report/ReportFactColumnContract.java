package com.nuono.next.datapull.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Keeps accepted report rows inside the exact MySQL fact-column envelope. */
public final class ReportFactColumnContract {
    private ReportFactColumnContract() {
    }

    public static String text(String value, int maximumCharacters) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.indexOf('\0') >= 0 || hasUnpairedSurrogate(normalized)
                || normalized.codePointCount(0, normalized.length()) > maximumCharacters) {
            throw new IllegalArgumentException("report fact text exceeds target column");
        }
        return normalized;
    }

    public static long signedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("report fact count exceeds target column");
        }
        return value;
    }

    public static long positiveId(Long value) {
        if (value == null || value < 1L) {
            throw new IllegalArgumentException("report fact owner ID is invalid");
        }
        return value;
    }

    public static LocalDate date(LocalDate value) {
        if (value != null && (value.getYear() < 1000 || value.getYear() > 9999)) {
            throw new IllegalArgumentException("report fact date exceeds target column");
        }
        return value;
    }

    public static LocalDateTime dateTime(LocalDateTime value) {
        if (value != null && (value.getYear() < 1000 || value.getYear() > 9999)) {
            throw new IllegalArgumentException("report fact timestamp exceeds target column");
        }
        return value;
    }

    public static BigDecimal decimal(BigDecimal value, int precision, int scale) {
        if (value == null || integerDigits(value) > precision - scale) {
            throw new IllegalArgumentException("report fact number exceeds target column");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() > scale
                || integerDigits(normalized) > precision - scale) {
            throw new IllegalArgumentException("report fact number exceeds target column");
        }
        return normalized;
    }

    private static boolean hasUnpairedSurrogate(String value) {
        for (int index = 0; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return true;
                }
                index += 1;
            } else if (Character.isLowSurrogate(current)) {
                return true;
            }
        }
        return false;
    }

    private static int integerDigits(BigDecimal value) {
        return value.signum() == 0 ? 0 : Math.max(0, value.precision() - value.scale());
    }
}
