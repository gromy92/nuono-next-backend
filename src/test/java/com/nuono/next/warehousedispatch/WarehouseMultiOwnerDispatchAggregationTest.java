package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionConfirmCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class WarehouseMultiOwnerDispatchAggregationTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void dispatchPlanUsesTheSingleOwnerDerivedFromSelectedInventory() {
        FulfillmentBalanceRecord secondOwner = secondOwnerBalance(900001L);
        when(mapper.selectBalanceScopes(List.of(900001L), storeOwners())).thenReturn(List.of(secondOwner));
        when(mapper.lockDispatchOwner(409L)).thenReturn(409L);
        when(mapper.selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners()))
                .thenReturn(List.of(secondOwner));
        when(mapper.reserveBalance(900001L, 409L, 5, 901L)).thenReturn(1);
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of());
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());

        var view = service.createDispatchPlan(multiOwnerAccess(), dispatchCommand(900001L));

        assertThat(view.ownerUserId).isEqualTo(409L);
        verify(mapper).insertDispatchPlan(
                org.mockito.ArgumentMatchers.argThat(row -> row.ownerUserId.equals(409L)),
                org.mockito.ArgumentMatchers.eq(901L)
        );
        verify(mapper).insertDispatchPlanLineSource(
                org.mockito.ArgumentMatchers.argThat(row -> row.ownerUserId.equals(409L)),
                org.mockito.ArgumentMatchers.eq(901L)
        );
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).selectBalanceScopes(List.of(900001L), storeOwners());
        lockOrder.verify(mapper).lockDispatchOwner(409L);
        lockOrder.verify(mapper).selectDispatchPlanByClientRequestId(409L, "second-owner-plan");
        lockOrder.verify(mapper).selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners());
        lockOrder.verify(mapper).reserveBalance(900001L, 409L, 5, 901L);
    }

    @Test
    void shippingBatchUsesTheSingleOwnerDerivedFromSelectedInventory() {
        FulfillmentBalanceRecord secondOwner = secondOwnerBalance(900001L);
        when(mapper.selectBalanceScopes(List.of(900001L), storeOwners())).thenReturn(List.of(secondOwner));
        when(mapper.lockDispatchOwner(409L)).thenReturn(409L);
        when(mapper.selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners()))
                .thenReturn(List.of(secondOwner));
        when(mapper.reserveBalance(900001L, 409L, 5, 901L)).thenReturn(1);
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(760001L);
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of());
        when(mapper.nextShippingSuggestionOptionId())
                .thenReturn(710001L, 710002L, 710003L, 710004L, 710005L);

        var view = service.createShippingBatch(multiOwnerAccess(), batchCommand(900001L));

        assertThat(view.ownerUserId).isEqualTo(409L);
        verify(mapper).insertShippingBatch(
                org.mockito.ArgumentMatchers.argThat(row -> row.ownerUserId.equals(409L)),
                org.mockito.ArgumentMatchers.eq(901L)
        );
        verify(mapper).insertShippingBatchSource(
                org.mockito.ArgumentMatchers.argThat(row -> row.ownerUserId.equals(409L)),
                org.mockito.ArgumentMatchers.eq(901L)
        );
        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).selectBalanceScopes(List.of(900001L), storeOwners());
        lockOrder.verify(mapper).lockDispatchOwner(409L);
        lockOrder.verify(mapper).selectShippingBatchByClientRequestId(409L, "second-owner-batch");
        lockOrder.verify(mapper).selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners());
        lockOrder.verify(mapper).reserveBalance(900001L, 409L, 5, 901L);
    }

    @Test
    void mobileConfirmationEvaluatesTheSameLockedInventorySnapshotItWouldPersist() {
        FulfillmentBalanceRecord scope = secondOwnerBalance(900001L);
        FulfillmentBalanceRecord locked = secondOwnerBalance(900001L);
        locked.siteCode = "AE";
        when(mapper.selectBalanceScopes(List.of(900001L), storeOwners())).thenReturn(List.of(scope));
        when(mapper.lockDispatchOwner(409L)).thenReturn(409L);
        when(mapper.selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners()))
                .thenReturn(List.of(locked));
        MobileShippingDecisionConfirmCommand command = new MobileShippingDecisionConfirmCommand();
        command.siteCode = "SA";
        command.transportMode = "AIR";
        command.sources = batchCommand(900001L).sources;

        assertThatThrownBy(() -> service.confirmMobileShippingDecision(multiOwnerAccess(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不属于当前站点");

        InOrder lockOrder = inOrder(mapper);
        lockOrder.verify(mapper).selectBalanceScopes(List.of(900001L), storeOwners());
        lockOrder.verify(mapper).lockDispatchOwner(409L);
        lockOrder.verify(mapper).selectAuthorizedBalancesForUpdate(List.of(900001L), storeOwners());
        verify(mapper, never()).selectAuthorizedBalances(any(), any());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), any(), anyLong());
        verify(mapper, never()).insertShippingBatch(any(ShippingBatchRecord.class), anyLong());
    }

    @Test
    void balancesFromDifferentOwnersCannotFormOneAggregate() {
        FulfillmentBalanceRecord firstOwner = balance("CONFIRMED", "SUBMITTED");
        firstOwner.sourceStoreCode = "STORE-A";
        FulfillmentBalanceRecord secondOwner = secondOwnerBalance(900002L);
        when(mapper.selectBalanceScopes(List.of(900001L, 900002L), storeOwners()))
                .thenReturn(List.of(firstOwner, secondOwner));
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "multi-owner-plan";
        command.sources = List.of(dispatchSource(900001L), dispatchSource(900002L));

        assertThatThrownBy(() -> service.createDispatchPlan(multiOwnerAccess(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不同业务归属人");

        verify(mapper, never()).lockDispatchOwner(anyLong());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), any(), anyLong());
        verify(mapper, never()).insertDispatchPlan(any(DispatchPlanRecord.class), anyLong());
    }

    @Test
    void historicalPlansAndBatchesAreReadAcrossEveryAuthorizedOwner() {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 409L;
        plan.planNo = "DP-340001";
        ShippingBatchRecord batch = new ShippingBatchRecord();
        batch.id = 700001L;
        batch.ownerUserId = 409L;
        batch.batchNo = "WB-700001";
        when(mapper.listDispatchPlans(storeOwners())).thenReturn(List.of(plan));
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of());
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());
        when(mapper.listShippingBatches(storeOwners())).thenReturn(List.of(batch));

        assertThat(service.listDispatchPlans(multiOwnerAccess()))
                .extracting(item -> item.ownerUserId)
                .containsExactly(409L);
        assertThat(service.listShippingBatches(multiOwnerAccess()))
                .extracting(item -> item.ownerUserId)
                .containsExactly(409L);
    }

    @Test
    void secondOwnerPlanCanContinueThroughTheExistingLifecycle() {
        DispatchPlanRecord plan = new DispatchPlanRecord();
        plan.id = 340001L;
        plan.ownerUserId = 409L;
        plan.planNo = "DP-340001";
        plan.status = "DRAFT";
        plan.handoffGenerationNo = 0;
        when(mapper.selectDispatchPlanByIdForUpdate(340001L)).thenReturn(plan);
        when(mapper.updateDispatchPlanReady(340001L, 409L, 1, "WDH-340001-1", 901L))
                .thenReturn(1);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of());
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());

        var view = service.readyForLogistics(multiOwnerAccess(), "340001");

        assertThat(view.ownerUserId).isEqualTo(409L);
        assertThat(view.status).isEqualTo("READY_FOR_LOGISTICS");
        verify(mapper).updateDispatchPlanReady(340001L, 409L, 1, "WDH-340001-1", 901L);
    }

    private CreateDispatchPlanCommand dispatchCommand(Long balanceId) {
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "second-owner-plan";
        command.sources = List.of(dispatchSource(balanceId));
        return command;
    }

    private DispatchPlanSourceCommand dispatchSource(Long balanceId) {
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = balanceId;
        source.quantity = 5;
        source.targetSiteCode = "SA";
        source.actualTransportMode = "AIR";
        return source;
    }

    private CreateShippingBatchCommand batchCommand(Long balanceId) {
        CreateShippingBatchCommand command = new CreateShippingBatchCommand();
        command.clientRequestId = "second-owner-batch";
        ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
        source.fulfillmentBalanceId = balanceId;
        source.quantity = 5;
        command.sources = List.of(source);
        return command;
    }

    private FulfillmentBalanceRecord secondOwnerBalance(Long id) {
        FulfillmentBalanceRecord result = balance("CONFIRMED", "SUBMITTED");
        result.id = id;
        result.ownerUserId = 409L;
        result.sourceStoreCode = "STORE-B";
        return result;
    }

    private BusinessAccessContext multiOwnerAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.copyOf(storeOwners().keySet()))
                .storeOwnerUserIds(storeOwners())
                .build();
    }

    private Map<String, Long> storeOwners() {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("STORE-A", 307L);
        result.put("STORE-B", 409L);
        return result;
    }
}
