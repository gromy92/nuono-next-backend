package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotStageBoundedResetTest {

    @Test
    void largeStageResetMakesBoundedProgressAcrossTransactions() {
        FakeCompleteSnapshotStageMapper mapper = new FakeCompleteSnapshotStageMapper();
        mapper.task(50L, 1L, "RUNNING");
        MyBatisSnapshotStageStore<String> store = new MyBatisSnapshotStageStore<>(
                mapper,
                descriptor(),
                codec()
        );
        List<String> items = new ArrayList<>();
        for (int index = 0; index < 401; index++) {
            items.add("ITEM-" + index);
        }
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "bounded-reset", LocalDateTime.of(2026, 8, 2, 3, 0), items.size()
        );
        store.stagePage(50L, 1L, new SnapshotPage<>(
                1, null, true, 1, items, authority, items.size(), 0
        ));

        assertEquals(SnapshotStageClearResult.MORE_WORK, store.clearBounded(50L, 1L));
        assertEquals(SnapshotStageClearResult.MORE_WORK, store.clearBounded(50L, 1L));
        assertEquals(SnapshotStageClearResult.CLEARED, store.clearBounded(50L, 1L));
        assertNull(mapper.aggregate());
    }

    private SnapshotItemDescriptor<String> descriptor() {
        return new SnapshotItemDescriptor<>() {
            @Override
            public String stableIdentity(String item) {
                return item;
            }

            @Override
            public String stableContentFingerprint(String item) {
                return String.format("%064x", Integer.toUnsignedLong(item.hashCode()));
            }
        };
    }

    private SnapshotPayloadCodec<String> codec() {
        return new SnapshotPayloadCodec<>() {
            @Override
            public String encode(String item) {
                return item;
            }

            @Override
            public String decode(String payload) {
                return payload;
            }
        };
    }
}
