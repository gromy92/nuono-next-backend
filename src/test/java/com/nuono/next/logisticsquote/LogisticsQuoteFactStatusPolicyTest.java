package com.nuono.next.logisticsquote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LogisticsQuoteFactStatusPolicyTest {

    @Test
    void shouldResolveStableNaturalKeyWhenPublishedItemDoesNotCarryOne() {
        LogisticsQuoteFactNaturalKey naturalKey = new LogisticsQuoteFactNaturalKey();

        String resolved = naturalKey.resolve(item(
                "logistics_base_price",
                null,
                mapOf(
                        "forwarderCode", "qike",
                        "serviceLineKey", "qike|SA|air|headhaul|RUH",
                        "cargoCategoryKey", "qike|SA|air|headhaul|RUH|general",
                        "billingUnit", "kg",
                        "pricingModel", "unit_price"
                )
        ));

        assertEquals("qike|qike|SA|air|headhaul|RUH|qike|SA|air|headhaul|RUH|general|kg|unit_price", resolved);
    }

    @Test
    void shouldKeepManualConfirmCargoCategoryPending() {
        LogisticsQuoteFactStatusPolicy policy = new LogisticsQuoteFactStatusPolicy();

        String status = policy.initialStatus(
                LogisticsQuoteFactType.CARGO_CATEGORY,
                mapOf("manualConfirmRequired", true)
        );

        assertEquals(LogisticsQuoteFactStatus.PENDING_MANUAL_CONFIRM.value(), status);
    }

    @Test
    void shouldDetectIncompatiblePayloadsForSameNaturalKeyWithinOneLandingOperation() {
        LogisticsQuoteFactStatusPolicy policy = new LogisticsQuoteFactStatusPolicy();
        Map<String, Object> oldPayload = mapOf(
                "forwarderCode", "et",
                "serviceLineKey", "et|SA|air|headhaul|RUH",
                "cargoCategoryKey", "et|SA|air|headhaul|RUH|general",
                "unitPrice", "64",
                "currency", "SAR",
                "billingUnit", "kg",
                "pricingModel", "unit_price"
        );
        Map<String, Object> samePayload = mapOf(
                "pricingModel", "unit_price",
                "billingUnit", "kg",
                "currency", "SAR",
                "unitPrice", "64",
                "cargoCategoryKey", "et|SA|air|headhaul|RUH|general",
                "serviceLineKey", "et|SA|air|headhaul|RUH",
                "forwarderCode", "et"
        );
        Map<String, Object> changedPayload = mapOf(
                "forwarderCode", "et",
                "serviceLineKey", "et|SA|air|headhaul|RUH",
                "cargoCategoryKey", "et|SA|air|headhaul|RUH|general",
                "unitPrice", "63",
                "currency", "SAR",
                "billingUnit", "kg",
                "pricingModel", "unit_price"
        );

        String firstSignature = policy.payloadSignature(LogisticsQuoteFactType.PRICE_RULE, oldPayload);
        String sameSignature = policy.payloadSignature(LogisticsQuoteFactType.PRICE_RULE, samePayload);
        String changedSignature = policy.payloadSignature(LogisticsQuoteFactType.PRICE_RULE, changedPayload);

        assertEquals(firstSignature, sameSignature);
        assertNotEquals(firstSignature, changedSignature);
        assertFalse(policy.isConflictingDuplicate(firstSignature, sameSignature));
        assertTrue(policy.isConflictingDuplicate(firstSignature, changedSignature));
    }

    private static LogisticsQuotePublishedItem item(String itemType, String naturalKey, Map<String, Object> payload) {
        return new LogisticsQuotePublishedItem(
                itemType,
                naturalKey,
                payload,
                new LogisticsQuoteFactSourceLineage(
                        "file_management",
                        20112L,
                        40104L,
                        70024L,
                        88001L,
                        "ET物流报价-20260414入仓生效.pdf",
                        "page 1 row 1"
                )
        );
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}
