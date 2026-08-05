package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotStagePageBoundTest {

    @Test
    void acceptsLargeItemCountsAndLargePageNumbersWithoutABusinessCap() {
        List<String> items = new ArrayList<>();
        for (int index = 0; index < 1_001; index += 1) {
            items.add("item-" + index);
        }
        SnapshotPage<String> page = new SnapshotPage<>(
                10_001, null, true, 10_001, items
        );

        SnapshotStagePageCandidate<String> candidate = SnapshotStagePageCandidate.from(
                page, descriptor(), codec()
        );

        assertThat(candidate.getPageNo()).isEqualTo(10_001);
        assertThat(candidate.getItems()).hasSize(1_001);
    }

    @Test
    void rejectsOnlyWhenTheAtomicStagePayloadExceedsItsTechnicalByteBudget() {
        String first = "a".repeat(8_355_841);
        String second = "b".repeat(8_355_841);

        assertThatThrownBy(() -> SnapshotStagePageCandidate.from(
                new SnapshotPage<>(1, null, true, 1, List.of(first, second)),
                descriptor(), codec()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page payload is too large");
    }

    private SnapshotItemDescriptor<String> descriptor() {
        return new SnapshotItemDescriptor<>() {
            @Override
            public String stableIdentity(String item) {
                return "item:" + Integer.toUnsignedString(item.hashCode());
            }

            @Override
            public String stableContentFingerprint(String item) {
                return String.format("%064x", item.hashCode() & 0xffffffffL);
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
