package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishHistoryAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductPublishHistoryAssembler assembler = new ProductPublishHistoryAssembler(objectMapper);

    @Test
    void publishTaskHistoryUsesTaskBaselineAndDraftFieldDiffs() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Old title", "39.90", "M");
        ProductMasterSnapshotView draft = snapshot("New title", "45.00", "L");
        ProductPublishTaskRecord task = task("synced", baseline, draft);

        List<Map<String, Object>> items = assembler.buildPublishTaskHistoryItems(List.of(task), List.of());

        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        assertEquals("publish_task", item.get("source"));
        assertEquals("publish-current", item.get("actionType"));
        assertEquals("发布成功", item.get("statusLabel"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) item.get("changes");
        assertFalse(changes.isEmpty());
        assertTrue(changes.stream().anyMatch((change) ->
                "英文标题".equals(change.get("label")) && "Old title".equals(change.get("before")) && "New title".equals(change.get("after"))));
        assertTrue(changes.stream().anyMatch((change) ->
                "活动价".equals(change.get("label")) && "39.90".equals(change.get("before")) && "45.00".equals(change.get("after"))));
        assertTrue(changes.stream().anyMatch((change) ->
                String.valueOf(change.get("label")).contains("尺码") && "M".equals(change.get("before")) && "L".equals(change.get("after"))));
    }

    @Test
    void pendingManualCheckMessageRecomputesUnknownDomainFromDiff() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "39.90", "M");
        ProductMasterSnapshotView draft = snapshot("Same title", "39.90", "L");
        ProductPublishTaskRecord task = task("pending_manual_check", baseline, draft);
        task.setChangedDomainsJson("[\"unknown\"]");

        Map<String, Object> summary = assembler.buildLastPublishTaskSummary(task, List.of());

        assertEquals("待人工核对", summary.get("statusLabel"));
        assertTrue(String.valueOf(summary.get("resultText")).contains("【尺码】"));
    }

    @Test
    void publishTaskWithoutFieldChangesDoesNotEnterModificationHistory() throws Exception {
        ProductMasterSnapshotView baseline = snapshot("Same title", "39.90", "M");
        ProductMasterSnapshotView draft = snapshot("Same title", "39.90", "M");
        ProductPublishTaskRecord task = task("synced", baseline, draft);

        assertEquals(List.of(), assembler.buildPublishTaskHistoryItems(List.of(task), List.of()));
    }

    private ProductPublishTaskRecord task(
            String status,
            ProductMasterSnapshotView baseline,
            ProductMasterSnapshotView draft
    ) throws Exception {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(64001L);
        task.setStatus(status);
        task.setCurrentSiteCode("STR245027-NAE");
        task.setPartnerSku("PARTNER-001");
        task.setPskuCode("PSKU-001");
        task.setBaselineJson(objectMapper.writeValueAsString(baseline));
        task.setDraftJson(objectMapper.writeValueAsString(draft));
        task.setChangedDomainsJson("[\"content\"]");
        task.setSubmittedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
        task.setFinishedAt(LocalDateTime.of(2026, 5, 20, 10, 5));
        return task;
    }

    private ProductMasterSnapshotView snapshot(String titleEn, String salePrice, String sizeEn) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setIdentity(new LinkedHashMap<>());
        snapshot.getIdentity().put("skuParent", "ZTEST001");
        snapshot.getIdentity().put("partnerSku", "PARTNER-001");
        snapshot.setTaxonomy(new LinkedHashMap<>());
        snapshot.setContent(new LinkedHashMap<>());
        snapshot.getContent().put("titleEn", titleEn);
        snapshot.getContent().put("images", List.of("https://img.example.com/1.jpg"));
        snapshot.setSiteOffers(List.of(offer("STR245027-NAE", "39.90", salePrice)));
        snapshot.setVariants(List.of(variant("CHILD-001", sizeEn)));
        snapshot.setKeyAttributes(List.of());
        return snapshot;
    }

    private Map<String, Object> offer(String storeCode, String price, String salePrice) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", storeCode);
        offer.put("site", "AE");
        offer.put("price", price);
        offer.put("salePrice", salePrice);
        return offer;
    }

    private Map<String, Object> variant(String childSku, String sizeEn) {
        Map<String, Object> variant = new LinkedHashMap<>();
        variant.put("childSku", childSku);
        variant.put("sizeEn", sizeEn);
        return variant;
    }
}
