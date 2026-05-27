package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishPreparationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductPublishPreparationService service = new ProductPublishPreparationService(
            objectMapper,
            new ProductDraftMergePolicy()
    );

    @Test
    void shouldHydrateMissingOfferFieldsFromBaselineWithoutMutatingDraft() {
        ProductMasterSnapshotView baseline = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "pskuCode", "PSKU-001",
                        "offerCode", "OFFER-001",
                        "currency", "AED",
                        "price", "48.00",
                        "salePrice", "39.90",
                        "saleStart", "2026-05-19T00:00:00+00:00",
                        "saleEnd", "2036-05-19T23:59:59+00:00",
                        "priceMin", "10.00",
                        "priceMax", "55.00",
                        "isActive", true,
                        "idWarranty", 33,
                        "offerNote", "baseline note"
                )
        );
        Map<String, Object> draftOffer = offer(
                "storeCode", "STR245027-NAE",
                "price", "48.00"
        );
        ProductMasterSnapshotView draft = snapshot(draftOffer);

        ProductMasterSnapshotView prepared = service.prepareForPublish(draft, baseline, "STR245027-NAE");

        assertNotSame(draft, prepared);
        assertFalse(draftOffer.containsKey("pskuCode"));
        assertFalse(draftOffer.containsKey("saleStart"));
        assertFalse(draftOffer.containsKey("offerNote"));

        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);
        assertEquals("PSKU-001", preparedOffer.get("pskuCode"));
        assertEquals("OFFER-001", preparedOffer.get("offerCode"));
        assertEquals("AED", preparedOffer.get("currency"));
        assertEquals("39.90", preparedOffer.get("salePrice"));
        assertEquals("2026-05-19T00:00:00+00:00", preparedOffer.get("saleStart"));
        assertEquals("2036-05-19T23:59:59+00:00", preparedOffer.get("saleEnd"));
        assertEquals("10.00", preparedOffer.get("priceMin"));
        assertEquals("55.00", preparedOffer.get("priceMax"));
        assertEquals(true, preparedOffer.get("isActive"));
        assertEquals(33, preparedOffer.get("idWarranty"));
        assertEquals("baseline note", preparedOffer.get("offerNote"));
    }

    @Test
    void shouldKeepExplicitBlankSaleFieldsAndMirrorThemToPricing() {
        ProductMasterSnapshotView baseline = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "price", "48.00",
                        "salePrice", "39.90",
                        "saleStart", "2026-05-19T00:00:00+00:00",
                        "saleEnd", "2036-05-19T23:59:59+00:00"
                )
        );
        ProductMasterSnapshotView draft = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "price", "48.00",
                        "salePrice", "",
                        "saleStart", "",
                        "saleEnd", ""
                )
        );

        ProductMasterSnapshotView prepared = service.prepareForPublish(draft, baseline, "STR245027-NAE");

        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);
        assertEquals("", preparedOffer.get("salePrice"));
        assertEquals("", preparedOffer.get("saleStart"));
        assertEquals("", preparedOffer.get("saleEnd"));
        assertEquals("", prepared.getPricing().get("salePrice"));
        assertEquals("", prepared.getPricing().get("saleStart"));
        assertEquals("", prepared.getPricing().get("saleEnd"));
    }

    @Test
    void shouldDefaultSaleWindowForDirtyOfferWithSalePriceWhenWindowIsMissing() {
        ProductMasterSnapshotView baseline = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "price", "48.00",
                        "salePrice", "39.20",
                        "priceMin", "10.00"
                )
        );
        ProductMasterSnapshotView draft = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "price", "48.00",
                        "salePrice", "39.20",
                        "priceMin", "9.13"
                )
        );

        ProductMasterSnapshotView prepared = service.prepareForPublish(draft, baseline, "STR245027-NAE");

        Map<String, Object> preparedOffer = prepared.getSiteOffers().get(0);
        LocalDate saleStart = LocalDate.parse(String.valueOf(preparedOffer.get("saleStart")));
        LocalDate saleEnd = LocalDate.parse(String.valueOf(preparedOffer.get("saleEnd")));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        assertTrue(!saleStart.isBefore(today.minusDays(1)));
        assertTrue(!saleStart.isAfter(today.plusDays(1)));
        assertEquals(saleStart.plusYears(10), saleEnd);
    }

    @Test
    void shouldMirrorCurrentSiteOfferFieldsToPricing() {
        ProductMasterSnapshotView baseline = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "price", "48.00",
                        "salePrice", "39.90"
                ),
                offer(
                        "storeCode", "STR245027-NSA",
                        "site", "SA",
                        "price", "60.00",
                        "salePrice", "50.00"
                )
        );
        ProductMasterSnapshotView draft = snapshot(
                offer(
                        "storeCode", "STR245027-NAE",
                        "site", "AE",
                        "price", "49.00",
                        "salePrice", "38.80",
                        "saleStart", "2026-05-20 00:00:00",
                        "saleEnd", "2036-05-20 23:59:59",
                        "priceMin", "9.00",
                        "priceMax", "55.00",
                        "isActive", false,
                        "idWarranty", 12,
                        "offerNote", "current note"
                ),
                offer(
                        "storeCode", "STR245027-NSA",
                        "site", "SA",
                        "price", "60.00",
                        "salePrice", "50.00",
                        "offerNote", "other note"
                )
        );
        draft.getPricing().put("price", "old");

        ProductMasterSnapshotView prepared = service.prepareForPublish(draft, baseline, "STR245027-NAE");

        Map<String, Object> pricing = prepared.getPricing();
        assertEquals("49.00", pricing.get("price"));
        assertEquals("38.80", pricing.get("salePrice"));
        assertEquals("2026-05-20 00:00:00", pricing.get("saleStart"));
        assertEquals("2036-05-20 23:59:59", pricing.get("saleEnd"));
        assertEquals("9.00", pricing.get("priceMin"));
        assertEquals("55.00", pricing.get("priceMax"));
        assertEquals(false, pricing.get("isActive"));
        assertEquals(12, pricing.get("idWarranty"));
        assertEquals("current note", pricing.get("offerNote"));
    }

    @SafeVarargs
    private final ProductMasterSnapshotView snapshot(Map<String, Object>... offers) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        Map<String, Object> storeContext = new LinkedHashMap<>();
        storeContext.put("storeCode", "STR245027-NAE");
        snapshot.setStoreContext(storeContext);
        snapshot.setSiteOffers(new ArrayList<>(List.of(offers)));
        snapshot.setPricing(new LinkedHashMap<>());
        return snapshot;
    }

    private Map<String, Object> offer(Object... values) {
        Map<String, Object> offer = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            offer.put(String.valueOf(values[i]), values[i + 1]);
        }
        return offer;
    }
}
