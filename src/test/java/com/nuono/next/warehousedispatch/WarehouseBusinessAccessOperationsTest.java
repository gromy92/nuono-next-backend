package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationLineCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateFulfillmentCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseBusinessAccessOperationsTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void emptyStoreScopeRejectsFulfillmentWriteBeforeReadingOrder() {
        assertThatThrownBy(() -> service.updateItemFulfillment(
                accessWithStoreOwners(Map.of()),
                "200001",
                "210001",
                fulfillmentCommand()
        ))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("不能操作该采购单");

        verify(mapper, never()).selectOrderAccess(anyLong(), anyMap());
        verify(mapper, never()).selectPurchaseOrderItem(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).updatePurchaseOrderItemFulfillment(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void authorizedStoreCannotConfirmAnotherOwnersPurchaseOrder() {
        PurchaseOrderAccessRecord victimOrder = order(999L, "STR69486-NSA");
        when(mapper.selectOrderAccess(eq(200001L), anyMap())).thenReturn(victimOrder);

        assertThatThrownBy(() -> service.createConfirmation(
                accessWithStoreOwners(Map.of("STR69486-NSA", 307L)),
                confirmation()
        ))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("不能操作该采购单");

        verify(mapper, never()).selectPurchaseOrderItem(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).insertConfirmation(any());
    }

    @Test
    void matchingOwnerAndStoreCanUpdateFulfillment() {
        when(mapper.selectOrderAccess(eq(200001L), anyMap()))
                .thenReturn(order(409L, "STR69486-NSA"));
        when(mapper.selectPurchaseOrderItem(210001L, 200001L, 409L)).thenReturn(item(409L));
        when(mapper.listItemSitesForBalance(210001L, 200001L, 409L)).thenReturn(List.of(site(409L)));
        when(mapper.updatePurchaseOrderItemFulfillment(
                210001L,
                200001L,
                409L,
                "WAREHOUSE_RECEIPT",
                "上海仓",
                401L
        )).thenReturn(1);

        var view = service.updateItemFulfillment(
                accessWithStoreOwners(Map.of("STR69486-NSA", 409L)),
                "200001",
                "210001",
                fulfillmentCommand()
        );

        assertThat(view.purchaseOrderId).isEqualTo("200001");
        assertThat(view.purchaseOrderItemId).isEqualTo("210001");
        ArgumentCaptor<Map<String, Long>> storeOwners = ArgumentCaptor.forClass(Map.class);
        verify(mapper).selectOrderAccess(eq(200001L), storeOwners.capture());
        assertThat(storeOwners.getValue()).containsExactly(Map.entry("STR69486-NSA", 409L));
        verify(mapper).updatePurchaseOrderItemFulfillment(
                210001L,
                200001L,
                409L,
                "WAREHOUSE_RECEIPT",
                "上海仓",
                401L
        );
    }

    @Test
    void fulfillmentUpdateConflictDoesNotReportSuccess() {
        when(mapper.selectOrderAccess(eq(200001L), anyMap()))
                .thenReturn(order(307L, "STR69486-NSA"));
        when(mapper.selectPurchaseOrderItem(210001L, 200001L, 307L)).thenReturn(item(307L));
        when(mapper.listBalancesForItemForUpdate(210001L, 200001L, 307L)).thenReturn(List.of());
        when(mapper.updatePurchaseOrderItemFulfillment(
                210001L,
                200001L,
                307L,
                "WAREHOUSE_RECEIPT",
                "上海仓",
                401L
        )).thenReturn(0);

        assertThatThrownBy(() -> service.updateItemFulfillment(
                accessWithStoreOwners(Map.of("STR69486-NSA", 307L)),
                "200001",
                "210001",
                fulfillmentCommand()
        ))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("状态已变化");

        verify(mapper, never()).listItemSitesForBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void exactFulfillmentReplayDoesNotTouchInventory() {
        PurchaseOrderItemRecord unchanged = item(307L);
        unchanged.fulfillmentSourceName = "上海仓";
        when(mapper.selectOrderAccess(eq(200001L), anyMap()))
                .thenReturn(order(307L, "STR69486-NSA"));
        when(mapper.selectPurchaseOrderItem(210001L, 200001L, 307L)).thenReturn(unchanged);

        var view = service.updateItemFulfillment(
                accessWithStoreOwners(Map.of("STR69486-NSA", 307L)),
                "200001",
                "210001",
                fulfillmentCommand()
        );

        assertThat(view.fulfillmentType).isEqualTo("WAREHOUSE_RECEIPT");
        assertThat(view.sourceName).isEqualTo("上海仓");
        verify(mapper, never()).listBalancesForItemForUpdate(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).updatePurchaseOrderItemFulfillment(
                any(), any(), any(), any(), any(), any()
        );
        verify(mapper, never()).listItemSitesForBalance(anyLong(), anyLong(), anyLong());
    }

    @Test
    void activeBalancePreventsFulfillmentChange() {
        FulfillmentBalanceRecord activeBalance = balance("WAREHOUSE_RECEIPT");
        activeBalance.reservedQuantity = 1;
        when(mapper.selectOrderAccess(eq(200001L), anyMap()))
                .thenReturn(order(307L, "STR69486-NSA"));
        when(mapper.selectPurchaseOrderItem(210001L, 200001L, 307L)).thenReturn(item(307L));
        when(mapper.listBalancesForItemForUpdate(210001L, 200001L, 307L))
                .thenReturn(List.of(activeBalance));

        assertThatThrownBy(() -> service.updateItemFulfillment(
                accessWithStoreOwners(Map.of("STR69486-NSA", 307L)),
                "200001",
                "210001",
                fulfillmentCommand()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能修改履约方式");

        verify(mapper, never()).updatePurchaseOrderItemFulfillment(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void balanceFulfillmentConflictDoesNotReportSuccess() {
        FulfillmentBalanceRecord staleBalance = balance("SUPPLIER_DIRECT");
        when(mapper.selectOrderAccess(eq(200001L), anyMap()))
                .thenReturn(order(307L, "STR69486-NSA"));
        when(mapper.selectPurchaseOrderItem(210001L, 200001L, 307L)).thenReturn(item(307L));
        when(mapper.listBalancesForItemForUpdate(210001L, 200001L, 307L))
                .thenReturn(List.of(staleBalance));
        when(mapper.updatePurchaseOrderItemFulfillment(
                210001L,
                200001L,
                307L,
                "WAREHOUSE_RECEIPT",
                "上海仓",
                401L
        )).thenReturn(1);
        when(mapper.updateActiveBalancesFulfillment(
                210001L,
                200001L,
                307L,
                "WAREHOUSE_RECEIPT",
                401L
        )).thenReturn(0);

        assertThatThrownBy(() -> service.updateItemFulfillment(
                accessWithStoreOwners(Map.of("STR69486-NSA", 307L)),
                "200001",
                "210001",
                fulfillmentCommand()
        ))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("库存状态已变化");

        verify(mapper, never()).listItemSitesForBalance(anyLong(), anyLong(), anyLong());
    }

    private BusinessAccessContext accessWithStoreOwners(Map<String, Long> storeOwnerUserIds) {
        return BusinessAccessContext.builder()
                .sessionUserId(401L)
                .businessOwnerUserId(307L)
                .storeOwnerUserIds(storeOwnerUserIds)
                .build();
    }

    private UpdateFulfillmentCommand fulfillmentCommand() {
        UpdateFulfillmentCommand command = new UpdateFulfillmentCommand();
        command.fulfillmentType = "WAREHOUSE_RECEIPT";
        command.sourceName = "上海仓";
        return command;
    }

    private ConfirmationCommand confirmation() {
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.confirmedQuantity = 5;
        line.abnormalQuantity = 0;
        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "cross-owner-confirmation";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        command.lines = List.of(line);
        return command;
    }

    private PurchaseOrderAccessRecord order(Long ownerUserId, String storeCode) {
        PurchaseOrderAccessRecord order = new PurchaseOrderAccessRecord();
        order.id = 200001L;
        order.ownerUserId = ownerUserId;
        order.logicalStoreId = 301L;
        order.orderNo = "PO-200001";
        order.anchorStoreCodeCache = storeCode;
        return order;
    }

    private PurchaseOrderItemRecord item(Long ownerUserId) {
        PurchaseOrderItemRecord item = new PurchaseOrderItemRecord();
        item.id = 210001L;
        item.purchaseOrderId = 200001L;
        item.ownerUserId = ownerUserId;
        item.logicalStoreId = 301L;
        item.productMasterId = 310001L;
        item.productVariantId = 320001L;
        item.partnerSku = "SGGRB115";
        item.fulfillmentType = "WAREHOUSE_RECEIPT";
        return item;
    }

    private PurchaseOrderItemSiteRecord site(Long ownerUserId) {
        PurchaseOrderItemSiteRecord site = new PurchaseOrderItemSiteRecord();
        site.id = 220002L;
        site.purchaseOrderId = 200001L;
        site.purchaseOrderItemId = 210001L;
        site.ownerUserId = ownerUserId;
        site.logicalStoreId = 301L;
        site.siteCode = "SA";
        site.transportMode = "AIR";
        site.quantity = 5;
        return site;
    }

    private FulfillmentBalanceRecord balance(String fulfillmentType) {
        FulfillmentBalanceRecord balance = new FulfillmentBalanceRecord();
        balance.id = 900001L;
        balance.ownerUserId = 307L;
        balance.purchaseOrderId = 200001L;
        balance.purchaseOrderItemId = 210001L;
        balance.fulfillmentType = fulfillmentType;
        balance.confirmedQuantity = 0;
        balance.abnormalQuantity = 0;
        balance.reservedQuantity = 0;
        balance.logisticsHandoffQuantity = 0;
        return balance;
    }

}
