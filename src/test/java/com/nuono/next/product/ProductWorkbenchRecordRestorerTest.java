package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductWorkbenchRecordRestorerTest {

    private ProductWorkbenchRecordRestorer restorer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ProductSnapshotHydrator snapshotHydrator = new ProductSnapshotHydrator(objectMapper);
        ProductPublishOfferWriter offerWriter = new ProductPublishOfferWriter(objectMapper, null);
        ProductPublishPreparationService preparationService = new ProductPublishPreparationService(
                objectMapper,
                new ProductDraftMergePolicy(),
                offerWriter
        );
        restorer = new ProductWorkbenchRecordRestorer(snapshotHydrator, preparationService);
    }

    @Test
    void shouldCreateSyncedRecordFromLiveSnapshot() {
        ProductMasterSnapshotView live = snapshot("Live title");
        live.getStoreContext().put("fetchedAt", "2026-06-05 10:00:00");

        ProductWorkbenchRecord record = restorer.createSyncedRecord(live);
        record.getDraftSnapshot().getContent().put("titleEn", "Changed locally");

        assertEquals("synced", record.getSyncStatus());
        assertEquals("2026-06-05 10:00:00", record.getLastSyncedAt());
        assertEquals("已读取商品详情基线，可以开始调整共享主档和站点经营面。", record.getNote());
        assertEquals("Live title", record.getBaselineSnapshot().getContent().get("titleEn"));
        assertNotSame(record.getBaselineSnapshot(), record.getDraftSnapshot());
    }

    @Test
    void shouldRestorePersistedMetadataWhenSnapshotsAreMissing() {
        ProductProjectionPersistenceService.PersistedWorkbenchState persisted =
                new ProductProjectionPersistenceService.PersistedWorkbenchState();
        persisted.setRecentActions(new ArrayList<>(List.of(Map.of("actionType", "save"))));
        persisted.setKeyContentHistory(new ArrayList<>(List.of(Map.of("field", "titleEn"))));
        persisted.setPendingKeyContentHistoryCount(2);
        persisted.setPendingKeyContentHistoryVisibleAfter("2026-06-05 11:00:00");

        ProductWorkbenchRecord record = restorer.restorePersistedWorkbenchRecord(snapshot("Live title"), persisted);

        assertEquals("synced", record.getSyncStatus());
        assertEquals(List.of(Map.of("actionType", "save")), record.getRecentActions());
        assertEquals(List.of(Map.of("field", "titleEn")), record.getKeyContentHistory());
        assertEquals(2, record.getPendingKeyContentHistoryCount());
        assertEquals("2026-06-05 11:00:00", record.getPendingKeyContentHistoryVisibleAfter());
    }

    @Test
    void shouldRestoreUnpublishedDraftAgainstLatestLiveBaseline() {
        ProductMasterSnapshotView live = snapshot("Live title v2");
        live.getSiteOffers().get(0).put("finalPrice", "49.00");
        ProductProjectionPersistenceService.PersistedWorkbenchState persisted =
                new ProductProjectionPersistenceService.PersistedWorkbenchState();
        persisted.setBaselineSnapshot(snapshot("Live title v1"));
        persisted.setDraftSnapshot(snapshot("Draft title"));

        ProductWorkbenchRecord record = restorer.restorePersistedWorkbenchRecord(live, persisted);

        assertEquals("draft", record.getSyncStatus());
        assertEquals("已从本地库恢复未发布草稿。", record.getNote());
        assertEquals("Live title v2", record.getBaselineSnapshot().getContent().get("titleEn"));
        assertEquals("Draft title", record.getDraftSnapshot().getContent().get("titleEn"));
        assertEquals("49.00", record.getDraftSnapshot().getSiteOffers().get(0).get("finalPrice"));
    }

    @Test
    void shouldTreatPersistedDraftMatchingLiveAsPublishedResult() {
        ProductMasterSnapshotView live = snapshot("Published title");
        ProductProjectionPersistenceService.PersistedWorkbenchState persisted =
                new ProductProjectionPersistenceService.PersistedWorkbenchState();
        persisted.setBaselineSnapshot(snapshot("Old title"));
        persisted.setDraftSnapshot(snapshot("Published title"));

        ProductWorkbenchRecord record = restorer.restorePersistedWorkbenchRecord(live, persisted);

        assertEquals("synced", record.getSyncStatus());
        assertEquals("已按最新发布结果恢复工作台。", record.getNote());
        assertEquals("Published title", record.getDraftSnapshot().getContent().get("titleEn"));
    }

    private ProductMasterSnapshotView snapshot(String title) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getIdentity().put("brand", "test-brand");
        snapshot.getTaxonomy().put("productFulltype", "home_decor-lighting");
        snapshot.getContent().put("titleEn", title);

        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", "STR245027-NAE");
        siteOffer.put("site", "AE");
        siteOffer.put("price", "48.00");
        snapshot.setSiteOffers(new ArrayList<>(List.of(siteOffer)));
        return snapshot;
    }
}
