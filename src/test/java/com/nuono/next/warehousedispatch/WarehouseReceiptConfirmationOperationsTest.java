package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationLineCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceQuantityDelta;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseReceiptConfirmationOperationsTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void createConfirmationRejectsDuplicatePurchaseOrderItemsBeforeReadingBalances() {
        ConfirmationCommand command = confirmation(
                confirmationLine(210001L, 5),
                confirmationLine(210001L, 5)
        );

        assertThatThrownBy(() -> service.createConfirmation(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一采购单商品不能重复确认");

        verify(mapper, never()).selectPurchaseOrderItem(anyLong());
        verify(mapper, never()).updateBalanceQuantities(any(BalanceQuantityDelta.class));
    }

    @Test
    void createConfirmationRejectsBalanceUpdateThatNoLongerMatchesLockedState() {
        PurchaseOrderItemRecord item = purchaseOrderItem();
        PurchaseOrderItemSiteRecord site = purchaseOrderItemSite();
        FulfillmentBalanceRecord balance = fulfillmentBalance();
        when(mapper.selectOrderAccess(200001L)).thenReturn(purchaseOrder());
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);
        when(mapper.listItemSitesForBalance(210001L)).thenReturn(List.of(site));
        when(mapper.listBalancesForItemForUpdate(210001L)).thenReturn(List.of(balance));
        when(mapper.nextConfirmationId()).thenReturn(370001L);
        when(mapper.nextConfirmationLineId()).thenReturn(380001L);
        when(mapper.updateBalanceQuantities(any(BalanceQuantityDelta.class))).thenReturn(0);

        assertThatThrownBy(() -> service.createConfirmation(
                access(),
                confirmation(confirmationLine(210001L, 5))
        ))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("收货库存状态已变化");
    }

    private ConfirmationCommand confirmation(ConfirmationLineCommand... lines) {
        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-confirmation-test-request";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        command.lines = List.of(lines);
        return command;
    }

    private ConfirmationLineCommand confirmationLine(Long itemId, int quantity) {
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = String.valueOf(itemId);
        line.confirmedQuantity = quantity;
        line.abnormalQuantity = 0;
        return line;
    }

    private PurchaseOrderAccessRecord purchaseOrder() {
        PurchaseOrderAccessRecord order = new PurchaseOrderAccessRecord();
        order.id = 200001L;
        order.ownerUserId = 307L;
        order.logicalStoreId = 301L;
        order.orderNo = "PO-200001";
        order.anchorStoreCodeCache = "STR69486-NSA";
        return order;
    }

    private PurchaseOrderItemRecord purchaseOrderItem() {
        PurchaseOrderItemRecord item = new PurchaseOrderItemRecord();
        item.id = 210001L;
        item.purchaseOrderId = 200001L;
        item.ownerUserId = 307L;
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
        site.ownerUserId = 307L;
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
