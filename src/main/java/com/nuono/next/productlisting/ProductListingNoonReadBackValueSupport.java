package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class ProductListingNoonReadBackValueSupport {

    private static final int DEFAULT_SALE_WINDOW_YEARS = 20;

    void requireMeaningfulText(
            List<String> fields,
            String field,
            String expected,
            String actual
    ) {
        if (hasMeaningfulText(expected) && !sameText(expected, actual, false)) {
            fields.add(field);
        }
    }

    void requireText(
            List<String> fields,
            String field,
            String expected,
            String actual,
            boolean ignoreCase,
            boolean brand
    ) {
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (!(brand && sameBrand(expected, actual))
                && !sameText(expected, actual, ignoreCase)) {
            fields.add(field);
        }
    }

    void requireDecimal(
            List<String> fields,
            String field,
            BigDecimal expected,
            JsonNode actual
    ) {
        if (expected == null) {
            return;
        }
        BigDecimal actualValue = decimal(actual);
        if (actualValue == null || expected.compareTo(actualValue) != 0) {
            fields.add(field);
        }
    }

    void requireOfferDate(
            List<String> fields,
            String field,
            String expected,
            String actual
    ) {
        if (StringUtils.hasText(expected)
                && !expected.equals(normalizeOfferDate(actual))) {
            fields.add(field);
        }
    }

    String expectedSaleStart(ProductListingDraftCommand draft) {
        String explicit = normalizeOfferDate(draft.getSaleStart());
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        return draft.getSalePrice() == null ? null : LocalDate.now().toString();
    }

    String expectedSaleEnd(ProductListingDraftCommand draft) {
        String explicit = normalizeOfferDate(draft.getSaleEnd());
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String start = expectedSaleStart(draft);
        return StringUtils.hasText(start)
                ? LocalDate.parse(start).plusYears(DEFAULT_SALE_WINDOW_YEARS).toString()
                : null;
    }

    boolean sameText(String expected, String actual, boolean ignoreCase) {
        String left = normalizeComparable(expected);
        String right = normalizeComparable(actual);
        if (!StringUtils.hasText(left)) {
            return true;
        }
        if (!StringUtils.hasText(right)) {
            return false;
        }
        return ignoreCase ? left.equalsIgnoreCase(right) : left.equals(right);
    }

    boolean hasMeaningfulText(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return StringUtils.hasText(value
                .replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .trim());
    }

    String text(JsonNode node, String field) {
        if (node == null || !StringUtils.hasText(field)) {
            return "";
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    String firstNonBlank(String... values) {
        if (values != null) {
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    BigDecimal firstNonNull(BigDecimal left, BigDecimal right) {
        return left == null ? right : left;
    }

    String upper(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String normalizeOfferDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }
        try {
            return ZonedDateTime.parse(text).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            // Fall through.
        }
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean sameBrand(String expected, String actual) {
        String left = normalizeBrand(expected);
        String right = normalizeBrand(actual);
        return StringUtils.hasText(left)
                && StringUtils.hasText(right) && left.equals(right);
    }

    private String normalizeComparable(String value) {
        return StringUtils.hasText(value)
                ? value.replace("&nbsp;", " ")
                .replace("&#160;", " ")
                .replaceAll("\\s+", " ")
                .trim()
                : "";
    }

    private String normalizeBrand(String value) {
        return normalizeComparable(value)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
