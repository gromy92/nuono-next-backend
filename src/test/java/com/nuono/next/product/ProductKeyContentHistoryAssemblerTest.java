package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nuono.next.product.ProductKeyContentHistoryAssembler.KeyContentHistoryCandidate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductKeyContentHistoryAssemblerTest {

    private final ProductKeyContentHistoryAssembler assembler = new ProductKeyContentHistoryAssembler();

    @Test
    void shouldCaptureOnlySalesImpactingSharedContentChanges() {
        ProductMasterSnapshotView baseline = snapshot(
                "Old title",
                "旧标题",
                "Old description",
                "旧描述",
                List.of("https://img.example.com/old-1.jpg")
        );
        ProductMasterSnapshotView published = snapshot(
                "New title",
                "新标题",
                "New description",
                "新描述",
                List.of("https://img.example.com/new-1.jpg", "https://img.example.com/new-2.jpg")
        );

        KeyContentHistoryCandidate candidate = assembler.buildCandidate(baseline, published, "STR245027-NAE");

        assertNotNull(candidate);
        assertEquals(List.of("title", "description", "images"), candidate.getChangeTypes());

        Map<String, Object> summary = candidate.getSummary();
        assertEquals("STR245027-NAE", summary.get("targetSiteCode"));
        assertEquals("SKU-PARENT-001", summary.get("skuParent"));

        @SuppressWarnings("unchecked")
        Map<String, Object> titleChange = (Map<String, Object>) summary.get("title");
        @SuppressWarnings("unchecked")
        Map<String, Object> titleBefore = (Map<String, Object>) titleChange.get("before");
        @SuppressWarnings("unchecked")
        Map<String, Object> titleAfter = (Map<String, Object>) titleChange.get("after");
        assertEquals("Old title", titleBefore.get("titleEn"));
        assertEquals("New title", titleAfter.get("titleEn"));

        @SuppressWarnings("unchecked")
        Map<String, Object> imageChange = (Map<String, Object>) summary.get("images");
        assertEquals(List.of("https://img.example.com/old-1.jpg"), imageChange.get("before"));
        assertEquals(
                List.of("https://img.example.com/new-1.jpg", "https://img.example.com/new-2.jpg"),
                imageChange.get("after")
        );
    }

    @Test
    void shouldIgnoreSiteOfferOnlyChanges() {
        ProductMasterSnapshotView baseline = snapshot(
                "Same title",
                "相同标题",
                "Same description",
                "相同描述",
                List.of("https://img.example.com/same.jpg")
        );
        ProductMasterSnapshotView published = snapshot(
                "Same title",
                "相同标题",
                "Same description",
                "相同描述",
                List.of("https://img.example.com/same.jpg")
        );
        published.getSiteOffers().get(0).put("price", "199.00");

        KeyContentHistoryCandidate candidate = assembler.buildCandidate(baseline, published, "STR245027-NAE");

        assertNull(candidate);
    }

    private ProductMasterSnapshotView snapshot(
            String titleEn,
            String titleAr,
            String descriptionEn,
            String descriptionAr,
            List<String> images
    ) {
        ProductMasterSnapshotView view = new ProductMasterSnapshotView();
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("skuParent", "SKU-PARENT-001");
        identity.put("partnerSku", "PARTNER-SKU-001");
        identity.put("pskuCode", "PSKU-001");
        identity.put("offerCode", "OFFER-001");
        view.setIdentity(identity);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("titleEn", titleEn);
        content.put("titleAr", titleAr);
        content.put("descriptionEn", descriptionEn);
        content.put("descriptionAr", descriptionAr);
        content.put("images", new ArrayList<>(images));
        view.setContent(content);

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", "STR245027-NAE");
        siteOffer.put("price", "188.00");
        siteOffer.put("salePrice", "168.00");
        view.setSiteOffers(new ArrayList<>(List.of(siteOffer)));
        return view;
    }
}
