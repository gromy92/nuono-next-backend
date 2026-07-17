package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchFromDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmationLineCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ConfirmPackingListsCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.IssueShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxItemCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateDispatchTargetCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.BalanceReceiptProgressRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ForwarderPurchaseRouteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ForwarderRouteCostComponentRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ForwarderRouteQuoteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentConfirmationLineInsertRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderAccessRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseOrderItemSiteRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PurchaseReceiptRow;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ConfirmationView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.ShippingBatchView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LocalDbWarehouseDispatchServiceTest {

    @Mock
    private WarehouseDispatchMapper mapper;

    private LocalDbWarehouseDispatchService service;

    @BeforeEach
    void setUp() {
        service = new LocalDbWarehouseDispatchService(mapper, new ObjectMapper());
    }

    @Test
    void listReceiptOrdersExposesItemStoreIdentityAndResolvedCoreSpec() {
        PurchaseReceiptRow row = new PurchaseReceiptRow();
        row.receiptSourceId = 290008L;
        row.receiptSourceNo = "SO-290008";
        row.receiptSourceTitle = "0710";
        row.receiptSourceStoreCode = "STR108065-NSA,STR69486-NSA";
        row.orderId = 200227L;
        row.orderNo = "PO-200227";
        row.orderTitle = "SGGR-0710";
        row.sourceStoreCode = "STR69486-NSA";
        row.itemId = 213691L;
        row.purchaseOrderItemSiteId = 225743L;
        row.productVariantId = 54101L;
        row.partnerSku = "SGGRB021";
        row.titleCache = "粉色迷你筋膜按摩枪";
        row.siteCode = "SA";
        row.transportMode = "AIR";
        row.expectedQuantity = 30;
        row.receivedQuantity = 0;
        row.plannedQuantity = 0;
        row.specStatus = "READY";
        row.productLengthCm = new BigDecimal("18.5");
        row.productWidthCm = new BigDecimal("12.0");
        row.productHeightCm = new BigDecimal("4.0");
        row.productWeightG = new BigDecimal("220.0");

        when(mapper.listReceiptRows(307L, access().getStoreCodes(), null)).thenReturn(List.of(row));

        var orders = service.listReceiptOrders(access(), null);

        assertThat(orders).singleElement().satisfies(order ->
                assertThat(order.items).singleElement().satisfies(item -> {
                    assertThat(item.storeCode).isEqualTo("STR69486-NSA");
                    assertThat(item.productVariantId).isEqualTo(54101L);
                    assertThat(item.productLengthCm).isEqualByComparingTo("18.5");
                    assertThat(item.productWidthCm).isEqualByComparingTo("12.0");
                    assertThat(item.productHeightCm).isEqualByComparingTo("4.0");
                    assertThat(item.productWeightG).isEqualByComparingTo("220.0");
                })
        );
    }

    @Test
    void createConfirmationAllowsSupplierOverReceiptAsAvailableInventory() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        PurchaseOrderItemRecord item = purchaseOrderItem();
        PurchaseOrderItemSiteRecord site = purchaseOrderItemSite(2);
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.plannedQuantity = 2;
        balance.confirmedQuantity = 0;
        balance.abnormalQuantity = 0;
        balance.reservedQuantity = 0;
        balance.logisticsHandoffQuantity = 0;
        balance.availableQuantity = 0;

        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-over-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.confirmedQuantity = 3;
        line.abnormalQuantity = 0;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);
        when(mapper.listItemSitesForBalance(210001L)).thenReturn(List.of(site));
        when(mapper.listBalancesForItemForUpdate(210001L)).thenReturn(List.of(balance));
        when(mapper.nextConfirmationId()).thenReturn(320001L);
        when(mapper.nextConfirmationLineId()).thenReturn(330001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        ConfirmationView view = service.createConfirmation(access(), command);

        assertThat(view.expectedQuantity).isEqualTo(2);
        assertThat(view.confirmedQuantity).isEqualTo(3);
        verify(mapper).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900001L)
                        && delta.confirmedDelta == 3
                        && delta.abnormalDelta == 0
                        && delta.planClosedDelta == 2
        ));
        verify(mapper).insertConfirmationLine(org.mockito.ArgumentMatchers.argThat(row ->
                row.expectedQuantity == 2
                        && row.confirmedQuantityDelta == 3
                        && row.snapshotJson.contains("\"overReceivedQuantity\":1")
        ));
    }

    @Test
    void createConfirmationStoresStructuredReceiptAdjustments() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        PurchaseOrderItemRecord item = purchaseOrderItem();
        PurchaseOrderItemSiteRecord site = purchaseOrderItemSite(10);
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.plannedQuantity = 10;
        balance.confirmedQuantity = 0;
        balance.abnormalQuantity = 0;
        balance.reservedQuantity = 0;
        balance.logisticsHandoffQuantity = 0;
        balance.availableQuantity = 0;

        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-structured-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.normalReceivedQuantity = 7;
        line.replenishmentQuantity = 1;
        line.replenishmentReason = "MISSING";
        line.returnQuantity = 1;
        line.damageQuantity = 1;
        line.overReceivedQuantity = 0;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);
        when(mapper.listItemSitesForBalance(210001L)).thenReturn(List.of(site));
        when(mapper.listBalancesForItemForUpdate(210001L)).thenReturn(List.of(balance));
        when(mapper.nextConfirmationId()).thenReturn(320001L);
        when(mapper.nextConfirmationLineId()).thenReturn(330001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        ConfirmationView view = service.createConfirmation(access(), command);

        assertThat(view.confirmedQuantity).isEqualTo(10);
        assertThat(view.abnormalQuantity).isEqualTo(3);
        verify(mapper).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900001L)
                        && delta.confirmedDelta == 10
                        && delta.abnormalDelta == 3
                        && delta.planClosedDelta == 9
        ));
        verify(mapper).insertConfirmationLine(org.mockito.ArgumentMatchers.argThat(row ->
                row.confirmedQuantityDelta == 10
                        && row.abnormalQuantityDelta == 3
                        && row.snapshotJson.contains("\"normalReceivedQuantity\":7")
                        && row.snapshotJson.contains("\"replenishmentQuantity\":1")
                        && row.snapshotJson.contains("\"replenishmentReason\":\"MISSING\"")
                        && row.snapshotJson.contains("\"returnQuantity\":1")
                        && row.snapshotJson.contains("\"damageQuantity\":1")
                        && row.snapshotJson.contains("\"planClosedQuantity\":9")
        ));
    }

    @Test
    void createConfirmationUpdatesEachBalanceOnceWhenMapperJoinReturnsDuplicates() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        PurchaseOrderItemRecord item = purchaseOrderItem();
        PurchaseOrderItemSiteRecord site = purchaseOrderItemSite(10);
        FulfillmentBalanceRecord balance = balance("PENDING_QUOTE", "NOT_SUBMITTED");
        balance.plannedQuantity = 10;
        balance.confirmedQuantity = 0;
        balance.abnormalQuantity = 0;
        balance.reservedQuantity = 0;
        balance.logisticsHandoffQuantity = 0;
        balance.availableQuantity = 0;

        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-duplicate-join-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.normalReceivedQuantity = 2;
        line.replenishmentQuantity = 1;
        line.returnQuantity = 1;
        line.damageQuantity = 1;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);
        when(mapper.listItemSitesForBalance(210001L)).thenReturn(List.of(site));
        when(mapper.listBalancesForItemForUpdate(210001L)).thenReturn(List.of(balance, balance));
        when(mapper.nextConfirmationId()).thenReturn(320001L);
        when(mapper.nextConfirmationLineId()).thenReturn(330001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        ConfirmationView view = service.createConfirmation(access(), command);

        assertThat(view.expectedQuantity).isEqualTo(10);
        assertThat(view.confirmedQuantity).isEqualTo(5);
        verify(mapper).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900001L)
                        && delta.confirmedDelta == 5
                        && delta.abnormalDelta == 3
                        && delta.planClosedDelta == 4
        ));
    }

    @Test
    void createConfirmationUsesRequestedItemSiteBalanceOnly() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        PurchaseOrderItemRecord item = purchaseOrderItem();
        PurchaseOrderItemSiteRecord firstSite = purchaseOrderItemSite(10);
        PurchaseOrderItemSiteRecord secondSite = purchaseOrderItemSite(6);
        secondSite.id = 220003L;
        secondSite.siteCode = "AE";
        secondSite.transportMode = "SEA";

        FulfillmentBalanceRecord firstBalance = balance("CONFIRMED", "SUBMITTED");
        firstBalance.id = 900001L;
        firstBalance.purchaseOrderItemSiteId = firstSite.id;
        firstBalance.plannedQuantity = 10;
        firstBalance.confirmedQuantity = 0;
        firstBalance.abnormalQuantity = 0;
        firstBalance.reservedQuantity = 0;
        firstBalance.logisticsHandoffQuantity = 0;
        firstBalance.availableQuantity = 0;

        FulfillmentBalanceRecord secondBalance = balance("CONFIRMED", "SUBMITTED");
        secondBalance.id = 900002L;
        secondBalance.purchaseOrderItemSiteId = secondSite.id;
        secondBalance.siteCode = "AE";
        secondBalance.plannedTransportMode = "SEA";
        secondBalance.plannedQuantity = 6;
        secondBalance.confirmedQuantity = 0;
        secondBalance.abnormalQuantity = 0;
        secondBalance.reservedQuantity = 0;
        secondBalance.logisticsHandoffQuantity = 0;
        secondBalance.availableQuantity = 0;

        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-site-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.purchaseOrderItemSiteId = 220003L;
        line.normalReceivedQuantity = 4;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);
        when(mapper.listItemSitesForBalance(210001L)).thenReturn(List.of(firstSite, secondSite));
        when(mapper.listBalancesForItemForUpdate(210001L)).thenReturn(List.of(firstBalance, secondBalance));
        when(mapper.nextConfirmationId()).thenReturn(320001L);
        when(mapper.nextConfirmationLineId()).thenReturn(330001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        ConfirmationView view = service.createConfirmation(access(), command);

        assertThat(view.expectedQuantity).isEqualTo(6);
        assertThat(view.confirmedQuantity).isEqualTo(4);
        verify(mapper).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900002L)
                        && delta.confirmedDelta == 4
                        && delta.abnormalDelta == 0
        ));
        verify(mapper, never()).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900001L)
        ));
        verify(mapper).insertConfirmationLine(org.mockito.ArgumentMatchers.argThat(row ->
                row.expectedQuantity == 6
                        && row.confirmedQuantityDelta == 4
        ));
    }

    @Test
    void createConfirmationRejectsNegativeStructuredQuantityBeforeItCanBeOffset() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        PurchaseOrderItemRecord item = purchaseOrderItem();
        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-negative-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.normalReceivedQuantity = -1;
        line.replenishmentQuantity = 2;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);

        assertThatThrownBy(() -> service.createConfirmation(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正常入库数量不能为负数");
        verify(mapper, never()).insertConfirmation(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createConfirmationReturnsExistingResultForRepeatedClientRequest() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        FulfillmentConfirmationInsertRecord existing = new FulfillmentConfirmationInsertRecord();
        existing.id = 320001L;
        existing.ownerUserId = 307L;
        existing.clientRequestId = "receipt-request-retry-1";
        existing.confirmationNo = "FC-320001";
        existing.confirmationType = "WAREHOUSE_RECEIPT";
        existing.status = "CONFIRMED";
        existing.expectedQuantity = 2;
        existing.confirmedQuantityDelta = 2;
        existing.abnormalQuantityDelta = 0;
        FulfillmentConfirmationLineInsertRecord existingLine = new FulfillmentConfirmationLineInsertRecord();
        existingLine.purchaseOrderItemId = 210001L;
        existingLine.partnerSku = "PAPERSAYS088";
        existingLine.expectedQuantity = 2;
        existingLine.confirmedQuantityDelta = 2;
        existingLine.abnormalQuantityDelta = 0;
        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-retry-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.normalReceivedQuantity = 2;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectConfirmationByClientRequestId(307L, "receipt-request-retry-1")).thenReturn(existing);
        when(mapper.listConfirmationLines(320001L)).thenReturn(List.of(existingLine));

        ConfirmationView view = service.createConfirmation(access(), command);

        assertThat(view.id).isEqualTo("320001");
        assertThat(view.confirmedQuantity).isEqualTo(2);
        verify(mapper, never()).nextConfirmationId();
        verify(mapper, never()).updateBalanceQuantities(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createConfirmationAllocatesOnlyToBalancesWithRemainingPlan() {
        PurchaseOrderAccessRecord order = purchaseOrder();
        PurchaseOrderItemRecord item = purchaseOrderItem();
        PurchaseOrderItemSiteRecord firstSite = purchaseOrderItemSite(6);
        PurchaseOrderItemSiteRecord secondSite = purchaseOrderItemSite(4);
        secondSite.id = 220003L;
        FulfillmentBalanceRecord firstBalance = balance("CONFIRMED", "SUBMITTED");
        firstBalance.id = 900001L;
        firstBalance.purchaseOrderItemSiteId = firstSite.id;
        firstBalance.plannedQuantity = 6;
        FulfillmentBalanceRecord secondBalance = balance("CONFIRMED", "SUBMITTED");
        secondBalance.id = 900002L;
        secondBalance.purchaseOrderItemSiteId = secondSite.id;
        secondBalance.plannedQuantity = 4;
        BalanceReceiptProgressRecord firstProgress = new BalanceReceiptProgressRecord();
        firstProgress.balanceId = 900001L;
        firstProgress.planClosedQuantity = 6;
        ConfirmationCommand command = new ConfirmationCommand();
        command.clientRequestId = "receipt-request-allocation-1";
        command.purchaseOrderId = "200001";
        command.confirmationType = "WAREHOUSE_RECEIPT";
        ConfirmationLineCommand line = new ConfirmationLineCommand();
        line.purchaseOrderItemId = "210001";
        line.normalReceivedQuantity = 2;
        command.lines = List.of(line);

        when(mapper.selectOrderAccess(200001L)).thenReturn(order);
        when(mapper.selectPurchaseOrderItem(210001L)).thenReturn(item);
        when(mapper.listItemSitesForBalance(210001L)).thenReturn(List.of(firstSite, secondSite));
        when(mapper.listBalancesForItemForUpdate(210001L)).thenReturn(List.of(firstBalance, secondBalance));
        when(mapper.listReceiptPlanClosedQuantities(List.of(900001L, 900002L))).thenReturn(List.of(firstProgress));
        when(mapper.nextConfirmationId()).thenReturn(320001L);
        when(mapper.nextConfirmationLineId()).thenReturn(330001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        service.createConfirmation(access(), command);

        verify(mapper).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900002L)
                        && delta.confirmedDelta == 2
                        && delta.abnormalDelta == 0
                        && delta.planClosedDelta == 2
        ));
        verify(mapper, never()).updateBalanceQuantities(org.mockito.ArgumentMatchers.argThat(delta ->
                delta.balanceId.equals(900001L)
        ));
    }

    @Test
    void createDispatchPlanAllowsAppRequestBeforeLogisticsQuoteIsConfirmedOrShippingSubmitted() {
        FulfillmentBalanceRecord balance = balance("PENDING_QUOTE", "NOT_SUBMITTED");
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-pending-quote";
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        source.actualTransportMode = "AIR";
        command.sources = List.of(source);

        WarehouseDispatchViews.DispatchPlanView view = service.createDispatchPlan(access(), command);

        assertThat(view.status).isEqualTo("DRAFT");
        assertThat(view.totalQuantity).isEqualTo(5);
        verify(mapper).reserveBalance(900001L, 5, 307L);
        verify(mapper).insertDispatchPlan(org.mockito.ArgumentMatchers.argThat(row ->
                "DRAFT".equals(row.status) && row.totalQuantity == 5
        ), eq(307L));
    }

    @Test
    void createDispatchPlanReturnsExistingPlanForRepeatedClientRequest() {
        DispatchPlanRecord existing = dispatchPlan();
        existing.clientRequestId = "app-dispatch-request-1";
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = existing.clientRequestId;
        command.sources = List.of(validDispatchPlanSource());

        when(mapper.selectDispatchPlanByClientRequestId(307L, existing.clientRequestId)).thenReturn(existing);

        WarehouseDispatchViews.DispatchPlanView view = service.createDispatchPlan(access(), command);

        assertThat(view.id).isEqualTo("340001");
        assertThat(view.clientRequestId).isEqualTo(existing.clientRequestId);
        verify(mapper).lockDispatchOwner(307L);
        verify(mapper, never()).selectBalancesForUpdate(org.mockito.ArgumentMatchers.anyList());
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
        verify(mapper, never()).insertDispatchPlan(org.mockito.ArgumentMatchers.any(), anyLong());
    }

    @Test
    void createDispatchPlanAllowsZdPendingQuoteAfterWarehouseOrderSubmission() {
        FulfillmentBalanceRecord balance = balance("PENDING_QUOTE", "SUBMITTED");
        balance.logisticsQuoteBlocking = false;
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-zd-pending-quote";
        command.sources = List.of(validDispatchPlanSource());

        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        WarehouseDispatchViews.DispatchPlanView view = service.createDispatchPlan(access(), command);

        assertThat(view.totalQuantity).isEqualTo(5);
        verify(mapper).reserveBalance(900001L, 5, 307L);
    }

    @Test
    void createDispatchPlanRequiresClientRequestIdBeforeAnyReservation() {
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.sources = List.of(validDispatchPlanSource());

        assertThatThrownBy(() -> service.createDispatchPlan(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户端请求号");
        verify(mapper, never()).selectBalancesForUpdate(org.mockito.ArgumentMatchers.anyList());
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createDispatchPlanUsesTargetSiteAndTransportWithoutMutatingSourcePlan() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.siteCode = "SA";
        balance.plannedTransportMode = "AIR";

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-target-override";
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        source.targetSiteCode = "AE";
        source.actualTransportMode = "SEA";
        command.sources = List.of(source);

        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        WarehouseDispatchViews.DispatchPlanView view = service.createDispatchPlan(access(), command);

        assertThat(view.lines).hasSize(1);
        assertThat(view.lines.get(0).siteCode).isEqualTo("AE");
        assertThat(view.lines.get(0).actualTransportMode).isEqualTo("SEA");
        verify(mapper).insertDispatchPlanLine(org.mockito.ArgumentMatchers.argThat(row ->
                "AE".equals(row.siteCode)
                        && "SEA".equals(row.actualTransportMode)
                        && row.quantity == 5
        ), eq(307L));
        verify(mapper).insertDispatchPlanLineSource(org.mockito.ArgumentMatchers.argThat(row ->
                row.fulfillmentBalanceId.equals(900001L)
                        && "AIR".equals(row.plannedTransportMode)
                        && row.quantity == 5
        ), eq(307L));
    }

    @Test
    void createDispatchPlanDefaultsToSavedInventoryDispatchTarget() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.siteCode = "SA";
        balance.plannedTransportMode = "AIR";
        balance.targetSiteCode = "AE";
        balance.targetTransportMode = "SEA";

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-saved-target";
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        command.sources = List.of(source);

        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        WarehouseDispatchViews.DispatchPlanView view = service.createDispatchPlan(access(), command);

        assertThat(view.lines).hasSize(1);
        assertThat(view.lines.get(0).siteCode).isEqualTo("AE");
        assertThat(view.lines.get(0).actualTransportMode).isEqualTo("SEA");
        verify(mapper).insertDispatchPlanLine(org.mockito.ArgumentMatchers.argThat(row ->
                "AE".equals(row.siteCode)
                        && "SEA".equals(row.actualTransportMode)
                        && row.quantity == 5
        ), eq(307L));
        verify(mapper).insertDispatchPlanLineSource(org.mockito.ArgumentMatchers.argThat(row ->
                row.fulfillmentBalanceId.equals(900001L)
                        && "AIR".equals(row.plannedTransportMode)
                        && row.quantity == 5
        ), eq(307L));
    }

    @Test
    void updateDispatchTargetPersistsTargetWithoutMutatingSourcePlan() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.siteCode = "SA";
        balance.plannedTransportMode = "AIR";

        UpdateDispatchTargetCommand command = new UpdateDispatchTargetCommand();
        command.targetSiteCode = "AE";
        command.targetTransportMode = "SEA";

        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.updateBalanceDispatchTarget(900001L, "AE", "SEA", 307L)).thenReturn(1);

        WarehouseDispatchViews.ReadySourceView view = service.updateDispatchTarget(access(), "900001", command);

        assertThat(view.fulfillmentBalanceId).isEqualTo(900001L);
        assertThat(view.siteCode).isEqualTo("SA");
        assertThat(view.plannedTransportMode).isEqualTo("AIR");
        assertThat(view.targetSiteCode).isEqualTo("AE");
        assertThat(view.targetTransportMode).isEqualTo("SEA");
        verify(mapper).updateBalanceDispatchTarget(900001L, "AE", "SEA", 307L);
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createDispatchPlanRejectsInvalidTargetSiteAndTransport() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");

        CreateDispatchPlanCommand invalidSiteCommand = new CreateDispatchPlanCommand();
        invalidSiteCommand.clientRequestId = "dispatch-invalid-site";
        DispatchPlanSourceCommand invalidSiteSource = new DispatchPlanSourceCommand();
        invalidSiteSource.fulfillmentBalanceId = 900001L;
        invalidSiteSource.quantity = 5;
        invalidSiteSource.targetSiteCode = "EG";
        invalidSiteCommand.sources = List.of(invalidSiteSource);

        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));

        assertThatThrownBy(() -> service.createDispatchPlan(access(), invalidSiteCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的目标站点");

        CreateDispatchPlanCommand invalidTransportCommand = new CreateDispatchPlanCommand();
        invalidTransportCommand.clientRequestId = "dispatch-invalid-transport";
        DispatchPlanSourceCommand invalidTransportSource = new DispatchPlanSourceCommand();
        invalidTransportSource.fulfillmentBalanceId = 900001L;
        invalidTransportSource.quantity = 5;
        invalidTransportSource.targetSiteCode = "SA";
        invalidTransportSource.actualTransportMode = "TRUCK";
        invalidTransportCommand.sources = List.of(invalidTransportSource);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), invalidTransportCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的目标货运方式");

        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createDispatchPlanAllowsTargetSiteWithoutProductOffer() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");

        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-target-no-offer";
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        source.targetSiteCode = "AE";
        source.actualTransportMode = "SEA";
        command.sources = List.of(source);

        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        when(mapper.nextDispatchLineId()).thenReturn(350001L);
        when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        when(mapper.nextOperationLogId()).thenReturn(390001L);

        WarehouseDispatchViews.DispatchPlanView view = service.createDispatchPlan(access(), command);

        assertThat(view.lines).hasSize(1);
        assertThat(view.lines.get(0).siteCode).isEqualTo("AE");
        assertThat(view.lines.get(0).actualTransportMode).isEqualTo("SEA");
        verify(mapper).reserveBalance(900001L, 5, 307L);
    }

    @Test
    void createDispatchPlanRejectsDuplicateBalanceTargets() {
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-duplicate-balance";
        DispatchPlanSourceCommand first = new DispatchPlanSourceCommand();
        first.fulfillmentBalanceId = 900001L;
        first.quantity = 3;
        first.targetSiteCode = "SA";
        DispatchPlanSourceCommand second = new DispatchPlanSourceCommand();
        second.fulfillmentBalanceId = 900001L;
        second.quantity = 2;
        second.targetSiteCode = "AE";
        command.sources = List.of(first, second);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一库存来源只能创建一个发货目标");
        verify(mapper, never()).selectBalancesForUpdate(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void createDispatchPlanRejectsNullSourceRowsBeforePartialReservation() {
        stubCreateDispatchPlanWouldOtherwiseSucceed();
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-null-source";
        command.sources = new java.util.ArrayList<>();
        command.sources.add(validDispatchPlanSource());
        command.sources.add(null);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发货来源数据异常");
        verify(mapper, never()).selectBalancesForUpdate(org.mockito.ArgumentMatchers.anyList());
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createDispatchPlanRejectsSourceRowsMissingFulfillmentBalanceIdBeforePartialReservation() {
        stubCreateDispatchPlanWouldOtherwiseSucceed();
        DispatchPlanSourceCommand malformed = new DispatchPlanSourceCommand();
        malformed.quantity = 2;
        malformed.targetSiteCode = "SA";
        malformed.actualTransportMode = "AIR";
        CreateDispatchPlanCommand command = new CreateDispatchPlanCommand();
        command.clientRequestId = "dispatch-missing-balance";
        command.sources = List.of(validDispatchPlanSource(), malformed);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发货来源数据异常");
        verify(mapper, never()).selectBalancesForUpdate(org.mockito.ArgumentMatchers.anyList());
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createDispatchPlanRejectsNonPositiveSourceQuantityBeforePartialReservation() {
        stubCreateDispatchPlanWouldOtherwiseSucceed();
        DispatchPlanSourceCommand zeroQuantity = validDispatchPlanSource();
        zeroQuantity.quantity = 0;
        CreateDispatchPlanCommand zeroCommand = new CreateDispatchPlanCommand();
        zeroCommand.clientRequestId = "dispatch-zero-quantity";
        zeroCommand.sources = List.of(validDispatchPlanSource(), zeroQuantity);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), zeroCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发货来源数据异常");

        DispatchPlanSourceCommand negativeQuantity = validDispatchPlanSource();
        negativeQuantity.quantity = -1;
        CreateDispatchPlanCommand negativeCommand = new CreateDispatchPlanCommand();
        negativeCommand.clientRequestId = "dispatch-negative-quantity";
        negativeCommand.sources = List.of(validDispatchPlanSource(), negativeQuantity);

        assertThatThrownBy(() -> service.createDispatchPlan(access(), negativeCommand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发货来源数据异常");
        verify(mapper, never()).selectBalancesForUpdate(org.mockito.ArgumentMatchers.anyList());
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createShippingBatchRejectsBalanceBeforeLogisticsQuoteIsConfirmedAndShippingSubmitted() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "NOT_SUBMITTED");
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));

        CreateShippingBatchCommand command = new CreateShippingBatchCommand();
        ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        command.sources = List.of(source);

        assertThatThrownBy(() -> service.createShippingBatch(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
    }

    @Test
    void createPackingListRejectsOutboundOrderWithBlockingLogisticsQuotes() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.countBlockingOutboundOrderLogisticsQuotes(800001L)).thenReturn(1);

        assertThatThrownBy(() -> service.createPackingList(access(), "800001", new CreatePackingListCommand()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("物流报价未确认");
        verify(mapper, never()).nextPackingListId();
    }

    @Test
    void createPackingListAllowsOutboundOrderAfterLogisticsQuoteIsSubmitted() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        CreatePackingListCommand command = new CreatePackingListCommand();
        command.remark = "仓库装箱";

        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.countBlockingOutboundOrderLogisticsQuotes(800001L)).thenReturn(0);
        when(mapper.nextPackingListId()).thenReturn(830001L);
        when(mapper.insertPackingList(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.markOutboundOrderPacking(800001L, 307L, 307L)).thenReturn(1);

        PackingListView view = service.createPackingList(access(), "800001", command);

        assertThat(view.id).isEqualTo("830001");
        assertThat(view.status).isEqualTo("DRAFT");
        verify(mapper).insertPackingList(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(830001L)
                        && row.outboundOrderId.equals(800001L)
                        && "仓库装箱".equals(row.remark)
        ), eq(307L));
    }

    @Test
    void createPackingListReturnsExistingActiveListOnRetry() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        PackingListRecord packingList = packingList();

        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.listPackingListsByOutboundOrder(800001L)).thenReturn(List.of(packingList));

        PackingListView view = service.createPackingList(access(), "800001", new CreatePackingListCommand());

        assertThat(view.id).isEqualTo("830001");
        verify(mapper, never()).nextPackingListId();
        verify(mapper, never()).insertPackingList(org.mockito.ArgumentMatchers.any(), anyLong());
    }

    @Test
    void issueShippingBatchRecoversMissingPackingListFromOutboundCreatedState() {
        ShippingBatchRecord batch = shippingBatch();
        batch.status = "OUTBOUND_CREATED";
        batch.selectedOptionId = 720001L;
        ShippingSuggestionOptionRecord option = new ShippingSuggestionOptionRecord();
        option.id = 720001L;
        option.batchId = batch.id;
        DispatchPlanRecord plan = dispatchPlan();
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        OutboundOrderRecord outboundOrder = outboundOrder();
        IssueShippingBatchCommand command = new IssueShippingBatchCommand();
        command.optionId = "720001";

        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectShippingSuggestionOptionById(720001L)).thenReturn(option);
        when(mapper.listOutboundOrdersByBatch(700001L)).thenReturn(List.of(outboundOrder));
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.countBlockingOutboundOrderLogisticsQuotes(800001L)).thenReturn(0);
        when(mapper.nextPackingListId()).thenReturn(830001L);
        when(mapper.insertPackingList(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.markOutboundOrderPacking(800001L, 307L, 307L)).thenReturn(1);
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.markDispatchPlanHandoffSuccess("WDH-340001-1", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(1);

        WarehouseDispatchViews.IssuedShippingBatchView view =
                service.issueShippingBatch(access(), "700001", command);

        assertThat(view.outboundOrders).hasSize(1);
        assertThat(view.outboundOrders.get(0).status).isEqualTo("PACKING");
        assertThat(view.packingLists).singleElement().satisfies(packingList -> {
            assertThat(packingList.id).isEqualTo("830001");
            assertThat(packingList.outboundOrderId).isEqualTo(800001L);
        });
        verify(mapper, never()).clearSelectedShippingOptions(anyLong(), anyLong());
        verify(mapper, never()).nextOutboundOrderId();
        verify(mapper).insertPackingList(org.mockito.ArgumentMatchers.argThat(row ->
                row.outboundOrderId.equals(800001L)
        ), eq(307L));
        verify(mapper).markDispatchPlanHandoffSuccess("WDH-340001-1", 307L);
        verify(mapper).moveReservedToLogisticsHandoff(900001L, 5, 307L);
    }

    @Test
    void issueShippingBatchIsIdempotentWhenPackingListAlreadyExists() {
        ShippingBatchRecord batch = shippingBatch();
        batch.status = "OUTBOUND_CREATED";
        batch.selectedOptionId = 720001L;
        ShippingSuggestionOptionRecord option = new ShippingSuggestionOptionRecord();
        option.id = 720001L;
        option.batchId = batch.id;
        DispatchPlanRecord plan = dispatchPlan();
        plan.status = "LOGISTICS_REQUESTED";
        OutboundOrderRecord outboundOrder = outboundOrder();
        PackingListRecord packingList = packingList();
        IssueShippingBatchCommand command = new IssueShippingBatchCommand();
        command.optionId = "720001";

        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectShippingSuggestionOptionById(720001L)).thenReturn(option);
        when(mapper.listOutboundOrdersByBatch(700001L)).thenReturn(List.of(outboundOrder));
        when(mapper.listPackingListsByOutboundOrder(800001L)).thenReturn(List.of(packingList));
        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);

        WarehouseDispatchViews.IssuedShippingBatchView view =
                service.issueShippingBatch(access(), "700001", command);

        assertThat(view.packingLists).singleElement().satisfies(existing ->
                assertThat(existing.id).isEqualTo("830001")
        );
        verify(mapper, never()).nextPackingListId();
        verify(mapper, never()).insertPackingList(org.mockito.ArgumentMatchers.any(), anyLong());
        verify(mapper, never()).markDispatchPlanHandoffSuccess(anyString(), anyLong());
        verify(mapper, never()).moveReservedToLogisticsHandoff(anyLong(), anyInt(), anyLong());
    }

    @Test
    void issueShippingBatchRejectsChangingOptionAfterOutboundCreation() {
        ShippingBatchRecord batch = shippingBatch();
        batch.status = "OUTBOUND_CREATED";
        batch.selectedOptionId = 720001L;
        ShippingSuggestionOptionRecord option = new ShippingSuggestionOptionRecord();
        option.id = 720002L;
        option.batchId = batch.id;
        IssueShippingBatchCommand command = new IssueShippingBatchCommand();
        command.optionId = "720002";

        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);
        when(mapper.selectShippingSuggestionOptionById(720002L)).thenReturn(option);

        assertThatThrownBy(() -> service.issueShippingBatch(access(), "700001", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已按其他物流方案下发");
        verify(mapper, never()).listOutboundOrdersByBatch(anyLong());
        verify(mapper, never()).insertPackingList(org.mockito.ArgumentMatchers.any(), anyLong());
    }

    @Test
    void replacePackingBoxesRefreshesOutboundLinePackedQuantities() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        OutboundOrderLineRecord firstLine = outboundLine(812001L, "PAPERSAYS088", 10);
        OutboundOrderLineRecord secondLine = outboundLine(812002L, "PAPERSAYSB085", 10);

        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        command.remark = "装箱完成";
        PackingBoxCommand box = new PackingBoxCommand();
        box.boxNo = "箱1";
        box.status = "SEALED";
        box.lengthCm = "28";
        box.widthCm = "18";
        box.heightCm = "16";
        box.grossWeightKg = "7.2";
        PackingBoxItemCommand firstItem = new PackingBoxItemCommand();
        firstItem.outboundOrderLineId = firstLine.id;
        firstItem.quantity = 10;
        PackingBoxItemCommand secondItem = new PackingBoxItemCommand();
        secondItem.outboundOrderLineId = secondLine.id;
        secondItem.quantity = 10;
        box.items = List.of(firstItem, secondItem);
        command.boxes = List.of(box);

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(firstLine, secondLine));
        when(mapper.nextPackingBoxId()).thenReturn(840001L);
        when(mapper.nextPackingBoxItemId()).thenReturn(852001L, 852002L);
        when(mapper.insertPackingBox(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.insertPackingBoxItem(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.updatePackingListTotals(anyLong(), anyLong(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyLong())).thenReturn(1);

        PackingListView view = service.replacePackingBoxes(access(), "830001", command);

        assertThat(view.boxCount).isEqualTo(1);
        assertThat(view.packedQuantity).isEqualTo(20);
        verify(mapper).refreshOutboundOrderLinePackedQuantities(800001L, 307L);
    }

    @Test
    void replacePackingBoxesAllowsSealedDraftWithoutBoxSpecs() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        OutboundOrderLineRecord line = outboundLine(812001L, "PAPERSAYS088", 2);
        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        PackingBoxCommand box = new PackingBoxCommand();
        box.boxNo = "箱1";
        box.status = "SEALED";
        PackingBoxItemCommand item = new PackingBoxItemCommand();
        item.outboundOrderLineId = line.id;
        item.quantity = 2;
        box.items = List.of(item);
        command.boxes = List.of(box);

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.nextPackingBoxId()).thenReturn(840001L);
        when(mapper.nextPackingBoxItemId()).thenReturn(852001L);
        when(mapper.insertPackingBox(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.insertPackingBoxItem(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.updatePackingListTotals(
                eq(830001L), eq(307L), eq(1), eq(2), isNull(), isNull(), isNull(), eq(307L)
        )).thenReturn(1);

        PackingListView view = service.replacePackingBoxes(access(), "830001", command);

        assertThat(view.boxes).singleElement().satisfies(savedBox -> {
            assertThat(savedBox.status).isEqualTo("SEALED");
            assertThat(savedBox.lengthCm).isNull();
            assertThat(savedBox.grossWeightKg).isNull();
        });
        assertThat(view.grossWeightKg).isNull();
        assertThat(view.volumeCbm).isNull();
    }

    @Test
    void replacePackingBoxesUpdatesExistingBoxByBoxNo() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        OutboundOrderLineRecord firstLine = outboundLine(812001L, "PAPERSAYS088", 2);
        PackingBoxRecord existingBox = packingBox(840231L, "箱1", "DRAFT", 2);

        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        PackingBoxCommand box = new PackingBoxCommand();
        box.boxNo = "箱1";
        box.status = "SEALED";
        box.lengthCm = "24";
        box.widthCm = "18";
        box.heightCm = "12";
        box.grossWeightKg = "4.1";
        PackingBoxItemCommand item = new PackingBoxItemCommand();
        item.outboundOrderLineId = firstLine.id;
        item.quantity = 2;
        box.items = List.of(item);
        command.boxes = List.of(box);

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(firstLine));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(existingBox));
        when(mapper.nextPackingBoxItemId()).thenReturn(852090L);
        when(mapper.updatePackingBox(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.insertPackingBoxItem(org.mockito.ArgumentMatchers.any(), eq(307L))).thenReturn(1);
        when(mapper.updatePackingListTotals(anyLong(), anyLong(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyLong())).thenReturn(1);

        PackingListView view = service.replacePackingBoxes(access(), "830001", command);

        assertThat(view.boxes).hasSize(1);
        assertThat(view.boxes.get(0).id).isEqualTo("840231");
        assertThat(view.boxes.get(0).status).isEqualTo("SEALED");
        verify(mapper, never()).nextPackingBoxId();
        verify(mapper, never()).insertPackingBox(org.mockito.ArgumentMatchers.any(), eq(307L));
        verify(mapper).updatePackingBox(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(840231L)
                        && "箱1".equals(row.boxNo)
                        && "SEALED".equals(row.status)
                        && row.grossWeightKg.compareTo(new BigDecimal("4.1")) == 0
        ), eq(307L));
        verify(mapper).insertPackingBoxItem(org.mockito.ArgumentMatchers.argThat(row ->
                row.packingBoxId.equals(840231L)
                        && row.outboundOrderLineId.equals(812001L)
                        && row.quantity == 2
        ), eq(307L));
    }

    @Test
    void replacePackingBoxesAllowsEmptyBoxesToClearPackingList() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        PackingBoxRecord existingBox = packingBox(840231L, "箱1", "DRAFT", 4);

        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        command.boxes = List.of();

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of());
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(existingBox));
        when(mapper.deletePackingBox(840231L, 307L)).thenReturn(1);
        when(mapper.updatePackingListTotals(anyLong(), anyLong(), anyInt(), anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyLong())).thenReturn(1);

        PackingListView view = service.replacePackingBoxes(access(), "830001", command);

        assertThat(view.boxCount).isZero();
        assertThat(view.packedQuantity).isZero();
        assertThat(view.boxes).isEmpty();
        verify(mapper).deletePackingBoxItems(830001L, 307L);
        verify(mapper).deletePackingBox(840231L, 307L);
        verify(mapper).updatePackingListTotals(
                eq(830001L),
                eq(307L),
                eq(0),
                eq(0),
                org.mockito.ArgumentMatchers.argThat(value -> value.compareTo(BigDecimal.ZERO) == 0),
                org.mockito.ArgumentMatchers.argThat(value -> value.compareTo(new BigDecimal("0.0000")) == 0),
                eq(null),
                eq(307L)
        );
    }

    @Test
    void confirmPackingListAcceptsAlreadySealedBoxes() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        OutboundOrderLineRecord line = outboundLine(812001L, "PAPERSAYS088", 2);
        PackingBoxRecord box = packingBox(840231L, "箱1", "SEALED", 2);
        PackingBoxItemRecord item = packingBoxItem(852090L, box.id, line.id, 2);

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(box));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(item));
        when(mapper.confirmPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderPacked(800001L, 307L, 307L)).thenReturn(1);

        PackingListView view = service.confirmPackingList(access(), "830001");

        assertThat(view.status).isEqualTo("CONFIRMED");
        assertThat(view.boxes).hasSize(1);
        assertThat(view.boxes.get(0).status).isEqualTo("SEALED");
    }

    @Test
    void confirmPackingListRejectsUnsealedBox() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        OutboundOrderLineRecord line = outboundLine(812001L, "PAPERSAYS088", 2);
        PackingBoxRecord box = packingBox(840231L, "箱1", "DRAFT", 2);
        PackingBoxItemRecord item = packingBoxItem(852090L, box.id, line.id, 2);

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(box));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.confirmPackingList(access(), "830001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("箱1 尚未封箱");
        verify(mapper, never()).confirmPackingList(anyLong(), anyLong(), anyLong());
    }

    @Test
    void confirmPackingListRejectsMissingBoxSpecsOnlyAtFinalSubmission() {
        PackingListRecord packingList = packingList();
        OutboundOrderRecord outboundOrder = outboundOrder();
        OutboundOrderLineRecord line = outboundLine(812001L, "PAPERSAYS088", 2);
        PackingBoxRecord box = packingBox(840231L, "箱1", "SEALED", 2);
        box.lengthCm = null;
        box.widthCm = null;
        box.heightCm = null;
        box.grossWeightKg = null;
        PackingBoxItemRecord item = packingBoxItem(852090L, box.id, line.id, 2);

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(outboundOrder);
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(line));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(box));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(item));

        assertThatThrownBy(() -> service.confirmPackingList(access(), "830001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("确认装箱前必须填写箱规和毛重");
        verify(mapper, never()).confirmPackingList(anyLong(), anyLong(), anyLong());
    }

    @Test
    void confirmPackingListsValidatesEveryListBeforeUpdatingAnyStatus() {
        PackingListRecord firstPackingList = packingList();
        PackingListRecord secondPackingList = packingList();
        secondPackingList.id = 830002L;
        secondPackingList.outboundOrderId = 800002L;
        secondPackingList.packingNo = "PK-830002";
        OutboundOrderRecord firstOutboundOrder = outboundOrder();
        OutboundOrderRecord secondOutboundOrder = outboundOrder();
        secondOutboundOrder.id = 800002L;
        secondOutboundOrder.outboundNo = "WO-800002";
        OutboundOrderLineRecord firstLine = outboundLine(812001L, "PAPERSAYS088", 2);
        OutboundOrderLineRecord secondLine = outboundLine(812002L, "PAPERSAYSB085", 3);
        secondLine.outboundOrderId = 800002L;
        PackingBoxRecord firstBox = packingBox(840231L, "箱1", "SEALED", 2);
        PackingBoxRecord secondBox = packingBox(840232L, "箱2", "DRAFT", 3);
        secondBox.packingListId = 830002L;
        secondBox.outboundOrderId = 800002L;
        PackingBoxItemRecord firstItem = packingBoxItem(852090L, firstBox.id, firstLine.id, 2);
        PackingBoxItemRecord secondItem = packingBoxItem(852091L, secondBox.id, secondLine.id, 3);
        secondItem.packingListId = 830002L;
        secondItem.outboundOrderId = 800002L;

        when(mapper.selectPackingListByIdForUpdate(830001L)).thenReturn(firstPackingList);
        when(mapper.selectPackingListByIdForUpdate(830002L)).thenReturn(secondPackingList);
        when(mapper.selectOutboundOrderByIdForUpdate(800001L)).thenReturn(firstOutboundOrder);
        when(mapper.selectOutboundOrderByIdForUpdate(800002L)).thenReturn(secondOutboundOrder);
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(firstBox));
        when(mapper.listPackingBoxes(830002L)).thenReturn(List.of(secondBox));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(firstItem));
        when(mapper.listPackingBoxItems(830002L)).thenReturn(List.of(secondItem));
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(firstLine));
        when(mapper.listOutboundOrderLines(800002L)).thenReturn(List.of(secondLine));

        ConfirmPackingListsCommand command = new ConfirmPackingListsCommand();
        command.packingListIds = List.of("830001", "830002");

        assertThatThrownBy(() -> service.confirmPackingLists(access(), command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("箱2 尚未封箱");
        verify(mapper, never()).confirmPackingList(anyLong(), anyLong(), anyLong());
        verify(mapper, never()).markOutboundOrderPacked(anyLong(), anyLong(), anyLong());
    }

    @Test
    void listPackingListsTreatsLegacyShippedDraftBoxesAsSealed() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        PackingListRecord packingList = packingList();
        packingList.status = "SHIPPED";
        PackingBoxRecord box = packingBox(840231L, "箱1", "DRAFT", 2);

        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.listPackingListsByOutboundOrder(800001L)).thenReturn(List.of(packingList));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(box));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of());

        List<PackingListView> views = service.listPackingLists(access(), "800001");

        assertThat(views).hasSize(1);
        assertThat(views.get(0).boxes).hasSize(1);
        assertThat(views.get(0).boxes.get(0).status).isEqualTo("SEALED");
    }

    @Test
    void logisticsHandoffSuccessOnlyMovesReservedBalance() {
        DispatchPlanRecord plan = dispatchPlan();
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;

        when(mapper.selectDispatchPlanByHandoffRequest("WDH-340001-1")).thenReturn(plan);
        when(mapper.markDispatchPlanHandoffSuccess("WDH-340001-1", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(1);

        service.markLogisticsHandoffSuccess(access(), "WDH-340001-1");

        verify(mapper).moveReservedToLogisticsHandoff(900001L, 5, 307L);
    }

    @Test
    void logisticsHandoffRejectsPartialSuccessWhenReservedBalanceCannotMove() {
        DispatchPlanRecord plan = dispatchPlan();
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;

        when(mapper.selectDispatchPlanByHandoffRequest("WDH-340001-1")).thenReturn(plan);
        when(mapper.markDispatchPlanHandoffSuccess("WDH-340001-1", 307L)).thenReturn(1);
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.moveReservedToLogisticsHandoff(900001L, 5, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.markLogisticsHandoffSuccess(access(), "WDH-340001-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("预留库存不足");
    }

    @Test
    void createShippingBatchFromDispatchPlanUsesSubmittedWarehouseOrderReservationWithoutRecheckingHistoricalQuote() {
        DispatchPlanRecord plan = dispatchPlan();
        plan.status = "DRAFT";
        plan.handoffGenerationNo = 0;
        DispatchPlanLineRecord line = new DispatchPlanLineRecord();
        line.id = 350001L;
        line.siteCode = "AE";
        line.actualTransportMode = "SEA";
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.id = 360001L;
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        FulfillmentBalanceRecord balance = balance("PENDING_QUOTE", "NOT_SUBMITTED");
        balance.logisticsQuoteBlocking = true;
        balance.siteCode = "SA";
        balance.plannedTransportMode = "AIR";
        balance.reservedQuantity = 5;
        balance.availableQuantity = 0;

        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of(line));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(710001L);
        when(mapper.nextShippingSuggestionOptionId()).thenReturn(720001L, 720002L, 720003L, 720004L, 720005L);
        when(mapper.nextShippingSuggestionLineId()).thenReturn(730001L, 730002L, 730003L, 730004L, 730005L);
        when(mapper.nextShippingSuggestionLineSourceId()).thenReturn(740001L, 740002L, 740003L, 740004L, 740005L);
        when(mapper.listActivePurchaseOrderRoutes(List.of("AE"), List.of("SEA")))
                .thenReturn(List.of(activeRoute("ET-AE-SEA-FBN-DXB-20260604", "易通阿联酋海运 + 海外仓 + FBN迪拜送仓 20260604", "ET", "易通", "AE", "SEA")));
        when(mapper.updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L)).thenReturn(1);

        ShippingBatchView view = service.createShippingBatchFromDispatchPlan(access(), "340001");

        assertThat(view.id).isEqualTo("700001");
        assertThat(view.totalQuantity).isEqualTo(5);
        assertThat(view.options).hasSize(1);
        assertThat(view.options.get(0).targetForwarderCodes).containsExactly("ET");
        assertThat(view.options.get(0).routeCodes).containsExactly("ET-AE-SEA-FBN-DXB-20260604");
        verify(mapper, never()).reserveBalance(anyLong(), anyInt(), anyLong());
        verify(mapper).insertShippingBatchSource(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(710001L)
                        && row.fulfillmentBalanceId.equals(900001L)
                        && row.reservedQuantity == 5
                        && "AE".equals(row.siteCode)
                        && "SEA".equals(row.plannedTransportMode)
        ), eq(307L));
        verify(mapper).updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L);
    }

    @Test
    void createShippingBatchFromDispatchPlanRejectsSourceWithoutMatchingDispatchLine() {
        DispatchPlanRecord plan = dispatchPlan();
        plan.status = "DRAFT";
        plan.handoffGenerationNo = 0;
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.id = 360001L;
        source.dispatchPlanLineId = 350999L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.siteCode = "SA";
        balance.plannedTransportMode = "AIR";
        balance.reservedQuantity = 5;

        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of());
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        org.mockito.Mockito.lenient().when(mapper.nextShippingBatchId()).thenReturn(700001L);
        org.mockito.Mockito.lenient().when(mapper.nextShippingBatchSourceId()).thenReturn(710001L);
        org.mockito.Mockito.lenient().when(mapper.updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L)).thenReturn(1);

        assertThatThrownBy(() -> service.createShippingBatchFromDispatchPlan(access(), "340001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("发货申请单商品行不存在或已变化");
        verify(mapper, never()).insertShippingBatch(org.mockito.ArgumentMatchers.any(), anyLong());
        verify(mapper, never()).insertShippingBatchSource(org.mockito.ArgumentMatchers.any(), anyLong());
    }

    @Test
    void createShippingBatchFromDispatchPlanIncludesActiveDatabaseRoutes() {
        DispatchPlanRecord plan = dispatchPlan();
        plan.status = "DRAFT";
        plan.handoffGenerationNo = 0;
        DispatchPlanLineRecord line = new DispatchPlanLineRecord();
        line.id = 350001L;
        line.siteCode = "SA";
        line.actualTransportMode = "AIR";
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.id = 360001L;
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 3;
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.siteCode = "SA";
        balance.plannedTransportMode = "AIR";
        balance.reservedQuantity = 3;
        balance.availableQuantity = 0;
        balance.productLengthCm = new BigDecimal("20");
        balance.productWidthCm = new BigDecimal("15");
        balance.productHeightCm = new BigDecimal("10");
        balance.productWeightG = new BigDecimal("500");

        ForwarderRouteQuoteRecord qikeHeadhaul = new ForwarderRouteQuoteRecord();
        qikeHeadhaul.routeCode = "QIKE-SAU-AIR-FBN-RUH-20260523";
        qikeHeadhaul.routeName = "启客沙特空运双清 + FBN利雅得送仓 20260523";
        qikeHeadhaul.forwarderCode = "QIKE";
        qikeHeadhaul.forwarderName = "启客";
        qikeHeadhaul.transportMode = "AIR";
        qikeHeadhaul.cargoCategoryCode = "CAT-A";
        qikeHeadhaul.cargoCategoryName = "普货";
        qikeHeadhaul.currency = "RMB";
        qikeHeadhaul.minUnitPrice = new BigDecimal("12");
        qikeHeadhaul.billingUnit = "KG";
        qikeHeadhaul.minBillableUnit = new BigDecimal("10");

        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of(line));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(710001L);
        when(mapper.nextShippingSuggestionOptionId()).thenReturn(720001L, 720002L, 720003L, 720004L, 720005L);
        when(mapper.nextShippingSuggestionLineId()).thenReturn(730001L, 730002L, 730003L, 730004L, 730005L);
        when(mapper.nextShippingSuggestionLineSourceId()).thenReturn(740001L, 740002L, 740003L, 740004L, 740005L);
        when(mapper.listActivePurchaseOrderRoutes(List.of("SA"), List.of("AIR")))
                .thenReturn(List.of(activeRoute("QIKE-SAU-AIR-FBN-RUH-20260523", "启客沙特空运双清 + FBN利雅得送仓 20260523", "QIKE", "启客", "SA", "AIR")));
        when(mapper.listForwarderRouteQuotes(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(qikeHeadhaul));
        when(mapper.listForwarderRouteCostComponents(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());
        when(mapper.updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L)).thenReturn(1);

        ShippingBatchView view = service.createShippingBatchFromDispatchPlan(access(), "340001");

        assertThat(view.options)
                .anySatisfy(option -> {
                    assertThat(option.targetForwarderCodes).contains("QIKE");
                    assertThat(option.routeCodes).contains("QIKE-SAU-AIR-FBN-RUH-20260523");
                    assertThat(option.optionName).contains("启客");
                });
    }

    @Test
    void createShippingBatchFromDispatchPlanUsesSelectedForwardersAndPairCombinationsWithCheapestLineAssignment() {
        DispatchPlanRecord plan = dispatchPlan();
        plan.status = "DRAFT";
        plan.handoffGenerationNo = 0;
        DispatchPlanLineRecord normalLine = new DispatchPlanLineRecord();
        normalLine.id = 350001L;
        normalLine.siteCode = "SA";
        normalLine.actualTransportMode = "SEA";
        DispatchPlanLineRecord sensitiveLine = new DispatchPlanLineRecord();
        sensitiveLine.id = 350002L;
        sensitiveLine.siteCode = "SA";
        sensitiveLine.actualTransportMode = "SEA";
        DispatchPlanLineSourceRecord normalSource = new DispatchPlanLineSourceRecord();
        normalSource.id = 360001L;
        normalSource.dispatchPlanLineId = 350001L;
        normalSource.fulfillmentBalanceId = 900001L;
        normalSource.quantity = 10;
        DispatchPlanLineSourceRecord sensitiveSource = new DispatchPlanLineSourceRecord();
        sensitiveSource.id = 360002L;
        sensitiveSource.dispatchPlanLineId = 350002L;
        sensitiveSource.fulfillmentBalanceId = 900002L;
        sensitiveSource.quantity = 3;
        FulfillmentBalanceRecord normalBalance = balance("CONFIRMED", "SUBMITTED");
        normalBalance.siteCode = "SA";
        normalBalance.plannedTransportMode = "SEA";
        normalBalance.reservedQuantity = 10;
        normalBalance.availableQuantity = 0;
        normalBalance.productLengthCm = new BigDecimal("100");
        normalBalance.productWidthCm = new BigDecimal("100");
        normalBalance.productHeightCm = new BigDecimal("3");
        normalBalance.productWeightG = new BigDecimal("500");
        FulfillmentBalanceRecord sensitiveBalance = balance("CONFIRMED", "SUBMITTED");
        sensitiveBalance.id = 900002L;
        sensitiveBalance.productVariantId = 320002L;
        sensitiveBalance.partnerSku = "SGGRB116";
        sensitiveBalance.purchaseOrderItemId = 210002L;
        sensitiveBalance.purchaseOrderItemSiteId = 220003L;
        sensitiveBalance.siteCode = "SA";
        sensitiveBalance.plannedTransportMode = "SEA";
        sensitiveBalance.reservedQuantity = 3;
        sensitiveBalance.availableQuantity = 0;
        sensitiveBalance.productLengthCm = new BigDecimal("40");
        sensitiveBalance.productWidthCm = new BigDecimal("25");
        sensitiveBalance.productHeightCm = new BigDecimal("20");
        sensitiveBalance.productWeightG = new BigDecimal("800");
        sensitiveBalance.liquidPowderType = "liquid";

        CreateShippingBatchFromDispatchPlanCommand command = new CreateShippingBatchFromDispatchPlanCommand();
        command.selectedForwarderCodes = List.of("ZD", "YT", "ET");

        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of(normalLine, sensitiveLine));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(normalSource, sensitiveSource));
        when(mapper.selectBalancesForUpdate(List.of(900001L, 900002L))).thenReturn(List.of(normalBalance, sensitiveBalance));
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(710001L, 710002L);
        when(mapper.nextShippingSuggestionOptionId()).thenReturn(720001L, 720002L, 720003L, 720004L, 720005L, 720006L);
        when(mapper.nextShippingSuggestionLineId()).thenReturn(730001L, 730002L, 730003L, 730004L, 730005L, 730006L,
                730007L, 730008L, 730009L, 730010L, 730011L, 730012L);
        when(mapper.nextShippingSuggestionLineSourceId()).thenReturn(740001L, 740002L, 740003L, 740004L, 740005L, 740006L,
                740007L, 740008L, 740009L, 740010L, 740011L, 740012L);
        when(mapper.listActivePurchaseOrderRoutes(List.of("SA"), List.of("SEA"))).thenReturn(List.of(
                activeRoute("ZD-SAU-SEA-FBN-RUH", "众鸫沙特海运", "ZD", "众鸫", "SA", "SEA"),
                activeRoute("YT-SAU-SEA-FBN-RUH", "义特沙特海运", "YT", "义特", "SA", "SEA"),
                activeRoute("ET-SAU-SEA-FBN-RUH-20260604", "易通沙特海运", "ET", "易通", "SA", "SEA")
        ));
        when(mapper.listForwarderRouteQuotes(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(
                routeQuote("ZD-SAU-SEA-FBN-RUH", "ZD", "众鸫", "CAT-003", "A类", "SEA", "12"),
                routeQuote("ZD-SAU-SEA-FBN-RUH", "ZD", "众鸫", "CAT-014", "G类", "SEA", "30"),
                routeQuote("YT-SAU-SEA-FBN-RUH", "YT", "义特", "CAT-020", "普货", "SEA", "5"),
                routeQuote("YT-SAU-SEA-FBN-RUH", "YT", "义特", "CAT-028", "敏感货", "SEA", "20"),
                routeQuote("ET-SAU-SEA-FBN-RUH-20260604", "ET", "易通", "CAT-A", "普货", "SEA", "10"),
                routeQuote("ET-SAU-SEA-FBN-RUH-20260604", "ET", "易通", "CAT-D", "D类", "SEA", "6")
        ));
        when(mapper.listForwarderRouteCostComponents(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of());
        when(mapper.updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L)).thenReturn(1);

        ShippingBatchView view = service.createShippingBatchFromDispatchPlan(access(), "340001", command);

        assertThat(view.options)
                .extracting(option -> option.optionName)
                .containsExactly("众鸫", "义特", "易通", "众鸫 + 义特", "众鸫 + 易通", "义特 + 易通");
        assertThat(view.options)
                .filteredOn(option -> "FORWARDER_YT_ET".equals(option.optionType))
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.targetForwarderCodes).containsExactly("YT", "ET");
                    assertThat(option.estimatedTotalAmount).isEqualByComparingTo("1.8600");
                    assertThat(option.lines)
                            .extracting(line -> line.partnerSku, line -> line.targetForwarderCode)
                            .containsExactlyInAnyOrder(
                                    org.assertj.core.groups.Tuple.tuple("SGGRB115", "YT"),
                                    org.assertj.core.groups.Tuple.tuple("SGGRB116", "ET")
                            );
                });
    }

    @Test
    void listDispatchPlansIncludesCurrentGeneratedShippingBatch() {
        DispatchPlanRecord plan = dispatchPlan();
        ShippingBatchRecord batch = shippingBatch();
        batch.optionCount = 2;
        batch.actualWeightKg = new BigDecimal("1.500");
        batch.volumeCbm = new BigDecimal("0.0300");

        when(mapper.listDispatchPlans(307L, 50)).thenReturn(List.of(plan));
        when(mapper.listDispatchPlanLinesByPlanIds(List.of(340001L))).thenReturn(List.of());
        when(mapper.listDispatchLineSourcesByPlanIds(List.of(340001L))).thenReturn(List.of());
        when(mapper.listLatestShippingBatchSummariesByDispatchPlanIds(List.of(340001L))).thenReturn(List.of(batch));

        List<WarehouseDispatchViews.DispatchPlanView> views = service.listDispatchPlans(access());

        assertThat(views).hasSize(1);
        assertThat(views.get(0).currentShippingBatch).isNotNull();
        assertThat(views.get(0).currentShippingBatch.id).isEqualTo("700001");
        assertThat(views.get(0).currentShippingBatch.batchNo).isEqualTo("SB-700001");
        assertThat(views.get(0).currentShippingBatch.optionCount).isEqualTo(2);
        assertThat(views.get(0).currentShippingBatch.actualWeightKg).isEqualByComparingTo("1.500");
        assertThat(views.get(0).currentShippingBatch.volumeCbm).isEqualByComparingTo("0.0300");
        assertThat(views.get(0).currentShippingBatch.options).isEmpty();
        verify(mapper, never()).listShippingBatchSources(700001L);
        verify(mapper, never()).listShippingSuggestionOptions(700001L);
    }

    @Test
    void getShippingBatchRestoresStoredPerProductAndBatchCostBreakdown() {
        ShippingBatchRecord batch = shippingBatch();
        ShippingSuggestionOptionRecord option = new ShippingSuggestionOptionRecord();
        option.id = 720001L;
        option.batchId = 700001L;
        option.optionType = "FORWARDER_ET";
        option.optionName = "易通";
        option.actualWeightKg = new BigDecimal("1.000");
        option.volumeCbm = new BigDecimal("0.1000");
        option.estimatedTotalAmount = new BigDecimal("20.3000");
        option.currency = "RMB";
        option.costSnapshotJson = "[{"
                + "\"partnerSku\":\"PAPERSAYS001\","
                + "\"targetForwarderCode\":\"ET\","
                + "\"routeCode\":\"ET-SAU-SEA-FBN-RUH\","
                + "\"transportMode\":\"SEA\","
                + "\"rawBillableQuantity\":0.1,"
                + "\"minimumBillableUnit\":0.2,"
                + "\"billableQuantity\":0.2,"
                + "\"billingUnit\":\"CBM\","
                + "\"freightAmount\":20,"
                + "\"minimumNotMet\":true,"
                + "\"costComponents\":["
                + "{\"componentType\":\"HEADHAUL\",\"componentName\":\"干线运费\",\"currency\":\"RMB\","
                + "\"unitPrice\":100,\"billingUnit\":\"CBM\",\"billableQuantity\":0.2,\"amount\":20},"
                + "{\"componentType\":\"WAREHOUSE_INBOUND\",\"componentName\":\"散件仓按件上架-小件\","
                + "\"currency\":\"RMB\",\"unitPrice\":0.3,\"billingUnit\":\"PIECE\","
                + "\"billableQuantity\":1,\"amount\":0.3}]}]";
        ShippingSuggestionLineRecord line = new ShippingSuggestionLineRecord();
        line.id = 730001L;
        line.optionId = 720001L;
        line.batchId = 700001L;
        line.partnerSku = "PAPERSAYS001";
        line.titleCache = "测试商品";
        line.siteCode = "SA";
        line.actualTransportMode = "SEA";
        line.targetForwarderCode = "ET";
        line.routeCode = "ET-SAU-SEA-FBN-RUH";
        line.estimatedAmount = new BigDecimal("20.3000");
        line.currency = "RMB";
        line.quantity = 1;

        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of());
        when(mapper.listShippingSuggestionOptions(700001L)).thenReturn(List.of(option));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(line));
        when(mapper.listShippingSuggestionLineSources(700001L)).thenReturn(List.of());

        ShippingBatchView restoredBatch = service.getShippingBatch(access(), "700001");

        assertThat(restoredBatch.options).singleElement().satisfies(restored -> {
            assertThat(restored.costComponents)
                    .extracting(component -> component.componentName)
                    .containsExactly("干线运费", "散件仓按件上架-小件");
            assertThat(restored.lines).singleElement().satisfies(restoredLine -> {
                assertThat(restoredLine.partnerSku).isEqualTo("PAPERSAYS001");
                assertThat(restoredLine.billableQuantity).isEqualByComparingTo("0.2");
                assertThat(restoredLine.freightAmount).isEqualByComparingTo("20");
                assertThat(restoredLine.costComponents).hasSize(2);
            });
        });
    }

    @Test
    void getShippingBatchDoesNotExposePartialWholeBatchMetrics() {
        ShippingBatchRecord batch = shippingBatch();
        ShippingBatchSourceRecord complete = new ShippingBatchSourceRecord();
        complete.id = 710001L;
        complete.batchId = 700001L;
        complete.productWeightG = new BigDecimal("500");
        complete.productLengthCm = new BigDecimal("20");
        complete.productWidthCm = new BigDecimal("10");
        complete.productHeightCm = new BigDecimal("5");
        complete.reservedQuantity = 2;
        ShippingBatchSourceRecord incomplete = new ShippingBatchSourceRecord();
        incomplete.id = 710002L;
        incomplete.batchId = 700001L;
        incomplete.productWidthCm = new BigDecimal("10");
        incomplete.productHeightCm = new BigDecimal("5");
        incomplete.reservedQuantity = 1;

        when(mapper.selectShippingBatchById(700001L)).thenReturn(batch);
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(complete, incomplete));
        when(mapper.listShippingSuggestionOptions(700001L)).thenReturn(List.of());
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of());
        when(mapper.listShippingSuggestionLineSources(700001L)).thenReturn(List.of());

        ShippingBatchView view = service.getShippingBatch(access(), "700001");

        assertThat(view.actualWeightKg).isNull();
        assertThat(view.volumeCbm).isNull();
    }

    @Test
    void createShippingBatchFromDispatchPlanAppliesMinimumBillableQuantityBeforeFreightAndWarehouseFees() {
        DispatchPlanRecord plan = dispatchPlan();
        plan.status = "DRAFT";
        plan.handoffGenerationNo = 0;
        DispatchPlanLineRecord line = new DispatchPlanLineRecord();
        line.id = 350001L;
        line.siteCode = "SA";
        line.actualTransportMode = "SEA";
        DispatchPlanLineSourceRecord source = new DispatchPlanLineSourceRecord();
        source.id = 360001L;
        source.dispatchPlanLineId = 350001L;
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 1;
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.siteCode = "SA";
        balance.plannedTransportMode = "SEA";
        balance.reservedQuantity = 1;
        balance.availableQuantity = 0;
        balance.productLengthCm = new BigDecimal("100");
        balance.productWidthCm = new BigDecimal("100");
        balance.productHeightCm = new BigDecimal("10");
        balance.productWeightG = new BigDecimal("1000");

        ForwarderRouteQuoteRecord headhaul = new ForwarderRouteQuoteRecord();
        headhaul.routeCode = "ET-SAU-SEA-FBN-RUH-20260604";
        headhaul.routeName = "易通沙特海运 + 海外仓 + FBN利雅得送仓 20260604";
        headhaul.forwarderCode = "ET";
        headhaul.forwarderName = "易通";
        headhaul.transportMode = "SEA";
        headhaul.cargoCategoryCode = "CAT-A";
        headhaul.cargoCategoryName = "普货";
        headhaul.currency = "RMB";
        headhaul.minUnitPrice = new BigDecimal("100");
        headhaul.billingUnit = "CBM";
        headhaul.minBillableUnit = new BigDecimal("0.2");

        ForwarderRouteCostComponentRecord lastMile = new ForwarderRouteCostComponentRecord();
        lastMile.routeCode = "ET-SAU-SEA-FBN-RUH-20260604";
        lastMile.segmentRole = "LAST_MILE";
        lastMile.sourceTable = "forwarder_quote_base_price";
        lastMile.sourceId = 912086L;
        lastMile.componentType = "LAST_MILE";
        lastMile.componentName = "平台仓送仓利雅得";
        lastMile.serviceCode = "ET-LAST-MILE-20260604";
        lastMile.currency = "RMB";
        lastMile.unitPrice = new BigDecimal("150");
        lastMile.billingUnit = "CBM";
        lastMile.priceStatus = "NORMAL";
        ForwarderRouteCostComponentRecord smallInboundFee = new ForwarderRouteCostComponentRecord();
        smallInboundFee.routeCode = "ET-SAU-SEA-FBN-RUH-20260604";
        smallInboundFee.segmentRole = "WAREHOUSE_PROCESSING";
        smallInboundFee.sourceTable = "forwarder_warehouse_processing_fee";
        smallInboundFee.sourceId = 915015L;
        smallInboundFee.componentType = "WAREHOUSE_INBOUND";
        smallInboundFee.componentName = "散件仓按件上架-小件";
        smallInboundFee.serviceCode = "ET-WH-PROCESS-20260604";
        smallInboundFee.currency = "RMB";
        smallInboundFee.unitPrice = new BigDecimal("0.3");
        smallInboundFee.billingUnit = "PIECE";
        ForwarderRouteCostComponentRecord mediumInboundFee = new ForwarderRouteCostComponentRecord();
        mediumInboundFee.routeCode = "ET-SAU-SEA-FBN-RUH-20260604";
        mediumInboundFee.segmentRole = "WAREHOUSE_PROCESSING";
        mediumInboundFee.sourceTable = "forwarder_warehouse_processing_fee";
        mediumInboundFee.sourceId = 915016L;
        mediumInboundFee.componentType = "WAREHOUSE_INBOUND";
        mediumInboundFee.componentName = "散件仓按件上架-中件";
        mediumInboundFee.serviceCode = "ET-WH-PROCESS-20260604";
        mediumInboundFee.currency = "RMB";
        mediumInboundFee.unitPrice = new BigDecimal("4");
        mediumInboundFee.billingUnit = "PIECE";

        when(mapper.selectDispatchPlanById(340001L)).thenReturn(plan);
        when(mapper.listDispatchPlanLines(340001L)).thenReturn(List.of(line));
        when(mapper.listDispatchLineSources(340001L)).thenReturn(List.of(source));
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(710001L);
        when(mapper.nextShippingSuggestionOptionId()).thenReturn(720001L, 720002L, 720003L, 720004L, 720005L);
        when(mapper.nextShippingSuggestionLineId()).thenReturn(730001L, 730002L, 730003L, 730004L, 730005L);
        when(mapper.nextShippingSuggestionLineSourceId()).thenReturn(740001L, 740002L, 740003L, 740004L, 740005L);
        when(mapper.listActivePurchaseOrderRoutes(List.of("SA"), List.of("SEA")))
                .thenReturn(List.of(activeRoute("ET-SAU-SEA-FBN-RUH-20260604", "易通沙特海运 + 海外仓 + FBN利雅得送仓 20260604", "ET", "易通", "SA", "SEA")));
        when(mapper.listForwarderRouteQuotes(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(List.of(headhaul));
        when(mapper.listForwarderRouteCostComponents(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(lastMile, smallInboundFee, mediumInboundFee));
        when(mapper.updateDispatchPlanReady(340001L, 307L, 1, "WDH-340001-1", 307L)).thenReturn(1);

        ShippingBatchView view = service.createShippingBatchFromDispatchPlan(access(), "340001");

        assertThat(view.options)
                .filteredOn(option -> "FORWARDER_ET".equals(option.optionType))
                .singleElement()
                .satisfies(option -> {
                    assertThat(option.actualWeightKg).isEqualByComparingTo("1.000");
                    assertThat(option.volumeCbm).isEqualByComparingTo("0.1000");
                    assertThat(option.estimatedTotalAmount).isEqualByComparingTo("50.3000");
                    assertThat(option.blockedReasons).contains("未达到目标货代最低计费单位");
                    assertThat(option.costComponents)
                            .extracting(component -> component.componentName)
                            .containsExactly("干线运费", "平台仓送仓利雅得", "散件仓按件上架-小件");
                    assertThat(option.costComponents.get(0).amount).isEqualByComparingTo("20.0000");
                    assertThat(option.costComponents.get(1).amount).isEqualByComparingTo("30.0000");
                    assertThat(option.costComponents.get(2).amount).isEqualByComparingTo("0.3000");
                    assertThat(option.lines).singleElement().satisfies(costLine -> {
                        assertThat(costLine.rawBillableQuantity).isEqualByComparingTo("0.1000");
                        assertThat(costLine.minimumBillableUnit).isEqualByComparingTo("0.2");
                        assertThat(costLine.billableQuantity).isEqualByComparingTo("0.2");
                        assertThat(costLine.billingUnit).isEqualTo("CBM");
                        assertThat(costLine.freightAmount).isEqualByComparingTo("20.0000");
                        assertThat(costLine.minimumNotMet).isTrue();
                        assertThat(costLine.costComponents)
                                .extracting(component -> component.componentType)
                                .containsExactly("HEADHAUL", "LAST_MILE", "WAREHOUSE_INBOUND");
                    });
                });
        verify(mapper).insertShippingSuggestionLine(org.mockito.ArgumentMatchers.argThat(row ->
                row.optionId.equals(720001L)
                        && "ET-SAU-SEA-FBN-RUH-20260604".equals(row.routeCode)
                        && row.estimatedAmount.compareTo(new BigDecimal("50.3000")) == 0
        ), eq(307L));
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
    }

    private DispatchPlanSourceCommand validDispatchPlanSource() {
        DispatchPlanSourceCommand source = new DispatchPlanSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        source.targetSiteCode = "SA";
        source.actualTransportMode = "AIR";
        return source;
    }

    private void stubCreateDispatchPlanWouldOtherwiseSucceed() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        org.mockito.Mockito.lenient().when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        org.mockito.Mockito.lenient().when(mapper.nextDispatchPlanId()).thenReturn(340001L);
        org.mockito.Mockito.lenient().when(mapper.reserveBalance(900001L, 5, 307L)).thenReturn(1);
        org.mockito.Mockito.lenient().when(mapper.nextDispatchLineId()).thenReturn(350001L);
        org.mockito.Mockito.lenient().when(mapper.nextDispatchSourceId()).thenReturn(360001L);
        org.mockito.Mockito.lenient().when(mapper.nextOperationLogId()).thenReturn(390001L);
    }

    private PurchaseOrderAccessRecord purchaseOrder() {
        PurchaseOrderAccessRecord record = new PurchaseOrderAccessRecord();
        record.id = 200001L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.orderNo = "PO-200001";
        record.title = "SGGR-0607";
        record.anchorStoreCodeCache = "STR69486-NSA";
        return record;
    }

    private PurchaseOrderItemRecord purchaseOrderItem() {
        PurchaseOrderItemRecord record = new PurchaseOrderItemRecord();
        record.id = 210001L;
        record.purchaseOrderId = 200001L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGRB115";
        record.skuParent = "SGGR";
        record.titleCache = "测试商品";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.totalQuantity = 2;
        return record;
    }

    private PurchaseOrderItemSiteRecord purchaseOrderItemSite(int quantity) {
        PurchaseOrderItemSiteRecord record = new PurchaseOrderItemSiteRecord();
        record.id = 220002L;
        record.purchaseOrderId = 200001L;
        record.purchaseOrderItemId = 210001L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.siteCode = "SA";
        record.transportMode = "AIR";
        record.quantity = quantity;
        return record;
    }

    private FulfillmentBalanceRecord balance(String quoteStatus, String shippingSubmitStatus) {
        FulfillmentBalanceRecord record = new FulfillmentBalanceRecord();
        record.id = 900001L;
        record.ownerUserId = 307L;
        record.logicalStoreId = 301L;
        record.sourceStoreCode = "STR69486-NSA";
        record.sourceStoreName = "SGGR";
        record.purchaseOrderId = 200001L;
        record.purchaseOrderNo = "PO-200001";
        record.purchaseOrderTitle = "SGGR-0607";
        record.purchaseOrderItemId = 210001L;
        record.purchaseOrderItemSiteId = 220002L;
        record.productMasterId = 310001L;
        record.productVariantId = 320001L;
        record.partnerSku = "SGGRB115";
        record.skuParent = "SGGR";
        record.titleCache = "测试商品";
        record.siteCode = "SA";
        record.plannedTransportMode = "AIR";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.availableQuantity = 20;
        record.specStatus = "READY";
        record.logisticsQuoteStatus = quoteStatus;
        record.logisticsShippingSubmitStatus = shippingSubmitStatus;
        return record;
    }

    private DispatchPlanRecord dispatchPlan() {
        DispatchPlanRecord record = new DispatchPlanRecord();
        record.id = 340001L;
        record.ownerUserId = 307L;
        record.planNo = "DP-340001";
        record.status = "READY_FOR_LOGISTICS";
        record.handoffRequestNo = "WDH-340001-1";
        return record;
    }

    private ShippingBatchRecord shippingBatch() {
        ShippingBatchRecord record = new ShippingBatchRecord();
        record.id = 700001L;
        record.dispatchPlanId = 340001L;
        record.ownerUserId = 307L;
        record.batchNo = "SB-700001";
        record.status = "DRAFT";
        record.skuCount = 1;
        record.totalQuantity = 5;
        return record;
    }

    private ForwarderPurchaseRouteRecord activeRoute(
            String routeCode,
            String routeName,
            String forwarderCode,
            String forwarderName,
            String siteCode,
            String transportMode
    ) {
        ForwarderPurchaseRouteRecord record = new ForwarderPurchaseRouteRecord();
        record.routeCode = routeCode;
        record.routeName = routeName;
        record.forwarderCode = forwarderCode;
        record.forwarderName = forwarderName;
        record.siteCode = siteCode;
        record.transportMode = transportMode;
        return record;
    }

    private ForwarderRouteQuoteRecord routeQuote(
            String routeCode,
            String forwarderCode,
            String forwarderName,
            String cargoCategoryCode,
            String cargoCategoryName,
            String transportMode,
            String unitPrice
    ) {
        ForwarderRouteQuoteRecord record = new ForwarderRouteQuoteRecord();
        record.routeCode = routeCode;
        record.routeName = routeCode;
        record.forwarderCode = forwarderCode;
        record.forwarderName = forwarderName;
        record.transportMode = transportMode;
        record.cargoCategoryCode = cargoCategoryCode;
        record.cargoCategoryName = cargoCategoryName;
        record.currency = "RMB";
        record.minUnitPrice = new BigDecimal(unitPrice);
        record.billingUnit = "CBM";
        return record;
    }

    private OutboundOrderRecord outboundOrder() {
        OutboundOrderRecord record = new OutboundOrderRecord();
        record.id = 800001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.outboundNo = "OB-800001";
        record.status = "DRAFT";
        record.skuCount = 1;
        record.totalQuantity = 5;
        return record;
    }

    private PackingListRecord packingList() {
        PackingListRecord record = new PackingListRecord();
        record.id = 830001L;
        record.outboundOrderId = 800001L;
        record.ownerUserId = 307L;
        record.packingNo = "PK-830001";
        record.status = "DRAFT";
        return record;
    }

    private PackingBoxRecord packingBox(Long boxId, String boxNo, String status, int quantity) {
        PackingBoxRecord record = new PackingBoxRecord();
        record.id = boxId;
        record.packingListId = 830001L;
        record.outboundOrderId = 800001L;
        record.ownerUserId = 307L;
        record.boxNo = boxNo;
        record.status = status;
        record.lengthCm = new BigDecimal("24");
        record.widthCm = new BigDecimal("18");
        record.heightCm = new BigDecimal("12");
        record.grossWeightKg = new BigDecimal("4.1");
        record.quantity = quantity;
        return record;
    }

    private PackingBoxItemRecord packingBoxItem(Long itemId, Long boxId, Long outboundOrderLineId, int quantity) {
        PackingBoxItemRecord record = new PackingBoxItemRecord();
        record.id = itemId;
        record.packingListId = 830001L;
        record.packingBoxId = boxId;
        record.outboundOrderId = 800001L;
        record.outboundOrderLineId = outboundOrderLineId;
        record.ownerUserId = 307L;
        record.productVariantId = 320001L;
        record.partnerSku = "PAPERSAYS088";
        record.siteCode = "SA";
        record.actualTransportMode = "SEA";
        record.quantity = quantity;
        return record;
    }

    private OutboundOrderLineRecord outboundLine(Long lineId, String partnerSku, int quantity) {
        OutboundOrderLineRecord record = new OutboundOrderLineRecord();
        record.id = lineId;
        record.outboundOrderId = 800001L;
        record.batchId = 700001L;
        record.ownerUserId = 307L;
        record.productVariantId = 320001L + (lineId - 812001L);
        record.partnerSku = partnerSku;
        record.siteCode = "SA";
        record.actualTransportMode = "SEA";
        record.fulfillmentType = "WAREHOUSE_RECEIPT";
        record.quantity = quantity;
        record.packedQuantity = 0;
        return record;
    }
}
