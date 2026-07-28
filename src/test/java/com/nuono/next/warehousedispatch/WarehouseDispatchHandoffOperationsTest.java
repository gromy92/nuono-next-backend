package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseDispatchHandoffOperationsTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void markLogisticsHandoffSuccessRejectsMissingDispatchSources() {
        DispatchPlanRecord plan = handoffPlan("READY_FOR_LOGISTICS");
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001")).thenReturn(plan);
        when(mapper.markDispatchPlanHandoffSuccess("HANDOFF-340001", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.markLogisticsHandoffSuccess(access(), "HANDOFF-340001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("交接库存状态已变化");
    }

    @Test
    void markLogisticsHandoffSuccessRejectsUnmovedReservedQuantity() {
        DispatchPlanRecord plan = handoffPlan("READY_FOR_LOGISTICS");
        DispatchPlanLineSourceRecord source = handoffSource();
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001")).thenReturn(plan);
        when(mapper.markDispatchPlanHandoffSuccess("HANDOFF-340001", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.markLogisticsHandoffSuccess(access(), "HANDOFF-340001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("交接库存状态已变化");
    }

    @Test
    void markLogisticsHandoffSuccessMovesEveryReservedQuantityBeforeLoggingSuccess() {
        DispatchPlanRecord plan = handoffPlan("READY_FOR_LOGISTICS");
        DispatchPlanRecord updated = handoffPlan("LOGISTICS_REQUESTED");
        DispatchPlanLineSourceRecord source = handoffSource();
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("HANDOFF-340001")).thenReturn(plan);
        when(mapper.selectDispatchPlanByHandoffRequest("HANDOFF-340001")).thenReturn(updated);
        when(mapper.markDispatchPlanHandoffSuccess("HANDOFF-340001", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        var view = service.markLogisticsHandoffSuccess(access(), "HANDOFF-340001");

        assertThat(view.status).isEqualTo("LOGISTICS_REQUESTED");
        verify(mapper).moveReservedToLogisticsHandoff(900001L, 5, 307L);
        verify(mapper).insertOperationLog(
                eq(390001L),
                eq(340001L),
                eq("HANDOFF_SUCCESS"),
                eq(307L),
                eq("READY_FOR_LOGISTICS"),
                eq("LOGISTICS_REQUESTED"),
                anyString()
        );
    }

    private DispatchPlanRecord handoffPlan(String status) {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 307L;
        plan.planNo = "DP-340001";
        plan.status = status;
        plan.handoffRequestNo = "HANDOFF-340001";
        return plan;
    }

    private DispatchPlanLineSourceRecord handoffSource() {
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        return source;
    }
}
