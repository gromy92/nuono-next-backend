package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishReadbackReconcilerTest {

    private static final String CURRENT_SITE = "STR245027-NAE";

    private final ProductPublishReadbackReconciler reconciler =
            new ProductPublishReadbackReconciler(new ObjectMapper());

    @Test
    void returnsSyncedWhenAllPublishedChangesMatchNoonReadback() {
        ProductMasterSnapshotView baseline = snapshot("Old title", "39.90", "M", "Blue");
        ProductMasterSnapshotView draft = snapshot("New title", "45.00", "L", "Red");
        ProductMasterSnapshotView noonCurrent = snapshot("New title", "45.00", "L", "Red");

        ProductPublishReadbackReconciliation decision = reconciler.reconcile(
                baseline,
                draft,
                noonCurrent,
                CURRENT_SITE,
                1
        );

        assertEquals("synced", decision.getStatus());
        assertTrue(decision.isConfirmed());
        assertFalse(decision.shouldScheduleAnotherReadback());
    }

    @Test
    void keepsPendingEffectiveBeforeFinalAttemptWhenReadbackDoesNotMatch() {
        ProductMasterSnapshotView baseline = snapshot("Old title", "39.90", "M", "Blue");
        ProductMasterSnapshotView draft = snapshot("New title", "45.00", "L", "Red");
        ProductMasterSnapshotView noonCurrent = snapshot("Old title", "39.90", "M", "Blue");

        ProductPublishReadbackReconciliation decision = reconciler.reconcile(
                baseline,
                draft,
                noonCurrent,
                CURRENT_SITE,
                2
        );

        assertEquals("pending_effective", decision.getStatus());
        assertEquals("noon_effect_pending", decision.getErrorCode());
        assertTrue(decision.shouldScheduleAnotherReadback());
    }

    @Test
    void returnsPendingManualCheckAtFinalAttemptWhenReadbackStillDoesNotMatch() {
        ProductMasterSnapshotView baseline = snapshot("Old title", "39.90", "M", "Blue");
        ProductMasterSnapshotView draft = snapshot("New title", "45.00", "L", "Red");
        ProductMasterSnapshotView noonCurrent = snapshot("Old title", "39.90", "M", "Blue");

        ProductPublishReadbackReconciliation decision = reconciler.reconcile(
                baseline,
                draft,
                noonCurrent,
                CURRENT_SITE,
                3
        );

        assertEquals("pending_manual_check", decision.getStatus());
        assertEquals("noon_effect_not_confirmed", decision.getErrorCode());
        assertFalse(decision.shouldScheduleAnotherReadback());
    }

    @Test
    void returnsVerifyTimeoutDecisionWithoutConvertingToFailure() {
        ProductPublishReadbackReconciliation decision = reconciler.timeoutDecision();

        assertEquals("verify_timeout", decision.getStatus());
        assertEquals("noon_verify_timeout", decision.getErrorCode());
        assertTrue(decision.shouldScheduleAnotherReadback());
    }

    @Test
    void writeUnknownIsReadbackOnlyAndQueuedIsWriteable() {
        assertTrue(reconciler.isReadbackOnlyStatus("write_unknown"));
        assertTrue(reconciler.isReadbackOnlyStatus("verify_timeout"));
        assertTrue(reconciler.isReadbackOnlyStatus("pending_effective"));
        assertFalse(reconciler.isReadbackOnlyStatus("queued"));
    }

    private ProductMasterSnapshotView snapshot(String titleEn, String salePrice, String sizeEn, String colourName) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setIdentity(new LinkedHashMap<>());
        snapshot.getIdentity().put("skuParent", "ZTEST001");
        snapshot.getIdentity().put("brand", "brand-a");
        snapshot.setTaxonomy(new LinkedHashMap<>());
        snapshot.getTaxonomy().put("productFulltype", "home_decor-lighting");
        snapshot.setContent(new LinkedHashMap<>());
        snapshot.getContent().put("titleEn", titleEn);
        snapshot.getContent().put("images", List.of("https://img.example.com/1.jpg"));
        snapshot.setSiteOffers(new ArrayList<>(List.of(offer(CURRENT_SITE, "48.00", salePrice))));
        snapshot.setVariants(new ArrayList<>(List.of(variant("CHILD-001", sizeEn))));
        snapshot.setGroup(group("GROUP-A", "colour_name", "CHILD-001", colourName));
        snapshot.setKeyAttributes(new ArrayList<>());
        return snapshot;
    }

    private Map<String, Object> offer(String storeCode, String price, String salePrice) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", storeCode);
        offer.put("site", "AE");
        offer.put("price", price);
        offer.put("salePrice", salePrice);
        offer.put("pskuCode", "PSKU-001");
        return offer;
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
    }

    private Map<String, Object> group(String skuGroup, String axisCode, String skuParent, String value) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("skuGroup", skuGroup);
        group.put("axes", List.of(axis(axisCode)));
        group.put("members", List.of(groupMember(skuParent, axisCode, value)));
        return group;
    }

    private Map<String, Object> axis(String axisCode) {
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("axisCode", axisCode);
        axis.put("axisName", "Colour Name");
        return axis;
    }

    private Map<String, Object> groupMember(String skuParent, String axisCode, String value) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("skuParent", skuParent);
        member.put(axisCode, value);
        return member;
    }
}
