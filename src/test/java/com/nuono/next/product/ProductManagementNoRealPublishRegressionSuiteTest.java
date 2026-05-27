package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductManagementNoRealPublishRegressionSuiteTest {

    private static final Long STANDARD_OWNER_USER_ID = 10002L;
    private static final String STANDARD_STORE_CODE = "STR245027-NAE";
    private static final String STANDARD_SKU_PARENT = "Z580978E7ED8F9491B50BZ";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductPublishValidationService validationService = new ProductPublishValidationService(
            objectMapper,
            new ProductPublishPlanner(new ProductDraftMergePolicy())
    );
    private final ProductPublishHistoryAssembler historyAssembler = new ProductPublishHistoryAssembler(objectMapper);

    @Test
    void automatedRegressionFlowsRejectRealPublishCurrentActions() {
        ProductManagementNoRealPublishGuard guard = new ProductManagementNoRealPublishGuard();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                guard.assertNoRealPublishActions(List.of(
                        "load-list",
                        "open-detail",
                        "load-group-candidates",
                        "publish-current"
                )));

        assertTrue(exception.getMessage().contains("publish-current"));
    }

    @Test
    void automatedRegressionFlowsAllowReadAndDraftOnlyActions() {
        ProductManagementNoRealPublishGuard guard = new ProductManagementNoRealPublishGuard();

        assertDoesNotThrow(() ->
                guard.assertNoRealPublishActions(List.of(
                        "load-list",
                        "open-detail",
                        "load-group-candidates",
                        "load-history",
                        "pull-from-noon",
                        "save-draft",
                        "rollback-draft"
                )));
    }

    @Test
    void standardSampleReadFlowCoversDetailTabsGroupPublishStatusAndHistoryWithoutRealPublish() throws Exception {
        ProductManagementNoRealPublishGuard guard = new ProductManagementNoRealPublishGuard();
        assertDoesNotThrow(() ->
                guard.assertNoRealPublishActions(List.of(
                        "load-list",
                        "open-detail",
                        "offer-tab",
                        "content-tab",
                        "sizes-tab",
                        "group-flow",
                        "publish-status-summary",
                        "load-history",
                        "pull-from-noon"
                )));

        ProductMasterSnapshotView baseline = standardSampleSnapshot("Old title", "39.90", "M");
        ProductMasterSnapshotView draft = copySnapshot(baseline);
        draft.getContent().put("titleEn", "New title");
        draft.getSiteOffers().get(0).put("salePrice", "45.00");
        draft.getVariants().get(0).put("sizeEn", "L");
        draft.setGroup(group(
                "GROUP-A",
                "12in1",
                List.of(axis("colour_name", "Colour Name")),
                List.of(
                        groupMember(STANDARD_SKU_PARENT, "colour_name", "Red"),
                        groupMember("UNGROUPED-003", "colour_name", "Yellow")
                )
        ));

        ProductSnapshotReaderService snapshotReader =
                new ProductSnapshotReaderService((command, reason, seed) -> baseline, objectMapper);
        ProductBaselineRefreshDecision refreshDecision =
                snapshotReader.refreshBaseline(baseline, baseline, draft, "keep_draft");
        assertEquals("draft", refreshDecision.getSyncStatus());
        assertEquals("New title", refreshDecision.getDraftSnapshot().getContent().get("titleEn"));

        assertEquals(STANDARD_STORE_CODE, baseline.getSiteOffers().get(0).get("storeCode"));
        assertTrue(baseline.getContent().containsKey("titleEn"));
        assertFalse(baseline.getVariants().isEmpty());
        assertEquals("GROUP-A", baseline.getGroup().get("skuGroup"));

        ProductUnsupportedChanges unsupportedChanges =
                validationService.detectUnsupportedChanges(draft, baseline, STANDARD_STORE_CODE);
        assertFalse(unsupportedChanges.isGroupChanged());
        assertEquals(List.of(), validationService.validateWriteCoverageOnly(unsupportedChanges));

        ProductPublishTaskRecord task = publishTask("pending_manual_check", baseline, draft);
        Map<String, Object> summary = historyAssembler.buildLastPublishTaskSummary(task, List.of());
        assertEquals("待人工核对", summary.get("statusLabel"));
        assertTrue(String.valueOf(summary.get("resultText")).contains("图文内容"));

        List<Map<String, Object>> historyItems = historyAssembler.buildPublishTaskHistoryItems(List.of(task), List.of());
        assertEquals(1, historyItems.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) historyItems.get(0).get("changes");
        assertTrue(changes.stream().anyMatch((change) ->
                "英文标题".equals(change.get("label")) && "Old title".equals(change.get("before"))));
        assertTrue(changes.stream().anyMatch((change) ->
                "活动价".equals(change.get("label")) && "45.00".equals(change.get("after"))));
        assertTrue(changes.stream().anyMatch((change) ->
                String.valueOf(change.get("label")).contains("尺码") && "L".equals(change.get("after"))));
    }

    private ProductMasterSnapshotView standardSampleSnapshot(String titleEn, String salePrice, String sizeEn) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.getStoreContext().put("ownerUserId", STANDARD_OWNER_USER_ID);
        snapshot.getStoreContext().put("storeCode", STANDARD_STORE_CODE);
        snapshot.getStoreContext().put("projectCode", "PRJ245027");
        snapshot.getStoreContext().put("projectName", "xingyao");
        snapshot.getStoreContext().put("fetchedAt", "2026-05-20 10:00:00");

        snapshot.getIdentity().put("skuParent", STANDARD_SKU_PARENT);
        snapshot.getIdentity().put("partnerSku", "MILKYWAYA01");
        snapshot.getIdentity().put("pskuCode", "PSKU-001");
        snapshot.getIdentity().put("brand", "test-brand");

        snapshot.getTaxonomy().put("productFulltype", "home_decor-lighting");
        snapshot.getContent().put("titleEn", titleEn);
        snapshot.getContent().put("descriptionEn", "Standard sample description");
        snapshot.getContent().put("images", new ArrayList<>(List.of("https://img.example.com/1.jpg")));
        snapshot.setKeyAttributes(new ArrayList<>(List.of(attribute("base_material", "Plastic"))));
        snapshot.setVariants(new ArrayList<>(List.of(variant("CHILD-SKU-001", sizeEn))));
        snapshot.setSiteOffers(new ArrayList<>(List.of(offer(
                "storeCode", STANDARD_STORE_CODE,
                "site", "AE",
                "price", "48.00",
                "salePrice", salePrice,
                "priceMin", "10.00",
                "priceMax", "55.00",
                "pskuCode", "PSKU-001"
        ))));
        snapshot.setGroup(group(
                "GROUP-A",
                "12in1",
                List.of(axis("colour_name", "Colour Name")),
                List.of(
                        groupMember(STANDARD_SKU_PARENT, "colour_name", "Blue"),
                        groupMember("GROUP-MEMBER-002", "colour_name", "Green")
                )
        ));
        return snapshot;
    }

    private ProductPublishTaskRecord publishTask(
            String status,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView draft
    ) throws Exception {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setStatus(status);
        task.setCurrentSiteCode(STANDARD_STORE_CODE);
        task.setPartnerSku("MILKYWAYA01");
        task.setPskuCode("PSKU-001");
        task.setBaselineJson(objectMapper.writeValueAsString(baseline));
        task.setDraftJson(objectMapper.writeValueAsString(draft));
        task.setChangedDomainsJson("[\"content\",\"offer\",\"sizes\",\"group\"]");
        task.setSubmittedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
        task.setFinishedAt(LocalDateTime.of(2026, 5, 20, 10, 5));
        return task;
    }

    private ProductMasterSnapshotView copySnapshot(ProductMasterSnapshotView source) {
        return objectMapper.convertValue(source, ProductMasterSnapshotView.class);
    }

    private Map<String, Object> offer(Object... values) {
        Map<String, Object> offer = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            offer.put(String.valueOf(values[i]), values[i + 1]);
        }
        return offer;
    }

    private Map<String, Object> attribute(String code, Object commonValue) {
        Map<String, Object> attribute = new LinkedHashMap<>();
        attribute.put("code", code);
        attribute.put("commonValue", commonValue);
        return attribute;
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
    }

    private Map<String, Object> group(
            String skuGroup,
            String groupRef,
            List<Map<String, Object>> axes,
            List<Map<String, Object>> members
    ) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("skuGroup", skuGroup);
        group.put("groupRef", groupRef);
        group.put("groupRefCanonical", groupRef.toUpperCase());
        group.put("conditionsBrand", "test-brand");
        group.put("conditionsFulltype", "home_decor-lighting");
        group.put("axes", axes);
        group.put("members", members);
        group.put("memberCount", members.size());
        return group;
    }

    private Map<String, Object> axis(String axisCode, String axisName) {
        Map<String, Object> axis = new LinkedHashMap<>();
        axis.put("axisCode", axisCode);
        axis.put("axisName", axisName);
        return axis;
    }

    private Map<String, Object> groupMember(String skuParent, String axisCode, String axisValue) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("skuParent", skuParent);
        member.put("axisValues", Map.of(axisCode, axisValue));
        return member;
    }
}
