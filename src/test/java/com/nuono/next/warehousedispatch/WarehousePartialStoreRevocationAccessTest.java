package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePartialStoreRevocationAccessTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void revokedPlanCannotTransitionOrCompleteHandoff() {
        DispatchPlanRecord plan = dispatchPlan("READY_FOR_LOGISTICS");
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan);
        when(mapper.selectDispatchPlanByHandoffRequestForUpdate("WDH-340001-1")).thenReturn(plan);
        when(mapper.isDispatchPlanSourceScopeAuthorized(340001L, authorizedStoreOwners()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.readyForLogistics(partiallyRevokedAccess(), "340001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");
        assertThatThrownBy(() -> service.markLogisticsHandoffSuccess(
                partiallyRevokedAccess(),
                "WDH-340001-1"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");

        verify(mapper, never()).updateDispatchPlanReady(
                anyLong(), anyLong(), anyInt(), anyString(), anyLong()
        );
        verify(mapper, never()).markDispatchPlanHandoffSuccess(anyString(), anyLong());
        verify(mapper, never()).moveReservedToLogisticsHandoff(
                anyLong(), anyInt(), anyLong()
        );
        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), anyString(), anyLong(), anyString(), anyString(), anyString()
        );
    }

    @Test
    void revokedBatchCannotBeReadOrMutated() {
        ShippingBatchRecord batch = shippingBatch();
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.isShippingBatchSourceScopeAuthorized(700001L, authorizedStoreOwners()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getShippingBatch(partiallyRevokedAccess(), "700001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");
        assertThatThrownBy(() -> service.createOutboundOrders(partiallyRevokedAccess(), "700001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");

        verify(mapper, never()).listShippingBatchSources(700001L);
        verify(mapper, never()).insertOutboundOrder(any(), anyLong());
        verify(mapper, never()).updateShippingBatchOutboundCreated(
                anyLong(), anyLong(), anyLong(), anyLong()
        );
    }

    @Test
    void revokedOutboundCannotCreatePackingList() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.isOutboundOrderSourceScopeAuthorized(800001L, authorizedStoreOwners()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.createPackingList(
                partiallyRevokedAccess(),
                "800001",
                new CreatePackingListCommand()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");

        verify(mapper, never()).nextPackingListId();
        verify(mapper, never()).insertPackingList(any(), anyLong());
        verify(mapper, never()).markOutboundOrderPacking(anyLong(), anyLong(), anyLong());
    }

    @Test
    void revokedPackingListCannotBeConfirmedOrShipped() {
        PackingListRecord packingList = packingList();
        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.isPackingListSourceScopeAuthorized(830001L, authorizedStoreOwners()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.confirmPackingList(partiallyRevokedAccess(), "830001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");
        assertThatThrownBy(() -> service.shipPackingList(partiallyRevokedAccess(), "830001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");

        verify(mapper, never()).confirmPackingList(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).markOutboundOrderPacked(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).shipPackingList(anyLong(), anyLong(), anyLong());
    }

    @Test
    void emptySourceAggregatesFailClosedBeforeReadingDetails() {
        DispatchPlanRecord plan = dispatchPlan("DRAFT");
        ShippingBatchRecord batch = shippingBatch();
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.isDispatchPlanSourceScopeAuthorized(340001L, authorizedStoreOwners()))
                .thenReturn(false);
        when(mapper.isShippingBatchSourceScopeAuthorized(700001L, authorizedStoreOwners()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getLogisticsHandoff(partiallyRevokedAccess(), "340001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");
        assertThatThrownBy(() -> service.getShippingBatch(partiallyRevokedAccess(), "700001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");

        verify(mapper, never()).listDispatchPlanLines(340001L);
        verify(mapper, never()).listDispatchLineSources(340001L);
        verify(mapper, never()).listShippingBatchSources(700001L);
    }

    @Test
    void dispatchReplayChecksPersistedScopeBeforeRevealingFingerprintConflicts() {
        FulfillmentBalanceRecord currentBalance = balance("CONFIRMED", "SUBMITTED");
        DispatchPlanRecord existing = dispatchPlan("DRAFT");
        existing.clientRequestId = "dispatch-request-revoked";
        existing.requestFingerprint =
                "8af1aeebcef57ea41741a3b0eea3b7aa0f876e346cd9f4800552fd4b7b570816";
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(currentBalance));
        when(mapper.lockDispatchOwner(307L)).thenReturn(307L);
        when(mapper.selectDispatchPlanByClientRequestId(307L, "dispatch-request-revoked"))
                .thenReturn(existing);
        when(mapper.isDispatchPlanSourceScopeAuthorized(
                340001L,
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(false);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), dispatchCommand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能操作");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), any());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
        verify(mapper, never()).insertDispatchPlan(any(DispatchPlanRecord.class), anyLong());
    }

    @Test
    void historicalListsPassExactStoreOwnerPairs() {
        DispatchPlanRecord plan = dispatchPlan("DRAFT");
        ShippingBatchRecord batch = shippingBatch();
        when(mapper.listDispatchPlans(authorizedStoreOwners())).thenReturn(List.of(plan));
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of());
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());
        when(mapper.listShippingBatches(authorizedStoreOwners())).thenReturn(List.of(batch));

        assertThat(service.listDispatchPlans(partiallyRevokedAccess()))
                .extracting(item -> item.id)
                .containsExactly("340001");
        assertThat(service.listShippingBatches(partiallyRevokedAccess()))
                .extracting(item -> item.id)
                .containsExactly("700001");

        verify(mapper).listDispatchPlans(authorizedStoreOwners());
        verify(mapper).listShippingBatches(authorizedStoreOwners());
    }

    private DispatchPlanRecord dispatchPlan(String status) {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 307L;
        plan.planNo = "DP-340001";
        plan.status = status;
        plan.handoffGenerationNo = 1;
        plan.handoffRequestNo = "WDH-340001-1";
        return plan;
    }

    private BusinessAccessContext partiallyRevokedAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.copyOf(authorizedStoreOwners().keySet()))
                .storeOwnerUserIds(authorizedStoreOwners())
                .build();
    }

    private Map<String, Long> authorizedStoreOwners() {
        return Map.of("STORE-A", 307L);
    }

    private CreateDispatchPlanCommand dispatchCommand() {
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-request-revoked";
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 6;
        source.targetSiteCode = "SA";
        source.actualTransportMode = "AIR";
        command.sources = List.of(source);
        return command;
    }
}
