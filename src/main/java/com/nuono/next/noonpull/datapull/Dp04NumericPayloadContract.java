package com.nuono.next.noonpull.datapull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

/** Numeric syntax and target-column bounds for the whitelisted DP-04 payload. */
final class Dp04NumericPayloadContract {
    private static final List<String> DECIMAL_FIELDS = List.of(
            "base_price", "original_price", "sale_price", "price"
    );
    private static final List<String> INTEGER_FIELDS = List.of(
            "fbn_stock", "supermall_stock", "fbp_stock"
    );

    private Dp04NumericPayloadContract() {
    }

    static void requireSyntax(Map<String, Object> payload) {
        for (String field : DECIMAL_FIELDS) {
            requireParsable(payload.get(field), true);
        }
        for (String field : INTEGER_FIELDS) {
            requireParsable(payload.get(field), false);
        }
    }

    static boolean fieldsFit(Map<String, Object> payload) {
        for (String field : DECIMAL_FIELDS) {
            if (!decimalFits(payload.get(field))) {
                return false;
            }
        }
        for (String field : INTEGER_FIELDS) {
            if (!integerFits(payload.get(field))) {
                return false;
            }
        }
        return true;
    }

    private static boolean decimalFits(Object value) {
        if (value == null) {
            return true;
        }
        try {
            BigDecimal number = decimal(value, true);
            BigDecimal normalized = number.stripTrailingZeros();
            int scale = Math.max(normalized.scale(), 0);
            int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
            return number.signum() >= 0 && scale <= 2 && integerDigits <= 10;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static boolean integerFits(Object value) {
        if (value == null) {
            return true;
        }
        try {
            BigDecimal number = decimal(value, false);
            return number.signum() >= 0
                    && number.stripTrailingZeros().scale() <= 0
                    && number.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) <= 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static void requireParsable(Object value, boolean commaSeparated) {
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return;
        }
        try {
            decimal(value, commaSeparated);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("DP-04 numeric field is invalid", invalid);
        }
    }

    private static BigDecimal decimal(Object value, boolean commaSeparated) {
        String text = String.valueOf(value);
        return new BigDecimal(commaSeparated ? text.replace(",", "") : text);
    }
}
