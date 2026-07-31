package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationLineCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationLineInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseRequestIdempotencyOperationsTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void createDispatchPlanReplaysSameRequestAndRejectsChangedPayloadWithoutReservingAgain() {
        AtomicReference<DispatchPlanRecord> persisted = new AtomicReference<>();
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        when(mapper.lockDispatchOwner(307L)).thenReturn(307L);
        when(mapper.selectDispatchPlanByClientRequestId(307L, "dispatch-request-1"))
                .thenAnswer(invocation -> persisted.get());
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance));
        when(mapper.selectAuthorizedBalancesForUpdate(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance));
        when(mapper.reserveBalance(900001L, 307L, 5, 307L)).thenReturn(1);
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.insertDispatchPlan(any(DispatchPlanRecord.class), anyLong()))
                .thenAnswer(invocation -> {
                    persisted.set(invocation.getArgument(0));
                    return 1;
                });
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of());
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of());

        CreateDispatchPlanCommand command = dispatchCommand("dispatch-request-1", 5);
        var first = service.createDispatchPlan(access(), command);
        var replay = service.createDispatchPlan(access(), dispatchCommand("dispatch-request-1", 5));

        assertThat(replay.id).isEqualTo(first.id);
        assertThat(replay.planNo).isEqualTo(first.planNo);
        assertThat(replay.totalQuantity).isEqualTo(first.totalQuantity);
        assertThat(replay.clientRequestId).isEqualTo("dispatch-request-1");

        CreateDispatchPlanCommand changedRemark = dispatchCommand("dispatch-request-1", 5);
        changedRemark.remark = "修改后的备注";
        assertThatThrownBy(() -> service.createDispatchPlan(access(), changedRemark))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");

        assertThatThrownBy(() -> service.createDispatchPlan(
                access(),
                dispatchCommand("dispatch-request-1", 6)
        ))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");

        verify(mapper, times(1)).reserveBalance(900001L, 307L, 5, 307L);
        verify(mapper, times(1)).insertDispatchPlan(any(DispatchPlanRecord.class), anyLong());
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        );
        order.verify(mapper).lockDispatchOwner(307L);
        order.verify(mapper).selectDispatchPlanByClientRequestId(307L, "dispatch-request-1");
        order.verify(mapper).selectAuthorizedBalancesForUpdate(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        );
    }

    @Test
    void createConfirmationReplaysSameRequestAndRejectsChangedPayloadWithoutApplyingInventoryAgain() {
        AtomicReference<FulfillmentConfirmationInsertRecord> persisted = new AtomicReference<>();
        List<FulfillmentConfirmationLineInsertRecord> persistedLines = new ArrayList<>();
        when(mapper.selectOrderAccess(eq(200001L), anyMap())).thenReturn(purchaseOrder());
        when(mapper.lockDispatchOwner(409L)).thenReturn(409L);
        when(mapper.selectConfirmationByClientRequestId(409L, "receipt-request-1"))
                .thenAnswer(invocation -> persisted.get());
        when(mapper.selectPurchaseOrderItem(210001L, 200001L, 409L)).thenReturn(purchaseOrderItem());
        when(mapper.listItemSitesForBalance(210001L, 200001L, 409L))
                .thenReturn(List.of(purchaseOrderItemSite()));
        when(mapper.listBalancesForItemForUpdate(210001L, 200001L, 409L))
                .thenReturn(List.of(fulfillmentBalance()));
        when(mapper.nextConfirmationId()).thenReturn(370001L);
        when(mapper.nextConfirmationLineId()).thenReturn(380001L);
        when(mapper.insertConfirmation(any(FulfillmentConfirmationInsertRecord.class)))
                .thenAnswer(invocation -> {
                    persisted.set(invocation.getArgument(0));
                    return 1;
                });
        when(mapper.insertConfirmationLine(any(FulfillmentConfirmationLineInsertRecord.class)))
                .thenAnswer(invocation -> {
                    persistedLines.add(invocation.getArgument(0));
                    return 1;
                });
        when(mapper.listConfirmationLines(370001L)).thenAnswer(invocation -> List.copyOf(persistedLines));
        when(mapper.updateBalanceQuantities(any(BalanceQuantityDelta.class))).thenReturn(1);

        ConfirmationCommand command = confirmationCommand("receipt-request-1", 5);
        var first = service.createConfirmation(receiptAccess(), command);
        var replay = service.createConfirmation(receiptAccess(), confirmationCommand("receipt-request-1", 5));

        assertThat(replay.id).isEqualTo(first.id);
        assertThat(replay.confirmationNo).isEqualTo(first.confirmationNo);
        assertThat(replay.confirmedQuantity).isEqualTo(5);
        assertThat(replay.lines).singleElement()
                .satisfies(line -> {
                    assertThat(line.purchaseOrderItemId).isEqualTo("210001");
                    assertThat(line.confirmedQuantity).isEqualTo(5);
                });

        assertThatThrownBy(() -> service.createConfirmation(
                receiptAccess(),
                confirmationCommand("receipt-request-1", 4)
        ))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");

        verify(mapper, times(1)).updateBalanceQuantities(any(BalanceQuantityDelta.class));
        verify(mapper, times(1)).insertConfirmation(any(FulfillmentConfirmationInsertRecord.class));
        verify(mapper, times(1)).insertConfirmationLine(any(FulfillmentConfirmationLineInsertRecord.class));
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectOrderAccess(eq(200001L), anyMap());
        order.verify(mapper).lockDispatchOwner(409L);
        order.verify(mapper).selectConfirmationByClientRequestId(409L, "receipt-request-1");
        order.verify(mapper).listBalancesForItemForUpdate(210001L, 200001L, 409L);
    }

    @Test
    void writeRequestsRejectMissingClientRequestIdBeforeReadingOrChangingInventory() {
        CreateDispatchPlanCommand dispatch = dispatchCommand(null, 5);
        ConfirmationCommand receipt = confirmationCommand(" ", 5);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), dispatch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户端请求号");
        assertThatThrownBy(() -> service.createConfirmation(access(), receipt))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户端请求号");

        verify(mapper, never()).lockDispatchOwner(anyLong());
        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), anyMap());
        verify(mapper, never()).updateBalanceQuantities(any(BalanceQuantityDelta.class));
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void writeRequestFailsClosedWhenOwnerRowCannotBeLocked() {
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance("CONFIRMED", "SUBMITTED")));
        when(mapper.lockDispatchOwner(307L)).thenReturn(null);

        assertThatThrownBy(() -> service.createDispatchPlan(
                access(),
                dispatchCommand("dispatch-request-owner-missing", 5)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法安全提交");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), anyMap());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void dispatchReplayRejectsMissingBlankUppercaseAndMalformedPersistedFingerprints() {
        DispatchPlanRecord existing = existingDispatchPlan("dispatch-request-invalid-fingerprint");
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance("CONFIRMED", "SUBMITTED")));
        when(mapper.selectDispatchPlanByClientRequestId(
                307L,
                "dispatch-request-invalid-fingerprint"
        )).thenReturn(existing);

        existing.requestFingerprint = null;
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");
        existing.requestFingerprint = " ";
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");
        existing.requestFingerprint =
                "1719BE8EB7BBF3AE9225E8CD9C8D5ABBC679D9162B8791EFB787524EF89FCC21";
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");
        existing.requestFingerprint = "deadbeef";
        assertFingerprintConflict("dispatch-request-invalid-fingerprint");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), anyMap());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
    }

    @Test
    void legacyDispatchFingerprintCannotIgnoreChangedRemark() {
        DispatchPlanRecord existing = existingDispatchPlan("dispatch-request-legacy");
        existing.remark = "原备注";
        existing.requestFingerprint =
                "8af1aeebcef57ea41741a3b0eea3b7aa0f876e346cd9f4800552fd4b7b570816";
        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance("CONFIRMED", "SUBMITTED")));
        when(mapper.selectDispatchPlanByClientRequestId(307L, "dispatch-request-legacy"))
                .thenReturn(existing);

        CreateDispatchPlanCommand changed = dispatchCommand("dispatch-request-legacy", 5);
        changed.remark = "修改后的备注";

        assertThatThrownBy(() -> service.createDispatchPlan(access(), changed))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), anyMap());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), anyInt(), anyLong());
    }

    private void assertFingerprintConflict(String clientRequestId) {
        assertThatThrownBy(() -> service.createDispatchPlan(
                access(),
                dispatchCommand(clientRequestId, 5)
        ))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");
    }

    private DispatchPlanRecord existingDispatchPlan(String clientRequestId) {
        DispatchPlanRecord existing = new DispatchPlanRecord();
        existing.id = 340001L;
        existing.ownerUserId = 307L;
        existing.clientRequestId = clientRequestId;
        existing.planNo = "DP-340001";
        existing.status = "DRAFT";
        return existing;
    }

    private CreateDispatchPlanCommand dispatchCommand(String clientRequestId, int quantity) {
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = clientRequestId;
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = quantity;
        source.targetSiteCode = "SA";
        source.actualTransportMode = "AIR";
        command.sources = List.of(source);
        return command;
    }

    private ConfirmationCommand confirmationCommand(String clientRequestId, int quantity) {
        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = clientRequestId;
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.confirmedQuantity = quantity;
        line.abnormalQuantity = 0;
        command.lines = List.of(line);
        return command;
    }

    private BusinessAccessContext receiptAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 409L))
                .build();
    }

    private PurchaseOrderAccessRecord purchaseOrder() {
        PurchaseOrderAccessRecord order = new PurchaseOrderAccessRecord();
        order.id = 200001L;
        order.ownerUserId = 409L;
        order.logicalStoreId = 301L;
        order.orderNo = "PO-200001";
        order.anchorStoreCodeCache = "STR69486-NSA";
        return order;
    }

    private PurchaseOrderItemRecord purchaseOrderItem() {
        PurchaseOrderItemRecord item = new PurchaseOrderItemRecord();
        item.id = 210001L;
        item.purchaseOrderId = 200001L;
        item.ownerUserId = 409L;
        item.logicalStoreId = 301L;
        item.productMasterId = 310001L;
        item.productVariantId = 320001L;
        item.partnerSku = "SGGRB115";
        item.skuParent = "SGGR";
        item.titleCache = "测试商品";
        item.fulfillmentType = "WAREHOUSE_RECEIPT";
        item.totalQuantity = 5;
        return item;
    }

    private PurchaseOrderItemSiteRecord purchaseOrderItemSite() {
        PurchaseOrderItemSiteRecord site = new PurchaseOrderItemSiteRecord();
        site.id = 220002L;
        site.purchaseOrderId = 200001L;
        site.purchaseOrderItemId = 210001L;
        site.ownerUserId = 409L;
        site.logicalStoreId = 301L;
        site.siteCode = "SA";
        site.transportMode = "AIR";
        site.quantity = 5;
        return site;
    }

    private FulfillmentBalanceRecord fulfillmentBalance() {
        FulfillmentBalanceRecord balance = new FulfillmentBalanceRecord();
        balance.id = 900001L;
        balance.purchaseOrderItemId = 210001L;
        balance.purchaseOrderItemSiteId = 220002L;
        balance.partnerSku = "SGGRB115";
        balance.plannedQuantity = 5;
        balance.confirmedQuantity = 0;
        balance.abnormalQuantity = 0;
        balance.reservedQuantity = 0;
        balance.logisticsHandoffQuantity = 0;
        balance.availableQuantity = 0;
        return balance;
    }
}
