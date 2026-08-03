package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.HandoffFailureCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseDispatchPlanTransitionSuccessTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void readyFromFailedStartsNewGenerationBeforeAudit() {
        DispatchPlanRecord failed = plan("HANDOFF_FAILED");
        DispatchPlanRecord ready = plan("READY_FOR_LOGISTICS");
        ready.handoffGenerationNo = 2;
        ready.handoffRequestNo = "WDH-340001-2";
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(failed);
        when(mapper.updateDispatchPlanReady(340001L, 307L, 2, "WDH-340001-2", 307L))
                .thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(ready);

        var view = service.readyForLogistics(access(), "340001");

        assertThat(view.status).isEqualTo("READY_FOR_LOGISTICS");
        assertThat(view.handoffGenerationNo).isEqualTo(2);
        InOrder order = inOrder(mapper);
        order.verify(mapper).updateDispatchPlanReady(340001L, 307L, 2, "WDH-340001-2", 307L);
        order.verify(mapper).insertOperationLog(
                eq(390001L), eq(340001L), eq("READY_FOR_LOGISTICS"), eq(307L),
                eq("HANDOFF_FAILED"), eq("READY_FOR_LOGISTICS"), anyString()
        );
    }

    @Test
    void reopenReadyPlanChangesStateBeforeAudit() {
        DispatchPlanRecord ready = plan("READY_FOR_LOGISTICS");
        DispatchPlanRecord draft = plan("DRAFT");
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(ready);
        when(mapper.reopenDispatchPlanDraft(340001L, 307L, 307L)).thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(draft);

        var view = service.reopenDraft(access(), "340001");

        assertThat(view.status).isEqualTo("DRAFT");
        InOrder order = inOrder(mapper);
        order.verify(mapper).reopenDispatchPlanDraft(340001L, 307L, 307L);
        order.verify(mapper).insertOperationLog(
                eq(390001L), eq(340001L), eq("REOPEN_DRAFT"), eq(307L),
                eq("READY_FOR_LOGISTICS"), eq("DRAFT"), anyString()
        );
    }

    @Test
    void reopenDraftReplayIsIdempotentWithoutDuplicateAudit() {
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan("DRAFT"));

        var view = service.reopenDraft(access(), "340001");

        assertThat(view.status).isEqualTo("DRAFT");
        verify(mapper, never()).reopenDispatchPlanDraft(340001L, 307L, 307L);
        verify(mapper, never()).nextOperationLogId();
    }

    @Test
    void handoffFailureChangesStateBeforeAudit() {
        HandoffFailureCommand command = failureCommand();
        DispatchPlanRecord failed = plan("HANDOFF_FAILED");
        failed.handoffErrorMessage = "货代拒绝";
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001"))
                .thenReturn(plan("READY_FOR_LOGISTICS"));
        when(mapper.markDispatchPlanHandoffFailed("HANDOFF-340001", "货代拒绝", 307L))
                .thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);
        when(mapper.selectDispatchPlanByHandoffRequest("HANDOFF-340001")).thenReturn(failed);

        var view = service.markLogisticsHandoffFailure(access(), command);

        assertThat(view.status).isEqualTo("HANDOFF_FAILED");
        InOrder order = inOrder(mapper);
        order.verify(mapper).markDispatchPlanHandoffFailed("HANDOFF-340001", "货代拒绝", 307L);
        order.verify(mapper).insertOperationLog(
                eq(390001L), eq(340001L), eq("HANDOFF_FAILED"), eq(307L),
                eq("READY_FOR_LOGISTICS"), eq("HANDOFF_FAILED"), anyString()
        );
    }

    private HandoffFailureCommand failureCommand() {
        HandoffFailureCommand command = new HandoffFailureCommand();
        command.handoffRequestNo = "HANDOFF-340001";
        command.errorMessage = "货代拒绝";
        return command;
    }

    private DispatchPlanRecord plan(String status) {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 307L;
        plan.planNo = "DP-340001";
        plan.status = status;
        plan.handoffGenerationNo = 1;
        plan.handoffRequestNo = "HANDOFF-340001";
        return plan;
    }
}
