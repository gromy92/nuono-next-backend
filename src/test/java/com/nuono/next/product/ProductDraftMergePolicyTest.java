package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductDraftMergePolicyTest {

    private final ProductDraftMergePolicy policy = new ProductDraftMergePolicy();

    @Test
    void shouldHydrateMissingPublishOfferFieldsFromBaseline() {
        Map<String, Object> draftOffer = new LinkedHashMap<>();
        draftOffer.put("storeCode", "STR245027-NAE");
        draftOffer.put("pskuCode", "");
        draftOffer.put("priceMin", "9.13");

        Map<String, Object> baselineOffer = new LinkedHashMap<>();
        baselineOffer.put("storeCode", "STR245027-NAE");
        baselineOffer.put("pskuCode", "PSKU-001");
        baselineOffer.put("price", "48.00");
        baselineOffer.put("salePrice", "39.90");
        baselineOffer.put("saleStart", "2026-05-19 00:00:00");
        baselineOffer.put("saleEnd", "2036-05-19 23:59:59");
        baselineOffer.put("priceMin", "11.00");
        baselineOffer.put("priceMax", "55.00");
        baselineOffer.put("isActive", true);

        Map<String, Object> hydrated = policy.hydrateMissingOfferFieldsForPublish(
                draftOffer,
                baselineOffer,
                List.of("pskuCode", "price", "salePrice", "saleStart", "saleEnd", "priceMin", "priceMax", "isActive")
        );

        assertEquals("PSKU-001", hydrated.get("pskuCode"));
        assertEquals("48.00", hydrated.get("price"));
        assertEquals("39.90", hydrated.get("salePrice"));
        assertEquals("2026-05-19 00:00:00", hydrated.get("saleStart"));
        assertEquals("2036-05-19 23:59:59", hydrated.get("saleEnd"));
        assertEquals("9.13", hydrated.get("priceMin"));
        assertEquals("55.00", hydrated.get("priceMax"));
        assertEquals(true, hydrated.get("isActive"));
    }

    @Test
    void shouldKeepExplicitBlankDraftValuesAsUserClearIntent() {
        Map<String, Object> draftOffer = new LinkedHashMap<>();
        draftOffer.put("storeCode", "STR245027-NAE");
        draftOffer.put("salePrice", "");
        draftOffer.put("saleStart", "");
        draftOffer.put("saleEnd", "");

        Map<String, Object> baselineOffer = new LinkedHashMap<>();
        baselineOffer.put("salePrice", "39.90");
        baselineOffer.put("saleStart", "2026-05-19 00:00:00");
        baselineOffer.put("saleEnd", "2036-05-19 23:59:59");

        Map<String, Object> hydrated = policy.hydrateMissingOfferFieldsForPublish(
                draftOffer,
                baselineOffer,
                List.of("salePrice", "saleStart", "saleEnd")
        );

        assertEquals("", hydrated.get("salePrice"));
        assertEquals("", hydrated.get("saleStart"));
        assertEquals("", hydrated.get("saleEnd"));
    }

    @Test
    void shouldDetectExplicitDraftFieldPresenceByKeyNotValue() {
        Map<String, Object> draftOffer = new LinkedHashMap<>();
        draftOffer.put("saleEnd", "");

        assertTrue(policy.hasExplicitDraftValue(draftOffer, "saleEnd"));
        assertFalse(policy.hasExplicitDraftValue(draftOffer, "saleStart"));
    }
}
