package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.authority;
import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.authorityPage;
import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.item;
import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.page;
import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.store;
import static com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.values;

import java.util.List;
import com.nuono.next.datapull.snapshot.MyBatisSnapshotStageFixture.Item;
import org.junit.jupiter.api.Test;

class MyBatisSnapshotStageStoreTest {
    @Test
    void nativeAuthorityAndSourceAccountingSurviveRestartAndGenerationDriftPoisons() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(49L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        SnapshotCollectionAuthority first = authority("provider-generation-1", 4L);
        store.stagePage(49L, 1L, authorityPage(
                1, 2, false, 2, first, 3, 1, item("A", "one"), item("B", "one")
        ));
        mapper.task(49L, 2L, "RUNNING");
        SnapshotPage<Item> pageTwo = authorityPage(
                2, null, true, 2, first, 1, 0, item("C", "one")
        );
        assertTrue(store(mapper).stagePage(49L, 2L, pageTwo).isAccepted());
        SnapshotStageProof<Item> proof = store(mapper).proveComplete(49L, 2L);

        assertTrue(proof.isComplete());
        assertEquals(4L, proof.getSourceItemCount());
        assertEquals(1L, proof.getBusinessSkippedItemCount());
        assertEquals(first, proof.getAuthority().orElseThrow());

        mapper.task(49L, 3L, "RUNNING");
        SnapshotStageResult drift = store.stagePage(49L, 3L, authorityPage(
                2, null, true, 2, authority("provider-generation-2", 4L),
                1, 0, item("C", "one")
        ));
        assertEquals("SNAPSHOT_AUTHORITY_GENERATION_DRIFT", drift.getSanitizedCode());
    }

    @Test
    void resumesAcrossAdapterReconstructionAndKeepsTheFirstStableIdentity() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(41L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> firstProcess = store(mapper);

        SnapshotStageResult first = firstProcess.stagePage(
                41L,
                1L,
                authorityPage(
                        1, 2, false, 2, authority("mybatis-stage:2", 4L),
                        2, 0, item("A", "first"), item("B", "one")
                )
        );
        mapper.task(41L, 2L, "RUNNING");
        MyBatisSnapshotStageStore<Item> restartedProcess = store(mapper);
        SnapshotStageResult second = restartedProcess.stagePage(
                41L,
                2L,
                authorityPage(
                        2, null, true, 2, authority("mybatis-stage:2", 4L),
                        2, 0, item("A", "later"), item("C", "one")
                )
        );
        mapper.task(41L, 3L, "RUNNING");

        SnapshotStageProof<Item> proof = restartedProcess.proveComplete(41L, 3L);

        assertEquals(SnapshotStageResult.Status.STAGED, first.getStatus());
        assertEquals(2, first.getNextPage().orElseThrow());
        assertEquals(SnapshotStageResult.Status.STAGED, second.getStatus());
        assertTrue(proof.isComplete());
        assertEquals(2, proof.getLastPage().orElseThrow());
        assertEquals(List.of("A:first", "B:one", "C:one"), values(proof.getItems()));
        assertEquals(1, proof.getSkippedIdentityCount());
        assertEquals(3L, mapper.aggregate().getActiveFenceEpoch());
    }

    @Test
    void laterDuplicateNeverReplacesTheFirstValidIdentityAfterRestart() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(47L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        store.stagePage(47L, 1L, page(
                1, null, true, 1, item("A", "fallback"), item("A", "preferred")
        ));
        mapper.task(47L, 2L, "RUNNING");

        SnapshotStageProof<Item> proof = store(mapper).proveComplete(47L, 2L);

        assertTrue(proof.isComplete());
        assertEquals(List.of("A:fallback"), values(proof.getItems()));
        assertEquals(1, proof.getSkippedIdentityCount());
    }

    @Test
    void businessDefectiveRowDoesNotReserveIdentityFromALaterValidFact() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(48L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        store.stagePage(48L, 1L, page(
                1, null, true, 1, item("A", "invalid"), item("A", "valid")
        ));
        mapper.task(48L, 2L, "RUNNING");

        SnapshotStageProof<Item> proof = store(mapper).proveComplete(48L, 2L);

        assertTrue(proof.isComplete());
        assertEquals(List.of("A:valid"), values(proof.getItems()));
        assertEquals(1, proof.getSkippedIdentityCount());
    }

    @Test
    void identicalReplayIsIdempotentButChangedContentPermanentlyPoisonsTheTask() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(42L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        SnapshotPage<Item> original = page(1, null, true, 1, item("A", "before"));
        store.stagePage(42L, 1L, original);

        mapper.task(42L, 2L, "RUNNING");
        SnapshotStageResult replay = store.stagePage(42L, 2L, original);
        mapper.task(42L, 3L, "RUNNING");
        SnapshotStageResult drift = store.stagePage(
                42L,
                3L,
                page(1, null, true, 1, item("A", "after"))
        );

        assertEquals(SnapshotStageResult.Status.IDEMPOTENT_REPLAY, replay.getStatus());
        assertFalse(drift.isAccepted());
        assertEquals("SNAPSHOT_PAGE_CONTENT_DRIFT", drift.getSanitizedCode());
        assertEquals(
                "SNAPSHOT_PAGE_CONTENT_DRIFT",
                store.proveComplete(42L, 3L).getSanitizedCode()
        );
    }

    @Test
    void taskLedgerFencePreventsAStaleWorkerFromMutatingOrRecreatingClearedStaging() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(43L, 5L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        SnapshotPage<Item> page = page(1, null, true, 1, item("A", "one"));
        store.stagePage(43L, 5L, page);
        mapper.task(43L, 6L, "RUNNING");

        SnapshotStageResult stale = store.stagePage(43L, 5L, page);
        boolean staleClear = store.clear(43L, 5L);
        mapper.task(43L, 6L, "RUNNING");
        boolean currentClear = store.clear(43L, 6L);
        SnapshotStageResult staleAfterClear = store.stagePage(43L, 5L, page);

        assertEquals("SNAPSHOT_STAGE_STALE_FENCE", stale.getSanitizedCode());
        assertFalse(staleClear);
        assertTrue(currentClear);
        assertFalse(staleAfterClear.isAccepted());
        assertNull(mapper.aggregate());
    }

    @Test
    void proofRevalidatesDecodedPayloadAgainstStoredIdentityAndFingerprint() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(44L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        store.stagePage(44L, 1L, page(1, null, true, 1, item("A", "before")));
        mapper.replacePayload(1, 0, "v1|A|tampered");
        mapper.task(44L, 2L, "RUNNING");

        SnapshotStageProof<Item> proof = store.proveComplete(44L, 2L);

        assertFalse(proof.isComplete());
        assertEquals("SNAPSHOT_PAYLOAD_INTEGRITY_FAILED", proof.getSanitizedCode());
    }

    @Test
    void aLargeProviderPageNumberIsNotRejectedByAnApplicationBusinessCap() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(45L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<Item> store = store(mapper);
        SnapshotCollectionAuthority collection = authority("large-provider-pagination", 2L);

        SnapshotStageResult first = store.stagePage(
                45L,
                1L,
                authorityPage(
                        1, 2, false, 1001, collection, 1, 0, item("FIRST", "one")
                )
        );

        SnapshotStageResult result = store.stagePage(
                45L,
                1L,
                authorityPage(
                        1001, null, true, 1001, collection, 1, 0, item("LAST", "one")
                )
        );

        assertTrue(first.isAccepted());
        assertTrue(result.isAccepted());
        assertEquals(1001, result.getKnownLastPage().orElseThrow());
        assertEquals(
                "SNAPSHOT_MISSING_PAGE",
                store.proveComplete(45L, 1L).getSanitizedCode()
        );
    }

    @Test
    void expiredLeaseCannotStageOrProveAProviderPage() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(46L, 1L, "RUNNING", false);
        MyBatisSnapshotStageStore<Item> store = store(mapper);

        SnapshotStageResult staged = store.stagePage(
                46L,
                1L,
                page(1, null, true, 1, item("A", "late"))
        );
        SnapshotStageProof<Item> proof = store.proveComplete(46L, 1L);

        assertFalse(staged.isAccepted());
        assertEquals("SNAPSHOT_STAGE_STALE_FENCE", staged.getSanitizedCode());
        assertFalse(proof.isComplete());
        assertEquals("SNAPSHOT_STAGE_STALE_FENCE", proof.getSanitizedCode());
        assertNull(mapper.aggregate());
    }

}
