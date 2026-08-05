package com.nuono.next.officialwarehouse;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

/** Strict scalar, header, quantity and date parsing for the FBN received CSV. */
final class OfficialWarehouseFbnReceivedReportValueParser {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SPACE_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter STORED_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT);

    private OfficialWarehouseFbnReceivedReportValueParser() {
    }

    static List<String> normalizedHeaders(List<String> rawHeaders) {
        List<String> headers = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < rawHeaders.size(); index++) {
            String header = normalizeCell(rawHeaders.get(index));
            if (index == 0 && header != null && header.startsWith("\ufeff")) {
                header = header.substring(1);
            }
            String normalized = header == null ? "" : header.toLowerCase(Locale.ROOT);
            if (!StringUtils.hasText(normalized) || !seen.add(normalized)) {
                throw new IllegalArgumentException(
                        "FBN received report has blank or duplicate columns."
                );
            }
            headers.add(normalized);
        }
        return headers;
    }

    static String text(Map<String, String> fields, String key) {
        return nullIfDash(fields.get(key));
    }

    static Integer integer(Map<String, String> fields, String key) {
        String value = nullIfDash(fields.get(key));
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "FBN received report contains an invalid quantity in " + key + ".",
                    exception
            );
        }
    }

    static String date(Map<String, String> fields, String key) {
        String value = text(fields, key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException exception) {
            throw invalidDate(key, exception);
        }
    }

    static String dateTime(Map<String, String> fields, String key) {
        String value = text(fields, key);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value, DATE_FORMATTER).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Continue with the timestamp formats accepted from the upstream report.
        }
        String localValue = value.toUpperCase(Locale.ROOT).endsWith(" UTC")
                ? value.substring(0, value.length() - 4).strip()
                : value;
        try {
            LocalDateTime parsed = localValue.indexOf('T') >= 0
                    ? LocalDateTime.parse(localValue, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    : LocalDateTime.parse(localValue, SPACE_DATE_TIME_FORMATTER);
            return parsed.format(STORED_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw invalidDate(key, exception);
        }
    }

    static String normalizeCell(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static String normalizeIdentity(String value) {
        return value == null
                ? ""
                : Normalizer.normalize(value, Normalizer.Form.NFKC)
                        .strip()
                        .toUpperCase(Locale.ROOT);
    }

    private static String nullIfDash(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.strip();
        return "-".equals(trimmed) ? null : trimmed;
    }

    private static IllegalArgumentException invalidDate(
            String key,
            DateTimeParseException cause
    ) {
        return new IllegalArgumentException(
                "FBN received report contains an invalid date in " + key + ".",
                cause
        );
    }
}
