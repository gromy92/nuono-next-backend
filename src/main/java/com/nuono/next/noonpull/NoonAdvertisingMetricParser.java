package com.nuono.next.noonpull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.util.StringUtils;

/** Strict numeric, date, header, and query-kind parsing shared by DP-06 payload formats. */
final class NoonAdvertisingMetricParser {
    private static final int FACT_DECIMAL_PRECISION = 18;
    private static final int FACT_DECIMAL_SCALE = 6;
    private static final int PERCENT_PRECISION = 10;
    private static final int PERCENT_SCALE = 4;

    long nonNegativeLong(String value) {
        BigDecimal parsed = parseDecimal(value);
        if (parsed.stripTrailingZeros().scale() > 0) {
            throw contract("ADS_COUNT_INVALID");
        }
        final long result;
        try {
            result = parsed.longValueExact();
        } catch (ArithmeticException invalidCount) {
            throw new NoonAdvertisingContractException(
                    "ADS_COUNT_OUT_OF_RANGE",
                    invalidCount
            );
        }
        if (result < 0L) {
            throw contract("ADS_NEGATIVE_COUNT");
        }
        return result;
    }

    BigDecimal decimal(String value) {
        return boundedDecimal(
                parseDecimal(value), FACT_DECIMAL_PRECISION, FACT_DECIMAL_SCALE
        );
    }

    BigDecimal percentFraction(String value) {
        BigDecimal fraction = parseDecimal(value)
                .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
        return boundedDecimal(fraction, PERCENT_PRECISION, PERCENT_SCALE);
    }

    String boundedText(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (!hasValidUnicode(normalized)) {
            throw contract("ADS_FIELD_INVALID");
        }
        if (normalized.codePointCount(0, normalized.length()) > maxLength) {
            throw contract("ADS_FIELD_TOO_LONG");
        }
        return normalized;
    }

    String boundedRawPayload(String value, int maxUtf8Bytes) {
        String payload = value == null ? "" : value;
        if (!hasValidUnicode(payload)) {
            throw contract("ADS_FIELD_INVALID");
        }
        if (payload.getBytes(StandardCharsets.UTF_8).length > maxUtf8Bytes) {
            throw contract("ADS_FIELD_TOO_LARGE");
        }
        return payload;
    }

    LocalDate optionalDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() >= 10) {
            normalized = normalized.substring(0, 10);
        }
        try {
            LocalDate parsed = LocalDate.parse(normalized);
            if (parsed.getYear() < 1000 || parsed.getYear() > 9999) {
                throw contract("ADS_CAMPAIGN_DATE_OUT_OF_RANGE");
            }
            return parsed;
        } catch (NoonAdvertisingContractException known) {
            throw known;
        } catch (RuntimeException invalidDate) {
            throw new NoonAdvertisingContractException(
                    "ADS_CAMPAIGN_DATE_INVALID",
                    invalidDate
            );
        }
    }

    String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String camelSeparated = value.trim().replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        return camelSeparated.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    String classifyQuery(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.contains("/") || normalized.startsWith("supermall-")
                || "CART".equals(normalized) || "SEARCH".equals(normalized)
                || "PDP".equals(normalized)) {
            return "surface_or_category";
        }
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (codePoint >= 0x0600 && codePoint <= 0x06ff) {
                return "arabic_search_term";
            }
            offset += Character.charCount(codePoint);
        }
        return "search_term";
    }

    private BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value) || "-".equals(value.trim())) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim().replace(",", "").replace("%", ""));
        } catch (NumberFormatException invalidNumber) {
            throw new NoonAdvertisingContractException("ADS_NUMBER_INVALID", invalidNumber);
        }
    }

    private BigDecimal boundedDecimal(BigDecimal value, int precision, int scale) {
        if (integerDigits(value) > precision - scale) {
            throw contract("ADS_NUMBER_OUT_OF_RANGE");
        }
        BigDecimal normalized = value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
        if (integerDigits(normalized) > precision - scale) {
            throw contract("ADS_NUMBER_OUT_OF_RANGE");
        }
        return normalized;
    }

    private int integerDigits(BigDecimal value) {
        return value.signum() == 0 ? 0 : Math.max(0, value.precision() - value.scale());
    }

    private boolean hasValidUnicode(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\0') return false;
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(character)) {
                return false;
            }
        }
        return true;
    }

    private NoonAdvertisingContractException contract(String code) {
        return new NoonAdvertisingContractException(code);
    }
}
