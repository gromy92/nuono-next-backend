package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.HandoffFailureCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseDispatchPlanTransitionGuardTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void lateFailureAfterSuccessfulHandoffIsConflictWithoutFalseAudit() {
        HandoffFailureCommand command = failureCommand();
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001"))
                .thenReturn(plan("LOGISTICS_REQUESTED"));

        assertThatThrownBy(() -> service.markLogisticsHandoffFailure(access(), command))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("物流交接状态已变化，请刷新后重试。");

        verify(mapper, never()).markDispatchPlanHandoffFailed(anyString(), any(), anyLong());
        verifyNoOperationLog();
    }

    @Test
    void handoffFailureCasMissIsConflictWithoutFalseAudit() {
        HandoffFailureCommand command = failureCommand();
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001"))
                .thenReturn(plan("READY_FOR_LOGISTICS"));
        when(mapper.markDispatchPlanHandoffFailed("HANDOFF-340001", "货代拒绝", 307L))
                .thenReturn(0);

        assertThatThrownBy(() -> service.markLogisticsHandoffFailure(access(), command))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("物流交接状态已变化，请刷新后重试。");

        verifyNoOperationLog();
    }

    @Test
    void duplicateHandoffFailureIsIdempotentWithoutDuplicateAudit() {
        HandoffFailureCommand command = failureCommand();
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001"))
                .thenReturn(plan("HANDOFF_FAILED"));

        var view = service.markLogisticsHandoffFailure(access(), command);

        assertThat(view.status).isEqualTo("HANDOFF_FAILED");
        verify(mapper, never()).markDispatchPlanHandoffFailed(anyString(), any(), anyLong());
        verifyNoOperationLog();
    }

    @Test
    void readyForLogisticsCasMissIsConflictWithoutFalseAudit() {
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan("DRAFT"));
        when(mapper.updateDispatchPlanReady(340001L, 307L, 2, "WDH-340001-2", 307L))
                .thenReturn(0);

        assertThatThrownBy(() -> service.readyForLogistics(access(), "340001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("发运计划状态已变化，请刷新后重试。");

        verifyNoOperationLog();
    }

    @Test
    void duplicateReadyForLogisticsIsIdempotentWithoutNewGenerationOrAudit() {
        when(mapper.selectDispatchPlanByIdForUpdate(340001L))
                .thenReturn(plan("READY_FOR_LOGISTICS"));

        var view = service.readyForLogistics(access(), "340001");

        assertThat(view.status).isEqualTo("READY_FOR_LOGISTICS");
        assertThat(view.handoffGenerationNo).isEqualTo(1);
        verify(mapper, never()).updateDispatchPlanReady(
                anyLong(), anyLong(), any(), anyString(), anyLong()
        );
        verifyNoOperationLog();
    }

    @Test
    void reopenDraftCasMissIsConflictWithoutFalseAudit() {
        when(mapper.selectDispatchPlanByIdForUpdate(340001L))
                .thenReturn(plan("READY_FOR_LOGISTICS"));
        when(mapper.reopenDispatchPlanDraft(340001L, 307L, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.reopenDraft(access(), "340001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessage("发运计划状态已变化，请刷新后重试。");

        verifyNoOperationLog();
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

    private void verifyNoOperationLog() {
        verify(mapper, never()).insertOperationLog(
                anyLong(), anyLong(), anyString(), anyLong(), any(), any(), any()
        );
    }
}
