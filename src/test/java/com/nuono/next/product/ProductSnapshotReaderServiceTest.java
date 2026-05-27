package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductSnapshotReaderServiceTest {

    @Test
    void readNoonSnapshotRoutesThroughDedicatedReaderBoundaryWithReasonAndReuseSeed() {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(10002L);
        command.setStoreCode("STR245027-NAE");
        command.setSkuParent("ZTEST001");
        ProductMasterSnapshotView reuseSeed = snapshot("reuse seed");
        ProductMasterSnapshotView expected = snapshot("Noon title");
        RecordingSnapshotFetcher fetcher = new RecordingSnapshotFetcher(expected);
        ProductSnapshotReaderService reader = new ProductSnapshotReaderService(fetcher);

        ProductMasterSnapshotView actual = reader.readNoonSnapshot(
                command,
                "detail-baseline-backfill.open-missing-baseline",
                reuseSeed
        );

        assertSame(expected, actual);
        assertSame(command, fetcher.command);
        assertEquals("detail-baseline-backfill.open-missing-baseline", fetcher.reason);
        assertSame(reuseSeed, fetcher.siteOfferReuseSeed);
    }

    @Test
    void explicitNoonSyncKeepsLocalDraftWhenRequested() {
        ProductSnapshotReaderService reader = new ProductSnapshotReaderService((command, reason, seed) -> snapshot("unused"));
        ProductMasterSnapshotView oldBaseline = snapshot("Old official title");
        ProductMasterSnapshotView localDraft = snapshot("Local draft title");
        localDraft.getSiteOffers().get(0).put("finalPrice", "39.50");
        ProductMasterSnapshotView liveSnapshot = snapshot("Latest official title");
        liveSnapshot.getSiteOffers().get(0).put("finalPrice", "41.00");

        ProductBaselineRefreshDecision decision = reader.refreshBaseline(
                liveSnapshot,
                oldBaseline,
                localDraft,
                "keep_draft"
        );

        assertEquals("Latest official title", decision.getBaselineSnapshot().getContent().get("titleEn"));
        assertEquals("Local draft title", decision.getDraftSnapshot().getContent().get("titleEn"));
        assertEquals("41.00", decision.getDraftSnapshot().getSiteOffers().get(0).get("finalPrice"));
        assertEquals("draft", decision.getSyncStatus());
        assertTrue(decision.getNote().contains("保留本地草稿"));
    }

    @Test
    void explicitNoonSyncMarksKeptDraftSyncedWhenBusinessContentMatchesLatestBaseline() {
        ProductSnapshotReaderService reader = new ProductSnapshotReaderService((command, reason, seed) -> snapshot("unused"));
        ProductMasterSnapshotView oldBaseline = snapshot("Old official title");
        oldBaseline.getStoreContext().put("fetchedAt", "2026-05-19 10:00:00");
        ProductMasterSnapshotView localDraft = snapshot("Latest official title");
        localDraft.getStoreContext().put("fetchedAt", "2026-05-19 10:00:00");
        ProductMasterSnapshotView liveSnapshot = snapshot("Latest official title");
        liveSnapshot.getStoreContext().put("fetchedAt", "2026-05-20 10:00:00");

        ProductBaselineRefreshDecision decision = reader.refreshBaseline(
                liveSnapshot,
                oldBaseline,
                localDraft,
                "keep_draft"
        );

        assertEquals("synced", decision.getSyncStatus());
        assertTrue(decision.getNote().contains("当前草稿与最新基线一致"));
    }

    @Test
    void explicitNoonSyncOverwritesDraftByDefault() {
        ProductSnapshotReaderService reader = new ProductSnapshotReaderService((command, reason, seed) -> snapshot("unused"));
        ProductMasterSnapshotView oldBaseline = snapshot("Old official title");
        ProductMasterSnapshotView localDraft = snapshot("Local draft title");
        ProductMasterSnapshotView liveSnapshot = snapshot("Latest official title");

        ProductBaselineRefreshDecision decision = reader.refreshBaseline(
                liveSnapshot,
                oldBaseline,
                localDraft,
                "overwrite"
        );

        assertEquals("Latest official title", decision.getBaselineSnapshot().getContent().get("titleEn"));
        assertEquals("Latest official title", decision.getDraftSnapshot().getContent().get("titleEn"));
        assertEquals("synced", decision.getSyncStatus());
        assertTrue(decision.getNote().contains("覆盖本地草稿"));
    }

    @Test
    void classifiesAbsentEmptyValueAndReadErrorWithoutConflatingThem() {
        ProductSnapshotReaderService reader = new ProductSnapshotReaderService((command, reason, seed) -> snapshot("unused"));
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("salePrice", "");
        incoming.put("priceMin", "9.13");
        Map<String, String> readErrors = Map.of("price", "pricing API returned 500");

        Map<String, ProductFieldRead<?>> reads = reader.classifySnapshotFieldReads(
                incoming,
                Set.of("price", "salePrice", "priceMin", "priceMax"),
                readErrors
        );

        assertEquals(ProductFieldReadState.READ_ERROR, reads.get("price").getState());
        assertEquals("pricing API returned 500", reads.get("price").getErrorMessage());
        assertEquals(ProductFieldReadState.READ_EMPTY, reads.get("salePrice").getState());
        assertEquals(ProductFieldReadState.READ_VALUE, reads.get("priceMin").getState());
        assertEquals(ProductFieldReadState.ABSENT, reads.get("priceMax").getState());
    }

    private ProductMasterSnapshotView snapshot(String title) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.setReady(true);
        snapshot.getStoreContext().put("storeCode", "STR245027-NAE");
        snapshot.getIdentity().put("skuParent", "ZTEST001");
        snapshot.getContent().put("titleEn", title);
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", "STR245027-NAE");
        offer.put("price", "48.00");
        offer.put("salePrice", "39.90");
        snapshot.setSiteOffers(java.util.List.of(offer));
        return snapshot;
    }

    private static final class RecordingSnapshotFetcher implements ProductNoonSnapshotFetcher {
        private final ProductMasterSnapshotView snapshot;
        private ProductMasterFetchCommand command;
        private String reason;
        private ProductMasterSnapshotView siteOfferReuseSeed;

        private RecordingSnapshotFetcher(ProductMasterSnapshotView snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public ProductMasterSnapshotView fetch(
                ProductMasterFetchCommand command,
                String reason,
                ProductMasterSnapshotView siteOfferReuseSeed
        ) {
            this.command = command;
            this.reason = reason;
            this.siteOfferReuseSeed = siteOfferReuseSeed;
            return snapshot;
        }
    }
}
