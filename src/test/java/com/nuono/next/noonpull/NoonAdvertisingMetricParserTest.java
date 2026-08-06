package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class NoonAdvertisingMetricParserTest {
    private final NoonAdvertisingMetricParser parser = new NoonAdvertisingMetricParser();

    @Test
    void persistenceTextRejectsNulAndUnpairedSurrogates() {
        assertCode("ADS_FIELD_INVALID", () -> parser.boundedText("bad\0value", 100));
        assertCode("ADS_FIELD_INVALID", () -> parser.boundedText("bad\ud800", 100));
        assertEquals("😀", parser.boundedText("😀", 1));
    }

    @Test
    void mysqlDateRangeIsEnforcedBeforeFactPersistence() {
        assertCode(
                "ADS_CAMPAIGN_DATE_OUT_OF_RANGE",
                () -> parser.optionalDate("0999-12-31")
        );
        assertEquals("1000-01-01", parser.optionalDate("1000-01-01").toString());
    }

    @Test
    void rawPayloadUsesUtf8ByteBudget() {
        assertEquals("é", parser.boundedRawPayload("é", 2));
        assertCode("ADS_FIELD_TOO_LARGE", () -> parser.boundedRawPayload("é", 1));
    }

    @Test
    void factCountsUseTheFullSignedBigintRangeAndRejectOverflowExactly() {
        assertEquals(2_147_483_648L, parser.nonNegativeLong("2147483648"));
        assertEquals(Long.MAX_VALUE, parser.nonNegativeLong("9223372036854775807"));
        assertCode(
                "ADS_COUNT_OUT_OF_RANGE",
                () -> parser.nonNegativeLong("9223372036854775808")
        );
        assertCode("ADS_COUNT_INVALID", () -> parser.nonNegativeLong("1.5"));
    }

    private void assertCode(String code, Runnable action) {
        NoonAdvertisingContractException failure = assertThrows(
                NoonAdvertisingContractException.class, action::run
        );
        assertEquals(code, failure.getSanitizedCode());
    }
}
