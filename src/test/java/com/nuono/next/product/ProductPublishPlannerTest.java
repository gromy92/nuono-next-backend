package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishPlannerTest {

    private final ProductPublishPlanner planner = new ProductPublishPlanner(new ProductDraftMergePolicy());

    @Test
    void shouldBlockLocalUploadedImageUrl() {
        ProductMasterSnapshotView baseline = snapshotWithImages(List.of("https://f.nooncdn.com/p/main.jpg"));
        ProductMasterSnapshotView draft = snapshotWithImages(List.of("/api/product-master/image-assets/local.png"));

        ProductPublishPlan plan = planner.plan(draft, baseline, "STR245027-NAE");

        assertFalse(plan.isPublishable());
        assertTrue(plan.getBlockers().contains("本地上传图片仍是系统相对地址，不能发布到 Noon。"));
    }

    @Test
    void shouldHydrateMissingOfferFieldsBeforePlanning() {
        ProductMasterSnapshotView baseline = snapshotWithOffer("48.00", "39.90", "2026-05-19 00:00:00", "2036-05-19 23:59:59");
        ProductMasterSnapshotView draft = snapshotWithOffer("49.00", null, null, null);
        draft.getSiteOffers().get(0).remove("salePrice");
        draft.getSiteOffers().get(0).remove("saleStart");
        draft.getSiteOffers().get(0).remove("saleEnd");

        ProductPublishPlan plan = planner.plan(draft, baseline, "STR245027-NAE");

        assertTrue(plan.isPublishable());
        Map<String, Object> offer = plan.getPublishableSnapshot().getSiteOffers().get(0);
        assertEquals("49.00", offer.get("price"));
        assertEquals("39.90", offer.get("salePrice"));
        assertEquals("2026-05-19 00:00:00", offer.get("saleStart"));
        assertEquals("2036-05-19 23:59:59", offer.get("saleEnd"));
    }

    private ProductMasterSnapshotView snapshotWithImages(List<String> images) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setContent(new LinkedHashMap<>());
        snapshot.getContent().put("images", images);
        snapshot.setSiteOffers(List.of());
        return snapshot;
    }

    private ProductMasterSnapshotView snapshotWithOffer(
            String price,
            String salePrice,
            String saleStart,
            String saleEnd
    ) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", "STR245027-NAE");
        offer.put("price", price);
        if (salePrice != null) {
            offer.put("salePrice", salePrice);
        }
        if (saleStart != null) {
            offer.put("saleStart", saleStart);
        }
        if (saleEnd != null) {
            offer.put("saleEnd", saleEnd);
        }
        snapshot.setSiteOffers(List.of(offer));
        snapshot.setContent(new LinkedHashMap<>());
        return snapshot;
    }
}
