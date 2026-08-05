package com.nuono.next.datapull.snapshot;

import com.nuono.next.infrastructure.mapper.SnapshotTwoPassMapper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** Shared production-Adapter fixture kept separate from behavior-focused tests. */
final class MyBatisSnapshotStageFixture {
    private MyBatisSnapshotStageFixture() {
    }

    static MyBatisSnapshotStageStore<Item> store(FakeCompleteSnapshotStageMapper mapper) {
        return store(mapper, null);
    }

    static MyBatisSnapshotStageStore<Item> store(
            FakeCompleteSnapshotStageMapper mapper,
            SnapshotTwoPassMapper twoPassMapper
    ) {
        return new MyBatisSnapshotStageStore<>(mapper, twoPassMapper,
                new SnapshotItemDescriptor<Item>() {
            @Override
            public String stableIdentity(Item item) {
                return item.identity;
            }

            @Override
            public String stableContentFingerprint(Item item) {
                return String.format(
                        "%064x",
                        Integer.toUnsignedLong((item.identity + ":" + item.value).hashCode())
                );
            }

            @Override
            public boolean isValidatedIdentityCandidate(Item item) {
                return !"invalid".equals(item.value);
            }
        }, new SnapshotPayloadCodec<Item>() {
            @Override
            public String encode(Item item) {
                return "v1|" + item.identity + "|" + item.value;
            }

            @Override
            public Item decode(String payload) {
                String[] parts = payload.split("\\|", -1);
                if (parts.length != 3 || !"v1".equals(parts[0])) {
                    throw new IllegalArgumentException("unknown payload schema");
                }
                return item(parts[1], parts[2]);
            }
        });
    }

    @SafeVarargs
    static SnapshotPage<Item> page(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            Item... items
    ) {
        return authorityPage(
                pageNo, nextPage, lastPage, totalPages,
                authority("mybatis-stage:" + totalPages, items.length), items.length, 0, items
        );
    }

    static SnapshotPage<Item> authorityPage(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            SnapshotCollectionAuthority authority,
            int sourceCount,
            int skippedCount,
            Item... items
    ) {
        return new SnapshotPage<>(
                pageNo, nextPage, lastPage, totalPages, Arrays.asList(items),
                authority, sourceCount, skippedCount
        );
    }

    static SnapshotCollectionAuthority authority(String token, long count) {
        return SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                token, LocalDateTime.of(2026, 8, 2, 3, 0), count
        );
    }

    static Item item(String identity, String value) {
        return new Item(identity, value);
    }

    static List<String> values(List<Item> items) {
        return items.stream()
                .map(item -> item.identity + ":" + item.value)
                .collect(Collectors.toList());
    }

    static final class Item {
        private final String identity;
        private final String value;

        private Item(String identity, String value) {
            this.identity = identity;
            this.value = value;
        }
    }
}
