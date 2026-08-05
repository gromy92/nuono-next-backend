package com.nuono.next.orderfinance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

/** Pure value parsing shared by the finance report adapter. */
final class NoonFinanceReportValueParser {
    private NoonFinanceReportValueParser() {
    }

    static LocalDate optionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.replace('\u00a0', ' ').replace('\u202f', ' ').trim();
        if (!StringUtils.hasText(trimmed)
                || "-".equals(trimmed)
                || "–".equals(trimmed)
                || "—".equals(trimmed)) {
            return null;
        }
        String token = trimmed.split("[ T]", 2)[0];
        if (token.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return parseDateWithPattern(token, "yyyy-M-d");
        }
        if (token.matches("\\d{4}/\\d{1,2}/\\d{1,2}")) {
            return parseDateWithPattern(token, "yyyy/M/d");
        }
        if (token.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
            String[] parts = token.split("/");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            if (first > 12 && second <= 12) {
                return parseDateWithPattern(token, "d/M/yyyy");
            }
            if (second > 12 && first <= 12) {
                return parseDateWithPattern(token, "M/d/yyyy");
            }
            throw new IllegalArgumentException("Ambiguous slash date value: " + value);
        }
        try {
            return LocalDate.parse(token);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date value: " + value, exception);
        }
    }

    static LocalDate requiredBusinessDate(String value, String column) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        LocalDate parsed = optionalDate(value);
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid date value: " + column);
        }
        return parsed;
    }

    static BigDecimal optionalDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replace('\u00a0', ' ')
                .replace('\u202f', ' ')
                .replace("–", "-")
                .replace("—", "-")
                .replace("−", "-")
                .trim();
        if (isZeroPlaceholder(normalized)) {
            return BigDecimal.ZERO;
        }
        boolean negative = normalized.startsWith("(") && normalized.endsWith(")");
        if (negative) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.trim()
                .replaceAll("^[\\p{Sc}\\s]+", "")
                .replaceAll("[\\p{Sc}\\s]+$", "")
                .trim();
        if (isZeroPlaceholder(normalized)) {
            return BigDecimal.ZERO;
        }
        if (negative && normalized.startsWith("-")) {
            throw new IllegalArgumentException("Invalid negative decimal value: " + value);
        }
        if (!isConservativeNumericToken(normalized)) {
            throw new IllegalArgumentException("Invalid decimal value: " + value);
        }
        BigDecimal parsed = new BigDecimal(normalized.replace(",", ""));
        return negative ? parsed.negate() : parsed;
    }

    static boolean hasNullAmount(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate parseDateWithPattern(String value, String pattern) {
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(pattern));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date value: " + value, exception);
        }
    }

    private static boolean isZeroPlaceholder(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String trimmed = value.trim();
        return "-".equals(trimmed) || "--".equals(trimmed);
    }

    private static boolean isConservativeNumericToken(String value) {
        return StringUtils.hasText(value)
                && !value.contains(" ")
                && value.matches("[-+]?(?:(?:\\d{1,3}(?:,\\d{3})+)|(?:\\d+))(?:\\.\\d+)?");
    }
}
