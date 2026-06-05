package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Ali1688HistoricalOrderSyncServiceTest {

    @Mock
    private Ali1688HistoricalOrderMapper mapper;

    @Test
    void initialBackfillPersistsOrderHeaderAndItemFacts() {
        FakeAli1688HistoricalOrderProvider provider = new FakeAli1688HistoricalOrderProvider();
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow();
        ArgumentCaptor<Ali1688HistoricalOrderSyncTaskRow> taskCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderSyncTaskRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderRow> orderCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderItemRow> itemCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderLogisticsRow> logisticsCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderLogisticsRow.class);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorization);
        when(mapper.nextSyncTaskId()).thenReturn(92001L);
        when(mapper.nextOrderId()).thenReturn(93001L);
        when(mapper.nextOrderItemId()).thenReturn(94001L);
        when(mapper.nextOrderLogisticsId()).thenReturn(95001L);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(itemRow()));
        when(mapper.listOrderLogistics(307L, List.of(93001L))).thenReturn(List.of(logisticsRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.runInitialBackfill(context);

        verify(mapper).insertSyncTask(taskCaptor.capture());
        verify(mapper).upsertOrder(orderCaptor.capture());
        verify(mapper).upsertOrderItem(itemCaptor.capture());
        verify(mapper).upsertOrderLogistics(logisticsCaptor.capture());
        verify(mapper).markSyncTaskSuccess(92001L, 1, 1, 0, "{\"nextCursor\":null}");
        assertThat(taskCaptor.getValue().getTaskType()).isEqualTo("initial_backfill");
        assertThat(orderCaptor.getValue().getOrderNaturalKey()).isEqualTo("91001:ALI-ORDER-20260525-001");
        assertThat(orderCaptor.getValue().getPaidAt()).isEqualTo("2026-05-25 10:31:20");
        assertThat(orderCaptor.getValue().getBuyerCompanyName()).isEqualTo("松果果电子商务有限公司");
        assertThat(orderCaptor.getValue().getSellerMemberName()).isEqualTo("诚信通源头工厂");
        assertThat(orderCaptor.getValue().getGoodsTotalText()).isEqualTo("¥128.00");
        assertThat(orderCaptor.getValue().getFreightText()).isEqualTo("¥0.00");
        assertThat(orderCaptor.getValue().getPaidAmountText()).isEqualTo("¥128.00");
        assertThat(orderCaptor.getValue().getShipperName()).isEqualTo("商家发货");
        assertThat(orderCaptor.getValue().getReceiverName()).isEqualTo("梁宇");
        assertThat(orderCaptor.getValue().getInitiatorLoginName()).isEqualTo("沁雪冰菏");
        assertThat(itemCaptor.getValue().getItemNaturalKey()).isEqualTo("91001:ALI-ORDER-20260525-001:745612345678:红色:1");
        assertThat(itemCaptor.getValue().getSkuId()).isEqualTo("SKU-745612345678-RED");
        assertThat(itemCaptor.getValue().getProductCode()).isEqualTo("彩虹蛋糕");
        assertThat(itemCaptor.getValue().getModelText()).isEqualTo("仿真花束");
        assertThat(itemCaptor.getValue().getSingleProductCode()).isEqualTo("MX-001");
        assertThat(itemCaptor.getValue().getQuantity()).isEqualTo(10);
        assertThat(itemCaptor.getValue().getUnit()).isEqualTo("套");
        assertThat(logisticsCaptor.getValue().getLogisticsNaturalKey())
                .isEqualTo("91001:ALI-ORDER-20260525-001:94001:ZTO20260525001");
        assertThat(logisticsCaptor.getValue().getLogisticsCompany()).isEqualTo("中通快递(ZTO)");
        assertThat(logisticsCaptor.getValue().getTrackingNo()).isEqualTo("ZTO20260525001");
        assertThat(view.getOrders()).hasSize(1);
        assertThat(view.getSyncSummary().getTotalOrderCount()).isEqualTo(1);
        assertThat(view.getSyncSummary().getTotalItemCount()).isEqualTo(1);
    }

    @Test
    void workbenchDisplaysPersistedOrdersWithItems() {
        LocalDbAli1688HistoricalOrderService service =
                new LocalDbAli1688HistoricalOrderService(mapper, new FakeAli1688HistoricalOrderProvider());
        BusinessAccessContext context = bossContext();

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow());
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(itemRow()));
        when(mapper.listOrderLogistics(307L, List.of(93001L))).thenReturn(List.of(logisticsRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        assertThat(view.getOrders()).hasSize(1);
        Ali1688HistoricalOrderWorkbenchView.OrderRowView order = view.getOrders().get(0);
        assertThat(order.getOrderNo()).isEqualTo("ALI-ORDER-20260525-001");
        assertThat(order.getOrderTime()).isEqualTo("2026-05-25 10:30:00");
        assertThat(order.getSupplierName()).isEqualTo("义乌诚信通源头工厂");
        assertThat(order.getItems()).hasSize(1);
        Ali1688HistoricalOrderWorkbenchView.OrderItemView item = order.getItems().get(0);
        assertThat(item.getOfferId()).isEqualTo("745612345678");
        assertThat(item.getSkuId()).isEqualTo("SKU-745612345678-RED");
        assertThat(item.getProductCode()).isEqualTo("彩虹蛋糕");
        assertThat(item.getUnit()).isEqualTo("套");
        assertThat(item.getLogisticsCompany()).isEqualTo("中通快递(ZTO)");
        assertThat(item.getTrackingNo()).isEqualTo("ZTO20260525001");
    }

    @Test
    void initialBackfillProcessesMultiplePagesAndPersistsCheckpoint() {
        FakeAli1688HistoricalOrderProvider provider = FakeAli1688HistoricalOrderProvider.multiPage();
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = bossContext();

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow());
        when(mapper.selectLatestResumableTask(307L, 91001L)).thenReturn(null);
        when(mapper.nextSyncTaskId()).thenReturn(92001L);
        when(mapper.nextOrderId()).thenReturn(93001L, 93002L);
        when(mapper.nextOrderItemId()).thenReturn(94001L, 94002L);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow(), secondOrderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L, 93002L))).thenReturn(List.of(itemRow(), secondItemRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(2);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(2);

        Ali1688HistoricalOrderWorkbenchView view = service.runInitialBackfill(context);

        verify(mapper).updateSyncTaskCheckpoint(92001L, "{\"nextCursor\":\"page-2\"}", 50, 1, 1, 0);
        verify(mapper).markSyncTaskSuccess(92001L, 2, 2, 0, "{\"nextCursor\":null}");
        assertThat(view.getOrders()).hasSize(2);
        assertThat(view.getSyncSummary().getTotalOrderCount()).isEqualTo(2);
        assertThat(view.getSyncSummary().getTotalItemCount()).isEqualTo(2);
    }

    @Test
    void retryableFailureResumesFromCheckpointWithoutCreatingNewTask() {
        FakeAli1688HistoricalOrderProvider provider = FakeAli1688HistoricalOrderProvider.multiPage();
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderSyncTaskRow resumableTask = syncTask("failed", "{\"nextCursor\":\"page-2\"}");
        resumableTask.setProcessedCount(1);
        resumableTask.setImportedCount(1);
        resumableTask.setFailedCount(0);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow());
        when(mapper.selectLatestResumableTask(307L, 91001L)).thenReturn(resumableTask);
        when(mapper.nextOrderId()).thenReturn(93002L);
        when(mapper.nextOrderItemId()).thenReturn(94002L);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow(), secondOrderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L, 93002L))).thenReturn(List.of(itemRow(), secondItemRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(2);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(2);

        service.runInitialBackfill(context);

        verify(mapper, never()).insertSyncTask(any());
        verify(mapper).markSyncTaskSuccess(92001L, 2, 2, 0, "{\"nextCursor\":null}");
    }

    @Test
    void detailFailureMarksPartialSuccessAndKeepsImportedOrdersVisible() {
        FakeAli1688HistoricalOrderProvider provider = FakeAli1688HistoricalOrderProvider.partialDetailFailure();
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = bossContext();

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow());
        when(mapper.selectLatestResumableTask(307L, 91001L)).thenReturn(null);
        when(mapper.nextSyncTaskId()).thenReturn(92001L);
        when(mapper.nextOrderId()).thenReturn(93001L);
        when(mapper.nextOrderItemId()).thenReturn(94001L);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(itemRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.runInitialBackfill(context);

        verify(mapper).markSyncTaskPartialSuccess(
                92001L,
                1,
                1,
                1,
                "missing_fields",
                "部分订单详情字段未返回。",
                "{\"nextCursor\":null}",
                true,
                false
        );
        assertThat(view.getOrders()).hasSize(1);
    }

    @Test
    void providerFailureWithoutImportedOrdersMarksFailedWithClassifiedFlags() {
        FakeAli1688HistoricalOrderProvider provider =
                FakeAli1688HistoricalOrderProvider.failure("rate_limited", "1688 provider rate limited.");
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = bossContext();

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow());
        when(mapper.selectLatestResumableTask(307L, 91001L)).thenReturn(null);
        when(mapper.nextSyncTaskId()).thenReturn(92001L);
        when(mapper.selectLatestTask(307L, 91001L)).thenReturn(failedTask("rate_limited", true, false));
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(0);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(0);

        Ali1688HistoricalOrderWorkbenchView view = service.runInitialBackfill(context);

        verify(mapper).markSyncTaskFailed(
                92001L,
                0,
                0,
                1,
                "rate_limited",
                "1688 provider rate limited.",
                "{\"nextCursor\":null}",
                true,
                false
        );
        verify(mapper, never()).markSyncTaskPartialSuccess(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean()
        );
        assertThat(view.getSyncSummary().getFailureCode()).isEqualTo("rate_limited");
        assertThat(view.getSyncSummary().isRetryable()).isTrue();
        assertThat(view.getSyncSummary().isRequiresManualAction()).isFalse();
    }

    @Test
    void manualRefreshCreatesDistinctTaskAndUpdatesExistingOrderFacts() {
        FakeAli1688HistoricalOrderProvider provider = FakeAli1688HistoricalOrderProvider.incrementalUpdate();
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = operationsManagerContext();
        ArgumentCaptor<Ali1688HistoricalOrderSyncTaskRow> taskCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderSyncTaskRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderRow> orderCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderRow.class);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow());
        when(mapper.nextSyncTaskId()).thenReturn(92002L);
        when(mapper.nextOrderId()).thenReturn(93001L);
        when(mapper.nextOrderItemId()).thenReturn(94001L);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(updatedOrderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(itemRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.runManualRefresh(context);

        verify(mapper).insertSyncTask(taskCaptor.capture());
        verify(mapper).upsertOrder(orderCaptor.capture());
        verify(mapper).markSyncTaskSuccess(92002L, 1, 1, 0, "{\"nextCursor\":null}");
        assertThat(taskCaptor.getValue().getTaskType()).isEqualTo("manual_refresh");
        assertThat(taskCaptor.getValue().getCreatedBy()).isEqualTo(408L);
        assertThat(orderCaptor.getValue().getOrderStatus()).isEqualTo("已完成");
        assertThat(orderCaptor.getValue().getLogisticsStatus()).isEqualTo("已签收");
        assertThat(orderCaptor.getValue().getAmountText()).isEqualTo("¥138.00");
        assertThat(view.getSyncSummary().getLatestTaskStatus()).isEqualTo("success");
    }

    @Test
    void manualRefreshForLocalExcelImportDoesNotWriteFakeProviderOrders() {
        FakeAli1688HistoricalOrderProvider provider = FakeAli1688HistoricalOrderProvider.incrementalUpdate();
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper, provider);
        BusinessAccessContext context = operationsManagerContext();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow();
        authorization.setProviderCode("ALI1688_EXCEL_LOCAL");

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorization);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow()));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(itemRow()));
        when(mapper.listOrderLogistics(307L, List.of(93001L))).thenReturn(List.of(logisticsRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(3316);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(5624);

        Ali1688HistoricalOrderWorkbenchView view = service.runManualRefresh(context);

        verify(mapper, never()).insertSyncTask(any());
        verify(mapper, never()).upsertOrder(any());
        verify(mapper, never()).upsertOrderItem(any());
        verify(mapper, never()).upsertOrderLogistics(any());
        assertThat(view.getSyncSummary().getTotalOrderCount()).isEqualTo(3316);
        assertThat(view.getSyncSummary().getTotalItemCount()).isEqualTo(5624);
    }

    @Test
    void syncServiceHasNoProcurementDownstreamWriteCollaborators() {
        Set<String> collaboratorTypes = Arrays.stream(LocalDbAli1688HistoricalOrderService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(collaboratorTypes)
                .doesNotContain(
                        "ProcurementMapper",
                        "ProcurementRequirementConfirmationMapper",
                        "ProcurementAutoInquiryMapper",
                        "PaymentPlanMapper",
                        "PurchaseOrderMapper"
                );
    }

    private BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleName("老板")
                .roleLevel(1)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private BusinessAccessContext operationsManagerContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(408L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName("运营管理")
                .roleLevel(2)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private Ali1688HistoricalOrderAuthorizationRow authorizationRow() {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91001L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_DEV");
        row.setAccountLabel("1688 开发授权账号");
        row.setStatus("authorized");
        row.setScopeSummary("读取 1688 历史订单，不会付款或创建订单。");
        return row;
    }

    private Ali1688HistoricalOrderSyncTaskRow syncTask(String status, String checkpointJson) {
        Ali1688HistoricalOrderSyncTaskRow row = new Ali1688HistoricalOrderSyncTaskRow();
        row.setId(92001L);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(91001L);
        row.setTaskType("initial_backfill");
        row.setStatus(status);
        row.setCheckpointJson(checkpointJson);
        return row;
    }

    private Ali1688HistoricalOrderSyncTaskRow failedTask(
            String failureCode,
            boolean retryable,
            boolean requiresManualAction
    ) {
        Ali1688HistoricalOrderSyncTaskRow row = syncTask("failed", "{\"nextCursor\":null}");
        row.setFailureCode(failureCode);
        row.setFailureMessage("1688 provider rate limited.");
        row.setFailedCount(1);
        row.setRetryable(retryable);
        row.setRequiresManualAction(requiresManualAction);
        return row;
    }

    private Ali1688HistoricalOrderRow orderRow() {
        Ali1688HistoricalOrderRow row = new Ali1688HistoricalOrderRow();
        row.setId(93001L);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(91001L);
        row.setProviderOrderNo("ALI-ORDER-20260525-001");
        row.setOrderTime("2026-05-25 10:30:00");
        row.setPaidAt("2026-05-25 10:31:20");
        row.setBuyerCompanyName("松果果电子商务有限公司");
        row.setBuyerMemberName("沁雪冰菏");
        row.setSupplierName("义乌诚信通源头工厂");
        row.setSellerMemberName("诚信通源头工厂");
        row.setGoodsTotalText("¥128.00");
        row.setFreightText("¥0.00");
        row.setAdjustmentText("¥0.00");
        row.setPaidAmountText("¥128.00");
        row.setAmountText("¥128.00");
        row.setAmountValue(new BigDecimal("128.00"));
        row.setCurrency("CNY");
        row.setOrderStatus("已付款");
        row.setLogisticsStatus("待发货");
        row.setShipperName("商家发货");
        row.setReceiverName("梁宇");
        row.setInitiatorLoginName("沁雪冰菏");
        row.setOriginalUrl("https://trade.1688.com/order/new_step_order_detail.htm?orderId=ALI-ORDER-20260525-001");
        return row;
    }

    private Ali1688HistoricalOrderRow secondOrderRow() {
        Ali1688HistoricalOrderRow row = new Ali1688HistoricalOrderRow();
        row.setId(93002L);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(91001L);
        row.setProviderOrderNo("ALI-ORDER-20260525-002");
        row.setOrderTime("2026-05-24 16:20:00");
        row.setSupplierName("深圳跨境源头供应商");
        row.setAmountText("¥88.00");
        row.setAmountValue(new BigDecimal("88.00"));
        row.setCurrency("CNY");
        row.setOrderStatus("已付款");
        row.setLogisticsStatus("已发货");
        row.setOriginalUrl("https://trade.1688.com/order/new_step_order_detail.htm?orderId=ALI-ORDER-20260525-002");
        return row;
    }

    private Ali1688HistoricalOrderRow updatedOrderRow() {
        Ali1688HistoricalOrderRow row = orderRow();
        row.setOrderStatus("已完成");
        row.setLogisticsStatus("已签收");
        row.setAmountText("¥138.00");
        return row;
    }

    private Ali1688HistoricalOrderItemRow itemRow() {
        Ali1688HistoricalOrderItemRow row = new Ali1688HistoricalOrderItemRow();
        row.setId(94001L);
        row.setOrderId(93001L);
        row.setOfferId("745612345678");
        row.setSkuId("SKU-745612345678-RED");
        row.setTitle("仿真罂粟花束 6 支装 家居装饰");
        row.setSkuText("红色");
        row.setModelText("仿真花束");
        row.setProductCode("彩虹蛋糕");
        row.setSingleProductCode("MX-001");
        row.setQuantity(10);
        row.setUnit("套");
        row.setUnitPriceText("¥12.80");
        row.setAmountText("¥128.00");
        return row;
    }

    private Ali1688HistoricalOrderLogisticsRow logisticsRow() {
        Ali1688HistoricalOrderLogisticsRow row = new Ali1688HistoricalOrderLogisticsRow();
        row.setId(95001L);
        row.setOrderId(93001L);
        row.setItemId(94001L);
        row.setLogisticsNaturalKey("91001:ALI-ORDER-20260525-001:94001:ZTO20260525001");
        row.setLogisticsCompany("中通快递(ZTO)");
        row.setTrackingNo("ZTO20260525001");
        return row;
    }

    private Ali1688HistoricalOrderItemRow secondItemRow() {
        Ali1688HistoricalOrderItemRow row = new Ali1688HistoricalOrderItemRow();
        row.setId(94002L);
        row.setOrderId(93002L);
        row.setOfferId("745612345679");
        row.setTitle("跨境收纳盒 3 件套");
        row.setSkuText("白色");
        row.setQuantity(4);
        row.setUnitPriceText("¥22.00");
        row.setAmountText("¥88.00");
        return row;
    }
}
