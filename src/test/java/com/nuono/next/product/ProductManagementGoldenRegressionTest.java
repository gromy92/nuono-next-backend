package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductManagementGoldenRegressionTest {

    @Test
    void shouldPreserveXingyaoOfferProjectionWhenPricingReadIsIncomplete() {
        ProductProjectionMergePolicy policy = new ProductProjectionMergePolicy();
        Map<String, Object> existing = new LinkedHashMap<>();
        existing.put("price", new BigDecimal("48.00"));
        existing.put("salePrice", new BigDecimal("39.90"));
        existing.put("priceMin", new BigDecimal("11.00"));
        existing.put("priceMax", new BigDecimal("55.00"));
        existing.put("saleStart", "2026-05-19 00:00:00");
        existing.put("saleEnd", "2036-05-19 23:59:59");

        Map<String, ProductFieldRead<?>> reads = new LinkedHashMap<>();
        reads.put("price", ProductFieldRead.error("Noon pricing timeout for MILKYWAYA17"));
        reads.put("salePrice", ProductFieldRead.absent());
        reads.put("priceMin", ProductFieldRead.absent());
        reads.put("priceMax", ProductFieldRead.absent());

        Map<String, Object> merged = policy.mergeProjectionFields(existing, reads, Set.of());

        assertEquals(new BigDecimal("48.00"), merged.get("price"));
        assertEquals(new BigDecimal("39.90"), merged.get("salePrice"));
        assertEquals(new BigDecimal("11.00"), merged.get("priceMin"));
        assertEquals(new BigDecimal("55.00"), merged.get("priceMax"));
    }

    @Test
    void shouldKeepLocalImagePublishBlockedInGoldenFlow() {
        ProductPublishPlanner planner = new ProductPublishPlanner(new ProductDraftMergePolicy());
        ProductMasterSnapshotView baseline = snapshot(List.of("https://f.nooncdn.com/p/pnsku/MILKYWAYA17/main.jpg"));
        ProductMasterSnapshotView draft = snapshot(List.of("/api/product-master/image-assets/local-milkyway.png"));

        ProductPublishPlan plan = planner.plan(draft, baseline, "STR245027-NAE");

        assertFalse(plan.isPublishable());
        assertTrue(plan.getBlockers().contains("本地上传图片仍是系统相对地址，不能发布到 Noon。"));
    }

    private ProductMasterSnapshotView snapshot(List<String> images) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("images", images);
        snapshot.setContent(content);
        snapshot.setSiteOffers(List.of(Map.of("storeCode", "STR245027-NAE", "price", "48.00")));
        return snapshot;
    }
}
