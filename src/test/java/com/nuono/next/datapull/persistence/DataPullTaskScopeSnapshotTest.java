package com.nuono.next.datapull.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.datapull.scope.DataPullScopeBindingEpoch;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class DataPullTaskScopeSnapshotTest {
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 8, 2, 0, 0);

    @Test
    void boundPayloadIsCopiedAndVerifiedWithoutASecondCatalogRead() {
        DataPullTask task = task();
        DataPullScopeBindingCandidate candidate = new DataPullScopeBindingCandidate(
                OperationCode.DP08A, "scope-a", "DP08_KEYWORD_V1", "{\"keyword\":\"paper\"}", SLOT
        );

        DataPullTaskScopeSnapshot.bind(
                task, DataPullScopeBindingEpoch.open(candidate, SLOT)
        );

        assertThat(DataPullTaskScopeSnapshot.requirePayload(
                task, OperationCode.DP08A, "DP08_KEYWORD_V1"
        )).isEqualTo("{\"keyword\":\"paper\"}");
    }

    @Test
    void missingOrChangedPayloadFailsClosed() {
        DataPullTask missing = task();
        assertThatThrownBy(() -> DataPullTaskScopeSnapshot.requirePayload(
                missing, OperationCode.DP08A, "DP08_KEYWORD_V1"
        )).isInstanceOf(IllegalStateException.class);

        DataPullScopeBindingCandidate candidate = new DataPullScopeBindingCandidate(
                OperationCode.DP08A, "scope-a", "DP08_KEYWORD_V1", "{}", SLOT
        );
        DataPullTaskScopeSnapshot.bind(
                missing, DataPullScopeBindingEpoch.open(candidate, SLOT)
        );
        missing.setScopePayload("{\"changed\":true}");

        assertThatThrownBy(() -> DataPullTaskScopeSnapshot.requirePayload(
                missing, OperationCode.DP08A, "DP08_KEYWORD_V1"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("digest drift");
    }

    @Test
    void nonDp08TaskCannotCarrySupplementalScopeBytes() {
        DataPullTask task = DataPullTask.queued(
                2L, OperationCode.DP04, "NOON", 307L, 10L,
                "307:STORE:SA", null, null, "STORE", "SA", "scope-a",
                SLOT, "DP04:slot", "FETCH_PAGE_1", SLOT
        );
        task.setScopeBindingId("a".repeat(64));
        task.setScopePayloadType("UNEXPECTED_V1");
        task.setScopePayloadSha256("b".repeat(64));
        task.setScopePayload("{}");
        task.setScopeBindingEffectiveFromUtc(SLOT);

        assertThatThrownBy(() -> DataPullTaskContract.requirePersistedScopeSnapshot(task))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unexpected scope snapshot");
    }

    private static DataPullTask task() {
        return DataPullTask.queued(
                1L, OperationCode.DP08A, "NOON_FRONTEND_SEARCH", 307L, 10L,
                "307:STORE:SA", null, null, "STORE", "SA", "scope-a",
                SLOT, "DP08A:slot", "FETCH_PAGE_1", SLOT
        );
    }
}
