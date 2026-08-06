package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.ScheduleTaskBindingRow;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class Dp08ScheduleTaskPayloadBinderTest {
    private static final LocalDateTime SLOT = LocalDateTime.of(2026, 8, 4, 2, 0);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final Dp08ScheduleTaskPayloadBinder binder =
            new Dp08ScheduleTaskPayloadBinder(objectMapper);

    @Test
    void bindsBothDp08TemporalPayloadShapes() {
        Dp08KeywordScope keyword = new Dp08KeywordScope(
                307L,
                10L,
                11L,
                12L,
                "STORE",
                "SA",
                "paper",
                "en-SA",
                "scope-a",
                List.of(new Dp08TrackedProduct(
                        Dp08TrackedProduct.SubjectType.SELF,
                        null,
                        "N123"
                ))
        );
        assertBound(
                OperationCode.DP08A,
                keyword.getStableScopeKey(),
                Dp08ScopeSnapshotCodec.KEYWORD_V1,
                new Dp08ScopeSnapshotCodec(objectMapper).encode(keyword)
        );

        Dp08ListTarget target = new Dp08ListTarget(
                307L,
                10L,
                "STORE",
                "SA",
                "N123",
                "scope-b",
                LocalDate.of(2026, 8, 4),
                true,
                List.of(new Dp08ListTarget.Reference(11L, null))
        );
        assertBound(
                OperationCode.DP08B,
                target.getStableScopeKey(),
                Dp08ScopeSnapshotCodec.LIST_TARGET_V1,
                new Dp08ScopeSnapshotCodec(objectMapper).encode(target)
        );
    }

    @Test
    void unknownOperationFailsBeforeTaskMutation() {
        assertThatThrownBy(() -> binder.bind(
                OperationCode.DP05,
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DP08 payload binder received DP05");
    }

    private void assertBound(
            OperationCode operation,
            String scopeKey,
            String payloadType,
            String payload
    ) {
        DataPullTask task = DataPullTask.queued(
                operation == OperationCode.DP08A ? 81L : 82L,
                operation,
                "NOON_FRONTEND_SEARCH",
                307L,
                10L,
                "307:STORE:SA",
                null,
                null,
                "STORE",
                "SA",
                scopeKey,
                SLOT,
                operation.name() + ":window",
                "FETCH",
                SLOT
        );
        DataPullScopeBindingCandidate candidate = new DataPullScopeBindingCandidate(
                operation,
                scopeKey,
                payloadType,
                payload,
                SLOT.minusHours(1)
        );
        ScheduleTaskBindingRow row = binding(scopeKey, candidate);

        binder.bind(operation, List.of(task), List.of(row));

        assertThat(task.getScopeBindingId()).isEqualTo(candidate.getBindingId());
        assertThat(task.getScopePayloadType()).isEqualTo(payloadType);
        assertThat(task.getScopePayload()).isEqualTo(payload);
    }

    private ScheduleTaskBindingRow binding(
            String scopeKey,
            DataPullScopeBindingCandidate candidate
    ) {
        ScheduleTaskBindingRow row = new ScheduleTaskBindingRow();
        row.setScopeKey(scopeKey);
        row.setScheduleSlot(SLOT);
        row.setBindingId(candidate.getBindingId());
        row.setPayloadType(candidate.getPayloadType());
        row.setPayloadSha256(candidate.getPayloadSha256());
        row.setPayload(candidate.getPayload());
        row.setEffectiveFromUtc(candidate.getEffectiveFromUtc());
        return row;
    }
}
