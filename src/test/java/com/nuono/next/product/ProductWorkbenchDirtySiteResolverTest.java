package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductWorkbenchDirtySiteResolverTest {

    private final ProductWorkbenchDirtySiteResolver resolver =
            new ProductWorkbenchDirtySiteResolver(new ObjectMapper());

    @Test
    void resolvesChangedSiteOfferCodes() {
        ProductMasterSnapshotView baseline = snapshot(List.of(offer("STR245027-NAE", "188.00")));
        ProductMasterSnapshotView draft = snapshot(List.of(offer("STR245027-NAE", "199.00")));

        assertEquals(List.of("STR245027-NAE"), resolver.resolveDirtySiteCodes(draft, baseline));
    }

    @Test
    void ignoresEquivalentComparableOfferValues() {
        ProductMasterSnapshotView baseline = snapshot(List.of(equivalentOffer(
                "188.00",
                "168.0",
                "2026-04-13 00:00:00",
                "2026-04-30T23:59:59+00:00",
                "active",
                "3.0"
        )));
        ProductMasterSnapshotView draft = snapshot(List.of(equivalentOffer(
                "188",
                "168.00",
                "2026-04-13",
                "2026-04-30",
                "1",
                "3"
        )));

        assertEquals(List.of(), resolver.resolveDirtySiteCodes(draft, baseline));
    }

    @Test
    void resolvesNewSiteOfferAsDirty() {
        ProductMasterSnapshotView baseline = snapshot(List.of(offer("STR245027-NAE", "188.00")));
        ProductMasterSnapshotView draft = snapshot(List.of(
                offer("STR245027-NAE", "188.00"),
                offer("STR245027-NSA", "199.00")
        ));

        assertEquals(List.of("STR245027-NSA"), resolver.resolveDirtySiteCodes(draft, baseline));
    }

    private ProductMasterSnapshotView snapshot(List<Map<String, Object>> siteOffers) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setSiteOffers(siteOffers);
        return snapshot;
    }

    private Map<String, Object> offer(String storeCode, String price) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", storeCode);
        offer.put("site", storeCode.endsWith("-NSA") ? "sa" : "ae");
        offer.put("price", price);
        return offer;
    }

    private Map<String, Object> equivalentOffer(
            String price,
            String salePrice,
            String saleStart,
            String saleEnd,
            String isActive,
            String idWarranty
    ) {
        Map<String, Object> offer = offer("STR245027-NAE", price);
        offer.put("salePrice", salePrice);
        offer.put("saleStart", saleStart);
        offer.put("saleEnd", saleEnd);
        offer.put("priceMin", "100.00");
        offer.put("priceMax", "300.0");
        offer.put("isActive", isActive);
        offer.put("idWarranty", idWarranty);
        offer.put("offerNote", "keep note");
        offer.put("barcode", "ignored-local-field");
        return offer;
    }
}
