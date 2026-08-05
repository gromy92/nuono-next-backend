package com.nuono.next.noonpull;

import java.math.BigDecimal;

/** Exact persistence-shape conversion for the legacy Noon Ads INT fact tables. */
public final class NoonAdvertisingLegacyNumericContract {

    private NoonAdvertisingLegacyNumericContract() {}

    public static long requireIntCount(String value) {
        return requireIntCount(new BigDecimal(normalize(value)));
    }

    public static long requireIntCount(BigDecimal value) {
        if (value == null) {
            return 0L;
        }
        final long result;
        try {
            result = value.longValueExact();
        } catch (ArithmeticException invalid) {
            throw new IllegalArgumentException("Noon Ads count is outside BIGINT", invalid);
        }
        if (result < 0L || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Noon Ads legacy count is outside INT");
        }
        return result;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().replace(",", "").replace("%", "");
    }
}
