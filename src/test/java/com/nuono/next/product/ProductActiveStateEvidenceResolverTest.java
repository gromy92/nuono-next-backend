package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductActiveStateEvidenceResolverTest {

    @Test
    void resolvesOnlyTrustedEvidenceForTheExactStoreSiteAndPartnerSku() {
        ProductMasterSnapshotView snapshot = snapshot(List.of(
                offer("STR108065-NSA", "SA", "OTHER-PSKU", true, "NOON_PRICING_INFO"),
                offer("STR108065-NAE", "AE", "TARGET-PSKU", true, "NOON_PRICING_INFO"),
                offer("STR108065-NSA", "SA", "TARGET-PSKU", false, "NOON_PRICING_INFO")
        ));

        assertThat(ProductActiveStateEvidenceResolver.resolve(snapshot, target()))
                .contains(false);
    }

    @Test
    void rejectsUnprovenOrWrongProductEvidenceInsteadOfCompletingTheParentProduct() {
        ProductMasterSnapshotView snapshot = snapshot(List.of(
                offer("STR108065-NSA", "SA", "OTHER-PSKU", true, "NOON_PRICING_INFO"),
                offer("STR108065-NSA", "SA", "TARGET-PSKU", true, "PROJECTION_CACHE")
        ));

        assertThat(ProductActiveStateEvidenceResolver.resolve(snapshot, target())).isEmpty();
    }

    private ProductMasterSnapshotView snapshot(List<Map<String, Object>> offers) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.setSiteOffers(offers);
        return snapshot;
    }

    private Map<String, Object> offer(
            String storeCode,
            String site,
            String partnerSku,
            boolean active,
            String source
    ) {
        return Map.of(
                "storeCode", storeCode,
                "site", site,
                "partnerSku", partnerSku,
                "isActive", active,
                "activeStateSource", source
        );
    }

    private ProductActiveStateReconciliationCandidate target() {
        ProductActiveStateReconciliationCandidate candidate =
                new ProductActiveStateReconciliationCandidate();
        candidate.setStoreCode("STR108065-NSA");
        candidate.setSiteCode("SA");
        candidate.setPartnerSku("TARGET-PSKU");
        return candidate;
    }
}
