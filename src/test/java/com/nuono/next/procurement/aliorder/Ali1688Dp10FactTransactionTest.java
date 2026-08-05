package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.checkpoint.DataPullScopeProgress;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplyCommand;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplySlice;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10BatchVerifier;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10BoundedStageStore;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactAdvance;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentResult;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentWriter;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ProgressConflictException;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ScopeIdentity;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10TaskFenceRow;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class Ali1688Dp10FactTransactionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 4, 0);

    @Test
    void verifyAdvancePerformsNoFactOrHighWaterWrite() {
        Fixture fixture = fixture("DP10_VERIFY");
        Ali1688Dp10ApplyCommand command = fixture.command();
        when(fixture.stageStore.verifyNext(fixture.task, command, NOW))
                .thenReturn(Ali1688Dp10BatchVerifier.Advance.PROGRESSED);

        assertThat(fixture.transaction.advance(command))
                .isEqualTo(Ali1688Dp10FactAdvance.VERIFYING);

        verify(fixture.factSegmentWriter, never()).applySegment(
                any(), any(), any(), anyInt());
        verify(fixture.progressStore, never()).commitCompletedWindow(any());
    }

    @Test
    void applyAdvancePersistsAtMostTwentyItemOrLogisticsRowsAndSavesCursor() {
        Fixture fixture = fixture("DP10_APPLY");
        Ali1688Dp10ApplyCommand command = fixture.command();
        Ali1688HistoricalOrderProvider.OrderSnapshot order = order();
        Ali1688Dp10ApplySlice slice = new Ali1688Dp10ApplySlice(
                1L, "CURRENT", 1, 0, 0, order);
        when(fixture.stageStore.nextApplySlice(fixture.task, command, NOW))
                .thenReturn(Optional.of(slice));
        when(fixture.factSegmentWriter.applySegment(
                fixture.task, fixture.authorization, slice, 20))
                .thenReturn(Ali1688Dp10FactSegmentResult.advanced(1));

        assertThat(fixture.transaction.advance(command))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);

        verify(fixture.stageStore).recordAppliedSegment(fixture.task, slice, 1, NOW);
        verify(fixture.progressStore, never()).commitCompletedWindow(any());
    }

    @Test
    void businessSkippedOrderDoesNotAdvanceItsCursorAndNextOrderStillApplies() {
        Fixture fixture = fixture("DP10_APPLY");
        Ali1688Dp10ApplyCommand command = fixture.command();
        Ali1688Dp10ApplySlice deleted = new Ali1688Dp10ApplySlice(
                1L, "CURRENT", 1, 0, 0, order());
        Ali1688Dp10ApplySlice following = new Ali1688Dp10ApplySlice(
                1L, "CURRENT", 1, 1, 0, order());
        when(fixture.stageStore.nextApplySlice(fixture.task, command, NOW))
                .thenReturn(Optional.of(deleted), Optional.of(following));
        when(fixture.factSegmentWriter.applySegment(
                fixture.task, fixture.authorization, deleted, 20))
                .thenReturn(Ali1688Dp10FactSegmentResult.businessSkipped(
                        "DP10_ORDER_HEADER_MANUALLY_DELETED"));
        when(fixture.factSegmentWriter.applySegment(
                fixture.task, fixture.authorization, following, 20))
                .thenReturn(Ali1688Dp10FactSegmentResult.advanced(1));

        assertThat(fixture.transaction.advance(command))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);
        assertThat(fixture.transaction.advance(command))
                .isEqualTo(Ali1688Dp10FactAdvance.APPLYING);

        verify(fixture.stageStore).recordBusinessSkip(
                fixture.task, deleted,
                "DP10_ORDER_HEADER_MANUALLY_DELETED", NOW);
        verify(fixture.stageStore, never()).recordAppliedSegment(
                fixture.task, deleted, 1, NOW);
        verify(fixture.stageStore).recordAppliedSegment(
                fixture.task, following, 1, NOW);
        verify(fixture.progressStore, never()).commitCompletedWindow(any());
    }

    @Test
    void childSetFinalizationFailureCannotAdvanceTheStageCursor() {
        Fixture fixture = fixture("DP10_APPLY");
        Ali1688Dp10ApplyCommand command = fixture.command();
        Ali1688Dp10ApplySlice slice = new Ali1688Dp10ApplySlice(
                1L, "CURRENT", 1, 0, 0, order());
        when(fixture.stageStore.nextApplySlice(fixture.task, command, NOW))
                .thenReturn(Optional.of(slice));
        when(fixture.factSegmentWriter.applySegment(
                fixture.task, fixture.authorization, slice, 20))
                .thenThrow(new IllegalStateException("DP10_CHILD_FINALIZE_FENCE_STALE"));

        assertThrows(IllegalStateException.class, () -> fixture.transaction.advance(command));

        verify(fixture.stageStore, never()).recordAppliedSegment(any(), any(), anyInt(), any());
        verify(fixture.progressStore, never()).commitCompletedWindow(any());
    }

    @Test
    void finalShortTransactionCommitsFixedEndOnlyAfterEveryPageIsApplied() throws Exception {
        Fixture fixture = fixture("DP10_APPLY");
        Ali1688Dp10ApplyCommand command = fixture.command();
        when(fixture.stageStore.nextApplySlice(fixture.task, command, NOW))
                .thenReturn(Optional.empty());
        when(fixture.stageStore.markNextPageApplied(fixture.task, command, NOW))
                .thenReturn(false);
        when(fixture.stageStore.allApplied(fixture.task, command, NOW)).thenReturn(true);
        when(fixture.progressStore.commitCompletedWindow(any())).thenReturn(Optional.of(
                DataPullScopeProgress.initial(OperationCode.DP10, fixture.task.getScopeKey(), NOW)));

        assertThat(fixture.transaction.advance(command))
                .isEqualTo(Ali1688Dp10FactAdvance.COMPLETE);

        verify(fixture.runtimeMapper, times(1)).lockTask(fixture.task.getId());
        Transactional transaction = Ali1688Dp10FactTransaction.class
                .getMethod("advance", Ali1688Dp10ApplyCommand.class)
                .getAnnotation(Transactional.class);
        assertThat(transaction).isNotNull();
        assertThat(transaction.timeout())
                .isEqualTo(DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS);
    }

    @Test
    void staleProgressOrTaskFenceCannotBePresentedAsComplete() {
        Fixture conflict = fixture("DP10_APPLY");
        Ali1688Dp10ApplyCommand conflictCommand = conflict.command();
        when(conflict.stageStore.nextApplySlice(conflict.task, conflictCommand, NOW))
                .thenReturn(Optional.empty());
        when(conflict.stageStore.markNextPageApplied(conflict.task, conflictCommand, NOW))
                .thenReturn(false);
        when(conflict.stageStore.allApplied(conflict.task, conflictCommand, NOW)).thenReturn(true);
        when(conflict.progressStore.commitCompletedWindow(any())).thenReturn(Optional.empty());
        assertThrows(Ali1688Dp10ProgressConflictException.class,
                () -> conflict.transaction.advance(conflictCommand));

        Fixture stale = fixture("DP10_APPLY");
        Ali1688Dp10ApplyCommand staleCommand = stale.command();
        stale.fence.setFenceEpoch(stale.fence.getFenceEpoch() + 1L);
        assertThrows(IllegalStateException.class,
                () -> stale.transaction.advance(staleCommand));
        verify(stale.stageStore, never()).nextApplySlice(any(), any(), any());
    }

    private Fixture fixture(String step) {
        Fixture fixture = new Fixture();
        fixture.authorization = authorization();
        fixture.task = task(fixture.authorization, step);
        fixture.fence = fence(fixture.task);
        fixture.runtimeMapper = mock(Ali1688Dp10RuntimeMapper.class);
        fixture.factSegmentWriter = mock(Ali1688Dp10FactSegmentWriter.class);
        fixture.progressStore = mock(DataPullScopeProgressStore.class);
        fixture.stageStore = mock(Ali1688Dp10BoundedStageStore.class);
        when(fixture.runtimeMapper.lockTask(fixture.task.getId())).thenReturn(fixture.fence);
        when(fixture.runtimeMapper.lockEffectiveOpenApiAuthorization(
                fixture.authorization.getOwnerUserId(),
                fixture.authorization.getProviderAccountId())).thenReturn(fixture.authorization);
        fixture.transaction = new Ali1688Dp10FactTransaction(
                fixture.runtimeMapper, fixture.factSegmentWriter,
                fixture.progressStore, fixture.stageStore);
        return fixture;
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91_001L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setStatus("authorized");
        return row;
    }

    private DataPullTask task(
            Ali1688HistoricalOrderAuthorizationRow authorization,
            String step
    ) {
        DataPullTask task = DataPullTask.queued(
                10_001L, OperationCode.DP10, Ali1688Dp10ScopeIdentity.PROVIDER_CHANNEL,
                authorization.getOwnerUserId(), null,
                Ali1688Dp10ScopeIdentity.accountKey(authorization), null, null, null, null,
                Ali1688Dp10ScopeIdentity.scopeKey(authorization), NOW.minusHours(9),
                "DP10:full-then-high-watermark-incremental:2026-08-02",
                step, NOW.minusHours(10));
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(NOW.plusMinutes(5));
        task.setFenceEpoch(4L);
        task.setVersion(7L);
        return task;
    }

    private Ali1688Dp10TaskFenceRow fence(DataPullTask task) {
        Ali1688Dp10TaskFenceRow row = new Ali1688Dp10TaskFenceRow();
        row.setId(task.getId());
        row.setOperationCode(task.getOperationCode());
        row.setOwnerUserId(task.getOwnerUserId());
        row.setAccountKey(task.getAccountKey());
        row.setScopeKey(task.getScopeKey());
        row.setState(task.getState());
        row.setLeaseOwner(task.getLeaseOwner());
        row.setLeaseUntil(task.getLeaseUntil());
        row.setFenceEpoch(task.getFenceEpoch());
        row.setVersion(task.getVersion());
        return row;
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot order() {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("A-1");
        order.setProviderModifiedAt(Instant.parse("2026-08-02T03:00:00Z"));
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setOfferId("offer-1");
        order.setItems(List.of(item));
        return order;
    }

    private static final class Fixture {
        private Ali1688Dp10RuntimeMapper runtimeMapper;
        private Ali1688Dp10FactSegmentWriter factSegmentWriter;
        private DataPullScopeProgressStore progressStore;
        private Ali1688Dp10BoundedStageStore stageStore;
        private Ali1688Dp10FactTransaction transaction;
        private Ali1688HistoricalOrderAuthorizationRow authorization;
        private DataPullTask task;
        private Ali1688Dp10TaskFenceRow fence;

        private Ali1688Dp10ApplyCommand command() {
            return new Ali1688Dp10ApplyCommand(
                    task, authorization, 1L, 1L, 1, 0L, 1, 0L,
                    Instant.parse("2026-08-02T04:00:00Z"), NOW);
        }
    }
}
