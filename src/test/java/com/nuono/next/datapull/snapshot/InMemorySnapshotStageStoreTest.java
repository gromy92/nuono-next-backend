package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class InMemorySnapshotStageStoreTest {

    @Test
    void provesContinuousPagesAndKeepsTheFirstStableIdentity() {
        InMemorySnapshotStageStore<Item> store = store();

        assertTrue(store.stagePage(
                41L,
                1L,
                page(1, 2, false, 2, item("A", "first"), item("B", "one"))
        ).isAccepted());
        assertTrue(store.stagePage(
                41L,
                2L,
                page(2, null, true, 2, item("A", "later"), item("C", "one"))
        ).isAccepted());

        SnapshotStageProof<Item> proof = store.proveComplete(41L, 3L);

        assertTrue(proof.isComplete());
        assertEquals(2, proof.getLastPage().orElseThrow());
        assertEquals(List.of("A:first", "B:one", "C:one"), values(proof.getItems()));
        assertEquals(1, proof.getSkippedIdentityCount());
    }

    @Test
    void identicalPageReplayIsIdempotentAcrossANewerFence() {
        InMemorySnapshotStageStore<Item> store = store();
        SnapshotPage<Item> firstPage = page(1, null, true, 1, item("A", "one"));

        SnapshotStageResult first = store.stagePage(42L, 1L, firstPage);
        SnapshotStageResult replay = store.stagePage(42L, 2L, firstPage);

        assertEquals(SnapshotStageResult.Status.STAGED, first.getStatus());
        assertEquals(SnapshotStageResult.Status.IDEMPOTENT_REPLAY, replay.getStatus());
        assertTrue(store.proveComplete(42L, 2L).isComplete());
    }

    @Test
    void changedContentOnARepeatedPagePoisonsTheSnapshot() {
        InMemorySnapshotStageStore<Item> store = store();
        store.stagePage(43L, 1L, page(1, null, true, 1, item("A", "before")));

        SnapshotStageResult drift = store.stagePage(
                43L,
                2L,
                page(1, null, true, 1, item("A", "after"))
        );

        assertFalse(drift.isAccepted());
        assertEquals("SNAPSHOT_PAGE_CONTENT_DRIFT", drift.getSanitizedCode());
        assertFalse(store.proveComplete(43L, 2L).isComplete());
        assertEquals(
                "SNAPSHOT_PAGE_CONTENT_DRIFT",
                store.proveComplete(43L, 2L).getSanitizedCode()
        );
    }

    @Test
    void missingMiddlePageCannotProduceACompleteProof() {
        InMemorySnapshotStageStore<Item> store = store();
        store.stagePage(44L, 1L, page(1, 2, false, 3, item("A", "one")));
        store.stagePage(44L, 2L, page(3, null, true, 3, item("C", "one")));

        SnapshotStageProof<Item> proof = store.proveComplete(44L, 3L);

        assertFalse(proof.isComplete());
        assertEquals("SNAPSHOT_MISSING_PAGE", proof.getSanitizedCode());
        assertEquals(List.of(), proof.getItems());
    }

    @Test
    void conflictingTotalPageMetadataCannotBeApplied() {
        InMemorySnapshotStageStore<Item> store = store();
        store.stagePage(45L, 1L, page(1, 2, false, 2, item("A", "one")));

        SnapshotStageResult conflict = store.stagePage(
                45L,
                2L,
                page(2, 3, false, 3, item("B", "one"))
        );

        assertFalse(conflict.isAccepted());
        assertEquals("SNAPSHOT_TOTAL_PAGES_DRIFT", conflict.getSanitizedCode());
        assertFalse(store.proveComplete(45L, 2L).isComplete());
    }

    @Test
    void unknownLastPageNeverProducesACompleteProof() {
        InMemorySnapshotStageStore<Item> store = store();

        SnapshotStageResult result = store.stagePage(
                46L,
                1L,
                page(1, null, null, null, item("A", "one"))
        );

        assertFalse(result.isAccepted());
        assertEquals("SNAPSHOT_LAST_PAGE_UNKNOWN", result.getSanitizedCode());
        assertFalse(store.proveComplete(46L, 1L).isComplete());
    }

    @Test
    void staleFenceCannotStageOrClearPagesOwnedByANewerWorker() {
        InMemorySnapshotStageStore<Item> store = store();
        store.stagePage(47L, 5L, page(1, null, true, 1, item("A", "one")));

        SnapshotStageResult stale = store.stagePage(
                47L,
                4L,
                page(1, null, true, 1, item("A", "one"))
        );

        assertFalse(stale.isAccepted());
        assertEquals("SNAPSHOT_STAGE_STALE_FENCE", stale.getSanitizedCode());
        assertFalse(store.clear(47L, 4L));
        assertTrue(store.proveComplete(47L, 5L).isComplete());
        assertTrue(store.clear(47L, 6L));
        assertEquals(
                "SNAPSHOT_NO_STAGED_PAGES",
                store.proveComplete(47L, 6L).getSanitizedCode()
        );
    }

    @Test
    void invalidItemDescriptorPoisonsOnlyItsTaskAcrossNewerFences() {
        InMemorySnapshotStageStore<Item> store = new InMemorySnapshotStageStore<>(
                new SnapshotItemDescriptor<Item>() {
                    @Override
                    public String stableIdentity(Item item) {
                        return "A".equals(item.identity) ? " " : item.identity;
                    }

                    @Override
                    public String stableContentFingerprint(Item item) {
                        return item.value;
                    }
                }
        );

        SnapshotStageResult invalid = store.stagePage(
                48L,
                1L,
                page(1, null, true, 1, item("A", "one"))
        );

        assertFalse(invalid.isAccepted());
        assertEquals("SNAPSHOT_ITEM_DESCRIPTOR_INVALID", invalid.getSanitizedCode());
        assertEquals(
                "SNAPSHOT_ITEM_DESCRIPTOR_INVALID",
                store.proveComplete(48L, 2L).getSanitizedCode()
        );
        assertTrue(store.stagePage(
                49L,
                1L,
                page(1, null, true, 1, item("B", "one"))
        ).isAccepted());
        assertTrue(store.proveComplete(49L, 1L).isComplete());
    }

    @Test
    void repeatedPageWithChangedPaginationMetadataPoisonsTheTask() {
        InMemorySnapshotStageStore<Item> store = store();
        store.stagePage(50L, 1L, page(1, 2, false, 2, item("A", "one")));

        SnapshotStageResult drift = store.stagePage(
                50L,
                2L,
                page(1, null, true, 1, item("A", "one"))
        );

        assertFalse(drift.isAccepted());
        assertEquals("SNAPSHOT_PAGE_METADATA_DRIFT", drift.getSanitizedCode());
        assertEquals(
                "SNAPSHOT_PAGE_METADATA_DRIFT",
                store.proveComplete(50L, 2L).getSanitizedCode()
        );
    }

    private InMemorySnapshotStageStore<Item> store() {
        return new InMemorySnapshotStageStore<>(new SnapshotItemDescriptor<Item>() {
            @Override
            public String stableIdentity(Item item) {
                return item.identity;
            }

            @Override
            public String stableContentFingerprint(Item item) {
                return item.identity + ":" + item.value;
            }
        });
    }

    @SafeVarargs
    private final SnapshotPage<Item> page(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            Item... items
    ) {
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "in-memory-stage",
                LocalDateTime.of(2026, 8, 2, 3, 0),
                0L
        );
        return new SnapshotPage<>(
                pageNo, nextPage, lastPage, totalPages, List.of(items),
                authority, items.length, 0
        );
    }

    private Item item(String identity, String value) {
        return new Item(identity, value);
    }

    private List<String> values(List<Item> items) {
        return items.stream()
                .map(item -> item.identity + ":" + item.value)
                .collect(java.util.stream.Collectors.toList());
    }

    private static final class Item {
        private final String identity;
        private final String value;

        private Item(String identity, String value) {
            this.identity = identity;
            this.value = value;
        }
    }
}
