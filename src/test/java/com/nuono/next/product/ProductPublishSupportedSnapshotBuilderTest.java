package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishSupportedSnapshotBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductPublishSupportedSnapshotBuilder builder = new ProductPublishSupportedSnapshotBuilder(
            objectMapper,
            new ProductPublishPlanner(new ProductDraftMergePolicy())
    );

    @Test
    void shouldBuildPublishableSnapshotCopyAndRecordPlannerBlockers() {
        ProductMasterSnapshotView baseline = snapshotWithOffer("STR245027-NAE", "48.00");
        baseline.getSiteOffers().get(0).put("saleStart", "2026-05-19 00:00:00");
        baseline.getSiteOffers().get(0).put("saleEnd", "2036-05-19 23:59:59");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getSiteOffers().get(0).put("price", "49.00");
        draft.getSiteOffers().get(0).remove("saleStart");
        draft.getSiteOffers().get(0).remove("saleEnd");
        draft.getContent().put("images", List.of("/api/product-master/image-assets/local.png"));
        ProductPublishUnsupportedChanges unsupportedChanges = new ProductPublishUnsupportedChanges();

        ProductMasterSnapshotView publishable = builder.build(draft, baseline, unsupportedChanges);

        assertNotSame(draft, publishable);
        assertFalse(draft.getSiteOffers().get(0).containsKey("saleStart"));
        assertEquals("2026-05-19 00:00:00", publishable.getSiteOffers().get(0).get("saleStart"));
        assertEquals("2036-05-19 23:59:59", publishable.getSiteOffers().get(0).get("saleEnd"));
        assertTrue(unsupportedChanges.getPublishBlockers().isEmpty());
    }

    @Test
    void shouldOverlayUnsupportedDraftChangesAfterLiveVerification() {
        ProductMasterSnapshotView liveAfterPublish = snapshotWithOffer("STR245027-NAE", "49.00");
        liveAfterPublish.setGroup(new LinkedHashMap<>(Map.of("skuGroup", "BASE-GROUP")));
        liveAfterPublish.setVariants(new ArrayList<>(List.of(record("childSku", "SKU-BASE", "size", "S"))));
        liveAfterPublish.setKeyAttributes(new ArrayList<>(List.of(attribute("barcode", "BASE-CODE"))));
        liveAfterPublish.getSiteOffers().get(0).put("barcode", "BASE-SITE-CODE");

        ProductMasterSnapshotView sourceDraft = copySnapshot(liveAfterPublish);
        sourceDraft.setGroup(new LinkedHashMap<>(Map.of("skuGroup", "DRAFT-GROUP")));
        sourceDraft.setVariants(new ArrayList<>(List.of(record("childSku", "SKU-DRAFT", "size", "M"))));
        sourceDraft.setKeyAttributes(new ArrayList<>(List.of(attribute("barcode", "DRAFT-CODE"))));
        sourceDraft.getSiteOffers().get(0).put("barcode", "DRAFT-SITE-CODE");
        sourceDraft.getSiteOffers().get(0).put("price", "55.00");

        ProductPublishUnsupportedChanges unsupportedChanges = new ProductPublishUnsupportedChanges();
        unsupportedChanges.setGroupChanged(true);
        unsupportedChanges.setVariantStructureChanged(true);
        unsupportedChanges.getUnsupportedAttributeCodes().add("barcode");
        unsupportedChanges.markUnsupportedSiteField("STR245027-NAE", "barcode");

        builder.overlayUnsupportedDraft(liveAfterPublish, sourceDraft, unsupportedChanges);

        assertEquals("DRAFT-GROUP", liveAfterPublish.getGroup().get("skuGroup"));
        assertEquals("SKU-DRAFT", liveAfterPublish.getVariants().get(0).get("childSku"));
        assertEquals("DRAFT-CODE", liveAfterPublish.getKeyAttributes().get(0).get("commonValue"));
        assertEquals("DRAFT-SITE-CODE", liveAfterPublish.getSiteOffers().get(0).get("barcode"));
        assertEquals("49.00", liveAfterPublish.getSiteOffers().get(0).get("price"));
    }

    private ProductMasterSnapshotView snapshotWithOffer(String storeCode, String price) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setContent(new LinkedHashMap<>());
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", storeCode);
        offer.put("site", "AE");
        offer.put("price", price);
        snapshot.setSiteOffers(new ArrayList<>(List.of(offer)));
        snapshot.setKeyAttributes(new ArrayList<>());
        snapshot.setVariants(new ArrayList<>());
        snapshot.setGroup(new LinkedHashMap<>());
        return snapshot;
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private Map<String, Object> attribute(String code, String value) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", value);
        return attribute;
    }

    private Map<String, Object> record(String key, Object value, String secondKey, Object secondValue) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put(key, value);
        record.put(secondKey, secondValue);
        return record;
    }
}
