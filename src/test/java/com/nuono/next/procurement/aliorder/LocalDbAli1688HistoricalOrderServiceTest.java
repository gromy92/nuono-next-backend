package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LocalDbAli1688HistoricalOrderServiceTest {

    @Mock
    private Ali1688HistoricalOrderMapper mapper;

    @Test
    void buildWorkbenchShowsPersistedAuthorization() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow row = authorizationRow(91001L);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(row);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("authorized");
        assertThat(view.getAuthorization().getAuthorizationId()).isEqualTo(91001L);
        assertThat(view.getAuthorization().getAccountLabel()).isEqualTo("1688 开发授权账号");
        assertThat(view.getAuthorization().getScopeSummary()).isEqualTo("读取 1688 历史订单，不会付款或创建订单。");
        assertThat(view.getRoleCapabilities().isCanAuthorize()).isTrue();
        assertThat(view.getRoleCapabilities().isCanTriggerSync()).isTrue();
    }

    @Test
    void buildWorkbenchExposesProductLineAssignmentState() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow(91001L);
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);
        Ali1688HistoricalOrderItemRow unassigned = itemRow(94010L, 93001L, "未分配货品", 10);
        Ali1688HistoricalOrderItemRow partiallyAssigned = itemRow(94011L, 93001L, "部分分配货品", 10);
        Ali1688HistoricalOrderItemRow fullyAssigned = itemRow(94012L, 93001L, "已分配货品", 10);
        Ali1688HistoricalOrderItemRow missingQuantity = itemRow(94013L, 93001L, "数量未返回货品", null);
        Ali1688HistoricalOrderItemAssignmentSummaryRow partialSummary =
                assignmentSummary(94011L, 4);
        Ali1688HistoricalOrderItemAssignmentSummaryRow fullSummary =
                assignmentSummary(94012L, 10);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorization);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order));
        when(mapper.listOrderItems(307L, List.of(93001L)))
                .thenReturn(List.of(unassigned, partiallyAssigned, fullyAssigned, missingQuantity));
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94010L, 94011L, 94012L, 94013L)))
                .thenReturn(List.of(partialSummary, fullSummary));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(4);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        List<Ali1688HistoricalOrderWorkbenchView.OrderItemView> items = view.getOrders()
                .stream()
                .flatMap(orderView -> orderView.getItems().stream())
                .collect(java.util.stream.Collectors.toList());
        assertThat(items).extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getAssignmentStatusLabel)
                .containsExactly("未分配", "部分分配", "已分配", "数量未返回");
        assertThat(items).extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getAssignmentStatus)
                .containsExactly("unassigned", "partially_assigned", "assigned", "quantity_missing");
        assertThat(items).extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getOriginalQuantity)
                .containsExactly(10, 10, 10, null);
        assertThat(items).extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getAssignedQuantity)
                .containsExactly(0, 4, 10, 0);
        assertThat(items).extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getRemainingQuantity)
                .containsExactly(10, 6, 0, null);
    }

    @Test
    void buildWorkbenchWithoutStoreScopeReadsAllOwnerVisibleAuthorizations() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow currentAuthorization = authorizationRow(91002L);
        Ali1688HistoricalOrderAuthorizationRow primaryAuthorization = authorizationRow(91001L);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(currentAuthorization);
        when(mapper.listVisibleAuthorizationIds(307L, null, null)).thenReturn(List.of(91001L, 91002L));
        when(mapper.selectAuthorizationById(307L, 91001L)).thenReturn(primaryAuthorization);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L, 91002L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L, 91002L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        when(mapper.countOrderItems(307L, List.of(91001L, 91002L))).thenReturn(0);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        assertThat(view.getAuthorization().getAuthorizationId()).isEqualTo(91001L);
        assertThat(view.getStoreScope().getStatus()).isEqualTo("owner_scope");
        assertThat(view.getStoreScope().getMatchedAuthorizationIds()).containsExactly("91001", "91002");
    }

    @Test
    void buildWorkbenchExposesAssignmentBreakdownForSplitProductLine() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow(91001L);
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);
        order.setGoodsTotalText("¥100.00");
        order.setFreightText("¥10.00");
        order.setPaidAmountText("¥110.00");
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "拆分货品", 10);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorization);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(item));
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(assignmentSummary(94011L, 7, "AE 3 / SA 4")));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        assertThat(view.getOrders()).hasSize(3);
        assertThat(view.getOrders()).extracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getGoodsTotalText)
                .containsExactly("¥30.00", "¥40.00", "¥30.00");
        assertThat(view.getOrders()).extracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getFreightText)
                .containsExactly("¥3.00", "¥4.00", "¥3.00");
        assertThat(view.getOrders()).extracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getPaidAmountText)
                .containsExactly("¥33.00", "¥44.00", "¥33.00");
        assertThat(view.getOrders()).flatExtracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getItems)
                .extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getQuantity)
                .containsExactly(3, 4, 3);
        assertThat(view.getOrders()).flatExtracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getItems)
                .extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getAmountText)
                .containsExactly("¥30.00", "¥40.00", "¥30.00");
        assertThat(view.getOrders()).flatExtracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getItems)
                .extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getAssignmentBreakdownText)
                .containsExactly("AE 3", "SA 4", null);
        assertThat(view.getOrders()).flatExtracting(Ali1688HistoricalOrderWorkbenchView.OrderRowView::getItems)
                .extracting(Ali1688HistoricalOrderWorkbenchView.OrderItemView::getAssignmentStatusLabel)
                .containsExactly("已分配", "已分配", "未分配");
    }

    @Test
    void buildWorkbenchExposesConsumableAssignmentBreakdownAsAssignedLine() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow(91001L);
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "耗材货品", 10);
        Ali1688HistoricalOrderItemAssignmentSummaryRow summary =
                assignmentSummary(94011L, 10, "耗材 10");
        summary.setConsumableAssignmentCount(1);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorization);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(item));
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(summary));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        Ali1688HistoricalOrderWorkbenchView.OrderItemView itemView =
                view.getOrders().get(0).getItems().get(0);
        assertThat(itemView.getAssignmentStatus()).isEqualTo("assigned");
        assertThat(itemView.getAssignmentStatusLabel()).isEqualTo("已分配");
        assertThat(itemView.getAssignedQuantity()).isEqualTo(10);
        assertThat(itemView.getRemainingQuantity()).isEqualTo(0);
        assertThat(itemView.getAssignmentBreakdownText()).isEqualTo("耗材 10");
    }

    @Test
    void buildWorkbenchExposesProductLinkOnAssignedStoreLine() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAuthorizationRow authorization = authorizationRow(91001L);
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "已关联商品货品", 4);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkRow link = productLinkRow(100001L, assignment);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorization);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91001L));
        when(mapper.selectAuthorizationById(307L, 91001L)).thenReturn(authorization);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(item));
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(assignmentSummary(94011L, 4, "PRJ108065 AE 4")));
        when(mapper.listActiveOrderItemAssignments(307L, List.of(94011L))).thenReturn(List.of(assignment));
        when(mapper.listActiveOrderItemProductLinks(307L, List.of(99001L))).thenReturn(List.of(link));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(
                context,
                Ali1688HistoricalOrderQuery.fromRequest(null, null, null, null, null, "PRJ108065", "AE", 1, 20)
        );

        Ali1688HistoricalOrderWorkbenchView.OrderItemView itemView =
                view.getOrders().get(0).getItems().get(0);
        assertThat(itemView.getAssignmentId()).isEqualTo(99001L);
        assertThat(itemView.getAssignmentTargetStoreCode()).isEqualTo("PRJ108065");
        assertThat(itemView.getAssignmentTargetSiteCode()).isEqualTo("AE");
        assertThat(itemView.getAssignmentBreakdownText()).isEqualTo("PRJ108065 AE 4");
        assertThat(itemView.getProductLink().getStatus()).isEqualTo("linked");
        assertThat(itemView.getProductLink().getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(itemView.getProductLink().getDisplayText()).isEqualTo("已关联: CANMAN-AE-SKU-001");
    }

    @Test
    void buildWorkbenchAppliesFiltersSearchAndPagination() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderAuthorizationRow row = authorizationRow(91001L);
        Ali1688HistoricalOrderQuery query = Ali1688HistoricalOrderQuery.fromRequest(
                "2026-05-01 00:00:00",
                "2026-05-25 23:59:59",
                "已付款",
                "义乌",
                "745612345678",
                2,
                10
        );
        ArgumentCaptor<Ali1688HistoricalOrderQuery> queryCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderQuery.class);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(row);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), queryCaptor.capture()))
                .thenReturn(List.of());
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(0);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(0);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context, query);

        Ali1688HistoricalOrderQuery normalized = queryCaptor.getValue();
        assertThat(normalized.getPlacedTimeFrom()).isEqualTo("2026-05-01 00:00:00");
        assertThat(normalized.getPlacedTimeTo()).isEqualTo("2026-05-25 23:59:59");
        assertThat(normalized.getOrderStatus()).isEqualTo("已付款");
        assertThat(normalized.getSupplierKeyword()).isEqualTo("义乌");
        assertThat(normalized.getKeyword()).isEqualTo("745612345678");
        normalized.setAssignmentState("unassigned");
        normalized.setAssignmentTargetStoreCode("PRJ108065");
        normalized.setAssignmentTargetSiteCode("AE");
        assertThat(normalized.getAssignmentState()).isEqualTo("unassigned");
        assertThat(normalized.getAssignmentTargetStoreCode()).isEqualTo("PRJ108065");
        assertThat(normalized.getAssignmentTargetSiteCode()).isEqualTo("AE");
        assertThat(normalized.getPage()).isEqualTo(2);
        assertThat(normalized.getPageSize()).isEqualTo(10);
        assertThat(normalized.getOffset()).isEqualTo(10);
        assertThat(view.getPagination().getPage()).isEqualTo(2);
        assertThat(view.getPagination().getPageSize()).isEqualTo(10);
    }

    @Test
    void buildWorkbenchFiltersCurrentStoreThroughAli1688AuthorizationBindings() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065", "PRJ245027");
        Ali1688HistoricalOrderAuthorizationRow row = authorizationRow(91001L);
        Ali1688HistoricalOrderQuery query = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                "PRJ108065",
                "AE",
                1,
                20
        );

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(row);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91001L, 91003L));
        when(mapper.selectAuthorizationById(307L, 91001L)).thenReturn(row);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L, 91003L)), org.mockito.ArgumentMatchers.eq(query)))
                .thenReturn(List.of(orderRow(93001L, 91003L)));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(missingItemRow()));
        when(mapper.countOrders(307L, List.of(91001L, 91003L), query)).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L, 91003L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context, query);

        assertThat(view.getStoreScope().getStatus()).isEqualTo("bound");
        assertThat(view.getStoreScope().getStoreCode()).isEqualTo("PRJ108065");
        assertThat(view.getStoreScope().getSiteCode()).isEqualTo("AE");
        assertThat(view.getStoreScope().getMatchedAuthorizationIds()).containsExactly("91001", "91003");
        assertThat(view.getOrders()).hasSize(1);
        assertThat(view.getOrders().get(0).getId()).isEqualTo("93001");
    }

    @Test
    void buildWorkbenchRejectsCurrentStoreOutsideSessionScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ245027");
        Ali1688HistoricalOrderQuery query = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                "PRJ108065",
                "AE",
                1,
                20
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.buildWorkbench(context, query))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void buildWorkbenchExposesMissingFieldsWithoutLeakingSensitiveListValues() throws Exception {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderRow order = missingAndSensitiveOrderRow();
        Ali1688HistoricalOrderItemRow item = missingItemRow();

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow(91001L));
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(item));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        Ali1688HistoricalOrderWorkbenchView.OrderRowView orderView = view.getOrders().get(0);
        assertThat(orderView.getMissingFields())
                .containsExactlyInAnyOrder("supplier", "amount", "logistics", "sourceLink");
        assertThat(orderView.getItems().get(0).getMissingFields())
                .containsExactlyInAnyOrder("sku", "image");
        assertThat(orderView.getAmountText()).isNull();

        String serializedListRow = new ObjectMapper().writeValueAsString(orderView);
        assertThat(serializedListRow)
                .doesNotContain("13800138000")
                .doesNotContain("西湖区文三路")
                .doesNotContain("周五前发货")
                .doesNotContain("supplier-contact");
    }

    @Test
    void buildWorkbenchDoesNotExposeReceiverPhonesOrRawSnapshotsInListPayload() throws Exception {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);
        order.setReceiverTelephone("057100000000");
        order.setReceiverMobile("13800000000");
        order.setRawSnapshotJson("{\"receiverMobile\":\"13800000000\",\"receiverAddress\":\"浙江省杭州市脱敏地址\"}");

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow(91001L));
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(order));
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(missingItemRow()));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91001L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91001L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context);

        String serialized = new ObjectMapper().writeValueAsString(view);
        assertThat(serialized)
                .doesNotContain("057100000000")
                .doesNotContain("13800000000")
                .doesNotContain("rawSnapshotJson")
                .doesNotContain("浙江省杭州市脱敏地址");
    }

    @Test
    void orderDetailAppliesSafeSensitiveFieldPolicy() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = operatorContext();
        Ali1688HistoricalOrderRow order = missingAndSensitiveOrderRow();

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow(91001L));
        when(mapper.selectOrderById(307L, List.of(91001L), 93001L)).thenReturn(order);
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(missingItemRow()));

        Ali1688HistoricalOrderWorkbenchView.OrderDetailView detail = service.orderDetail(context, 93001L);

        assertThat(detail.getMissingFields()).contains("supplier", "amount", "logistics", "sourceLink");
        assertThat(detail.getSensitiveFields().getRedactionLevel()).isEqualTo("hidden");
        assertThat(detail.getSensitiveFields().getReceiverPhone()).isEqualTo("已隐藏");
        assertThat(detail.getItems().get(0).getMissingFields()).contains("sku", "image");
    }

    @Test
    void orderDetailIncludesProductLineAssignmentState() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = operatorContext();
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "部分分配货品", 10);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow(91001L));
        when(mapper.selectOrderById(307L, List.of(91001L), 93001L)).thenReturn(order);
        when(mapper.listOrderItems(307L, List.of(93001L))).thenReturn(List.of(item));
        org.mockito.Mockito.lenient()
                .when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(assignmentSummary(94011L, 4)));

        Ali1688HistoricalOrderWorkbenchView.OrderDetailView detail = service.orderDetail(context, 93001L);

        Ali1688HistoricalOrderWorkbenchView.OrderItemView itemView = detail.getItems().get(0);
        assertThat(itemView.getAssignmentStatus()).isEqualTo("partially_assigned");
        assertThat(itemView.getAssignmentStatusLabel()).isEqualTo("部分分配");
        assertThat(itemView.getOriginalQuantity()).isEqualTo(10);
        assertThat(itemView.getAssignedQuantity()).isEqualTo(4);
        assertThat(itemView.getRemainingQuantity()).isEqualTo(6);
    }

    @Test
    void assignProductLinesPersistsSessionScopedAssignmentFacts() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                assignmentRequest("PRJ108065", "AE", 94011L, 4);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "待分配货品", 10);
        item.setAuthorizationId(91008L);
        ArgumentCaptor<Ali1688HistoricalOrderItemAssignmentRow> assignmentCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemAssignmentRow.class);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L))).thenReturn(List.of());
        when(mapper.nextOrderItemAssignmentId()).thenReturn(99001L);

        Ali1688HistoricalOrderAssignmentView.AssignResult result =
                service.assignProductLines(context, request);

        verify(mapper).insertOrderItemAssignment(assignmentCaptor.capture());
        Ali1688HistoricalOrderItemAssignmentRow inserted = assignmentCaptor.getValue();
        assertThat(inserted.getId()).isEqualTo(99001L);
        assertThat(inserted.getOwnerUserId()).isEqualTo(307L);
        assertThat(inserted.getAuthorizationId()).isEqualTo(91008L);
        assertThat(inserted.getOrderId()).isEqualTo(93001L);
        assertThat(inserted.getItemId()).isEqualTo(94011L);
        assertThat(inserted.getTargetType()).isEqualTo("STORE_SITE");
        assertThat(inserted.getTargetStoreCode()).isEqualTo("PRJ108065");
        assertThat(inserted.getTargetSiteCode()).isEqualTo("AE");
        assertThat(inserted.getAssignedQuantity()).isEqualTo(4);
        assertThat(inserted.getCreatedBy()).isEqualTo(307L);
        assertThat(inserted.getUpdatedBy()).isEqualTo(307L);
        assertThat(result.getAssignedLineCount()).isEqualTo(1);
        assertThat(result.getAssignedQuantity()).isEqualTo(4);
    }

    @Test
    void assignProductLineBatchesPersistsAllTargetsInOneServiceCall() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065", "PRJ245027");
        Ali1688HistoricalOrderAssignmentView.AssignBatchRequest request =
                new Ali1688HistoricalOrderAssignmentView.AssignBatchRequest();
        request.setAssignments(List.of(
                assignmentRequest("PRJ108065", "AE", 94011L, 2),
                assignmentRequest("PRJ245027", "SA", 94012L, 3)
        ));
        Ali1688HistoricalOrderItemRow firstItem = itemRow(94011L, 93001L, "待分配货品 A", 10);
        firstItem.setAuthorizationId(91008L);
        Ali1688HistoricalOrderItemRow secondItem = itemRow(94012L, 93001L, "待分配货品 B", 10);
        secondItem.setAuthorizationId(91008L);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(firstItem);
        when(mapper.selectOrderItemForAssignment(307L, 94012L)).thenReturn(secondItem);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L))).thenReturn(List.of());
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94012L))).thenReturn(List.of());
        when(mapper.nextOrderItemAssignmentId()).thenReturn(99001L, 99002L);

        Ali1688HistoricalOrderAssignmentView.AssignResult result =
                service.assignProductLineBatches(context, request);

        verify(mapper, times(2)).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
        assertThat(result.getAssignedLineCount()).isEqualTo(2);
        assertThat(result.getAssignedQuantity()).isEqualTo(5);
    }

    @Test
    void assignProductLinesPersistsConsumableAsWholeLineAssignmentFact() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                consumableAssignmentRequest(94011L);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "耗材货品", 10);
        item.setAuthorizationId(91008L);
        ArgumentCaptor<Ali1688HistoricalOrderItemAssignmentRow> assignmentCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemAssignmentRow.class);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L))).thenReturn(List.of());
        when(mapper.nextOrderItemAssignmentId()).thenReturn(99002L);

        Ali1688HistoricalOrderAssignmentView.AssignResult result =
                service.assignProductLines(context, request);

        verify(mapper).insertOrderItemAssignment(assignmentCaptor.capture());
        Ali1688HistoricalOrderItemAssignmentRow inserted = assignmentCaptor.getValue();
        assertThat(inserted.getTargetType()).isEqualTo("CONSUMABLE");
        assertThat(inserted.getTargetStoreCode()).isNull();
        assertThat(inserted.getTargetSiteCode()).isNull();
        assertThat(inserted.getAssignedQuantity()).isEqualTo(10);
        assertThat(inserted.getRemark()).contains("耗材");
        assertThat(result.getAssignedLineCount()).isEqualTo(1);
        assertThat(result.getAssignedQuantity()).isEqualTo(10);
    }

    @Test
    void assignProductLinesPersistsDiscontinuedAsDiscontinuedStoreFact() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                discontinuedAssignmentRequest("PRJ108065", "AE", 94011L, 4);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "下架商品货品", 10);
        item.setAuthorizationId(91008L);
        ArgumentCaptor<Ali1688HistoricalOrderItemAssignmentRow> assignmentCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemAssignmentRow.class);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L))).thenReturn(List.of());
        when(mapper.nextOrderItemAssignmentId()).thenReturn(99003L);

        Ali1688HistoricalOrderAssignmentView.AssignResult result =
                service.assignProductLines(context, request);

        verify(mapper).insertOrderItemAssignment(assignmentCaptor.capture());
        Ali1688HistoricalOrderItemAssignmentRow inserted = assignmentCaptor.getValue();
        assertThat(inserted.getTargetType()).isEqualTo("DISCONTINUED");
        assertThat(inserted.getTargetStoreCode()).isEqualTo("PRJ108065");
        assertThat(inserted.getTargetSiteCode()).isEqualTo("AE");
        assertThat(inserted.getAssignedQuantity()).isEqualTo(4);
        assertThat(inserted.getRemark()).contains("下架");
        assertThat(result.getAssignedLineCount()).isEqualTo(1);
        assertThat(result.getAssignedQuantity()).isEqualTo(4);
    }

    @Test
    void assignProductLinesRejectsQuantityAboveRemaining() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                assignmentRequest("PRJ108065", "AE", 94011L, 3);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "待分配货品", 10);
        item.setAuthorizationId(91008L);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(assignmentSummary(94011L, 8)));

        assertThatThrownBy(() -> service.assignProductLines(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
    }

    @Test
    void assignProductLinesRejectsStoreTargetWhenLineAlreadyHasConsumableAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                assignmentRequest("PRJ108065", "AE", 94011L, 2);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "已有耗材归属货品", 10);
        Ali1688HistoricalOrderItemAssignmentSummaryRow summary = assignmentSummary(94011L, 3);
        summary.setConsumableAssignmentCount(1);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(summary));

        assertThatThrownBy(() -> service.assignProductLines(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
    }

    @Test
    void assignProductLinesRejectsConsumableTargetWhenLineAlreadyHasStoreAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                consumableAssignmentRequest(94011L);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "已有店铺归属货品", 10);
        Ali1688HistoricalOrderItemAssignmentSummaryRow summary = assignmentSummary(94011L, 3);
        summary.setStoreSiteAssignmentCount(1);

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94011L)))
                .thenReturn(List.of(summary));

        assertThatThrownBy(() -> service.assignProductLines(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
    }

    @Test
    void assignProductLinesRejectsConsumableTargetWhenQuantityIsMissing() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                consumableAssignmentRequest(94013L);
        Ali1688HistoricalOrderItemRow item = itemRow(94013L, 93001L, "数量未返回耗材货品", null);

        when(mapper.selectOrderItemForAssignment(307L, 94013L)).thenReturn(item);
        when(mapper.listOrderItemAssignmentSummaries(307L, List.of(94013L))).thenReturn(List.of());

        assertThatThrownBy(() -> service.assignProductLines(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
    }

    @Test
    void assignProductLinesRejectsTargetStoreOutsideSessionScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ245027");
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                assignmentRequest("PRJ108065", "AE", 94011L, 3);

        assertThatThrownBy(() -> service.assignProductLines(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(mapper, never()).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
    }

    @Test
    void listProductLineAssignmentsReturnsAuditRecords() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "已分配货品", 10);
        Ali1688HistoricalOrderItemAssignmentRow assignment = assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        assignment.setCreatedBy(307L);
        assignment.setUpdatedBy(409L);
        assignment.setCreatedAt("2026-05-26 15:30:00");
        assignment.setUpdatedAt("2026-05-26 15:35:00");

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignments(307L, 94011L)).thenReturn(List.of(assignment));

        List<Ali1688HistoricalOrderAssignmentView.RecordView> records =
                service.listProductLineAssignments(context, 94011L);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getAssignmentId()).isEqualTo(99001L);
        assertThat(records.get(0).getTargetStoreCode()).isEqualTo("PRJ108065");
        assertThat(records.get(0).getTargetSiteCode()).isEqualTo("AE");
        assertThat(records.get(0).getAssignedQuantity()).isEqualTo(4);
        assertThat(records.get(0).getStatus()).isEqualTo("active");
        assertThat(records.get(0).getCreatedBy()).isEqualTo(307L);
        assertThat(records.get(0).getUpdatedBy()).isEqualTo(409L);
        assertThat(records.get(0).getCreatedAt()).isEqualTo("2026-05-26 15:30:00");
    }

    @Test
    void listProductLineAssignmentsReturnsConsumableAuditRecords() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = operatorContext();
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "耗材货品", 10);
        Ali1688HistoricalOrderItemAssignmentRow assignment = assignmentRow(99002L, 94011L, null, null, 10);
        assignment.setTargetType("CONSUMABLE");

        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.listOrderItemAssignments(307L, 94011L)).thenReturn(List.of(assignment));

        List<Ali1688HistoricalOrderAssignmentView.RecordView> records =
                service.listProductLineAssignments(context, 94011L);

        assertThat(records).hasSize(1);
        assertThat(records.get(0).getTargetType()).isEqualTo("CONSUMABLE");
        assertThat(records.get(0).getTargetStoreCode()).isNull();
        assertThat(records.get(0).getTargetSiteCode()).isNull();
        assertThat(records.get(0).getAssignedQuantity()).isEqualTo(10);
    }

    @Test
    void linkProductLinePersistsActiveSkuLinkForAssignedStoreLine() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderProductLinkView.LinkRequest request = productLinkRequest(99001L);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        ArgumentCaptor<Ali1688HistoricalOrderProductLinkRow> linkCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderProductLinkRow.class);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        stubProductSkuInAssignmentTarget(assignment, "CANMAN-AE-SKU-001");
        when(mapper.nextOrderItemProductLinkId()).thenReturn(100001L);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                service.linkProductLine(context, request);

        verify(mapper).insertOrderItemProductLink(linkCaptor.capture());
        Ali1688HistoricalOrderProductLinkRow inserted = linkCaptor.getValue();
        assertThat(inserted.getId()).isEqualTo(100001L);
        assertThat(inserted.getOwnerUserId()).isEqualTo(307L);
        assertThat(inserted.getAssignmentId()).isEqualTo(99001L);
        assertThat(inserted.getAuthorizationId()).isEqualTo(91008L);
        assertThat(inserted.getOrderId()).isEqualTo(93001L);
        assertThat(inserted.getItemId()).isEqualTo(94011L);
        assertThat(inserted.getTargetStoreCode()).isEqualTo("PRJ108065");
        assertThat(inserted.getTargetSiteCode()).isEqualTo("AE");
        assertThat(inserted.getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(inserted.getPartnerSku()).isEqualTo("CM-AE-PARTNER-001");
        assertThat(inserted.getPskuCode()).isEqualTo("PSKU-CM-AE-001");
        assertThat(inserted.getProductTitle()).isEqualTo("canman AE 抽纸盒");
        assertThat(inserted.getProductImageUrl()).isEqualTo("https://img.example.com/canman-ae.jpg");
        assertThat(inserted.getStatus()).isEqualTo("active");
        assertThat(inserted.getCreatedBy()).isEqualTo(307L);
        assertThat(inserted.getUpdatedBy()).isEqualTo(307L);
        assertThat(result.getStatus()).isEqualTo("linked");
        assertThat(result.getAssignmentId()).isEqualTo(99001L);
        assertThat(result.getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(result.getDisplayText()).isEqualTo("已关联: CANMAN-AE-SKU-001");
    }

    @Test
    void linkProductLineUsesBackendCandidateSnapshotInsteadOfRequestPayload() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderProductLinkView.LinkRequest request = productLinkRequest(99001L);
        request.setPartnerSku("SPOOFED-PARTNER");
        request.setPskuCode("SPOOFED-PSKU");
        request.setProductTitle("伪造商品标题");
        request.setProductImageUrl("https://attacker.example.com/spoofed.jpg");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkCandidateRow candidate = productLinkCandidateRow(
                "CANMAN-AE-SKU-001",
                "DB-PARTNER-001",
                "DB-PSKU-001",
                "后端商品标题",
                "unlinked"
        );
        candidate.setProductImageUrl("https://img.example.com/backend-candidate.jpg");
        ArgumentCaptor<Ali1688HistoricalOrderProductLinkRow> linkCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderProductLinkRow.class);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.countProductSkuInAssignmentTarget(307L, "PRJ108065", "AE", "CANMAN-AE-SKU-001"))
                .thenReturn(1);
        when(mapper.selectOrderItemProductLinkCandidateBySkuParent(
                307L,
                "PRJ108065",
                "AE",
                "CANMAN-AE-SKU-001"
        )).thenReturn(candidate);
        when(mapper.nextOrderItemProductLinkId()).thenReturn(100001L);

        service.linkProductLine(context, request);

        verify(mapper).insertOrderItemProductLink(linkCaptor.capture());
        Ali1688HistoricalOrderProductLinkRow inserted = linkCaptor.getValue();
        assertThat(inserted.getPartnerSku()).isEqualTo("DB-PARTNER-001");
        assertThat(inserted.getPskuCode()).isEqualTo("DB-PSKU-001");
        assertThat(inserted.getProductTitle()).isEqualTo("后端商品标题");
        assertThat(inserted.getProductImageUrl()).isEqualTo("https://img.example.com/backend-candidate.jpg");
    }

    @Test
    void relinkProductLineReplacesActiveSkuLinkAndRecordsAudit() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderProductLinkView.LinkRequest request =
                productLinkRequest(99001L, "CANMAN-AE-SKU-NEW", "CM-AE-PARTNER-NEW");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkRow activeLink = productLinkRow(100001L, assignment);
        ArgumentCaptor<Ali1688HistoricalOrderProductLinkRow> linkCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderProductLinkRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderProductLinkAuditRow> auditCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderProductLinkAuditRow.class);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        stubProductSkuInAssignmentTarget(assignment, "CANMAN-AE-SKU-NEW");
        when(mapper.selectActiveOrderItemProductLinkByAssignment(307L, 99001L)).thenReturn(activeLink);
        when(mapper.nextOrderItemProductLinkId()).thenReturn(100002L);
        when(mapper.nextOrderItemProductLinkAuditId()).thenReturn(101001L);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                service.linkProductLine(context, request);

        verify(mapper).updateOrderItemProductLinkStatus(100001L, 307L, "replaced", 307L);
        verify(mapper).insertOrderItemProductLink(linkCaptor.capture());
        verify(mapper).insertOrderItemProductLinkAudit(auditCaptor.capture());
        Ali1688HistoricalOrderProductLinkRow inserted = linkCaptor.getValue();
        assertThat(inserted.getId()).isEqualTo(100002L);
        assertThat(inserted.getSkuParent()).isEqualTo("CANMAN-AE-SKU-NEW");
        assertThat(inserted.getPartnerSku()).isEqualTo("CM-AE-PARTNER-NEW");
        Ali1688HistoricalOrderProductLinkAuditRow audit = auditCaptor.getValue();
        assertThat(audit.getId()).isEqualTo(101001L);
        assertThat(audit.getOwnerUserId()).isEqualTo(307L);
        assertThat(audit.getAssignmentId()).isEqualTo(99001L);
        assertThat(audit.getActionType()).isEqualTo("relink");
        assertThat(audit.getOldLinkId()).isEqualTo(100001L);
        assertThat(audit.getNewLinkId()).isEqualTo(100002L);
        assertThat(audit.getOldSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(audit.getNewSkuParent()).isEqualTo("CANMAN-AE-SKU-NEW");
        assertThat(audit.getCreatedBy()).isEqualTo(307L);
        assertThat(result.getStatus()).isEqualTo("linked");
        assertThat(result.getSkuParent()).isEqualTo("CANMAN-AE-SKU-NEW");
    }

    @Test
    void unlinkProductLineMarksActiveSkuLinkUnlinkedAndRecordsAudit() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkRow activeLink = productLinkRow(100001L, assignment);
        ArgumentCaptor<Ali1688HistoricalOrderProductLinkAuditRow> auditCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderProductLinkAuditRow.class);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.selectActiveOrderItemProductLinkByAssignment(307L, 99001L)).thenReturn(activeLink);
        when(mapper.nextOrderItemProductLinkAuditId()).thenReturn(101002L);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                service.unlinkProductLine(context, 99001L);

        verify(mapper).updateOrderItemProductLinkStatus(100001L, 307L, "unlinked", 307L);
        verify(mapper).insertOrderItemProductLinkAudit(auditCaptor.capture());
        Ali1688HistoricalOrderProductLinkAuditRow audit = auditCaptor.getValue();
        assertThat(audit.getId()).isEqualTo(101002L);
        assertThat(audit.getActionType()).isEqualTo("unlink");
        assertThat(audit.getOldLinkId()).isEqualTo(100001L);
        assertThat(audit.getNewLinkId()).isNull();
        assertThat(audit.getOldSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(audit.getNewSkuParent()).isNull();
        assertThat(audit.getCreatedBy()).isEqualTo(307L);
        assertThat(result.getStatus()).isEqualTo("unlinked");
        assertThat(result.getAssignmentId()).isEqualTo(99001L);
        assertThat(result.getSkuParent()).isNull();
    }

    @Test
    void linkProductLineIsIdempotentWhenSameSkuIsAlreadyActive() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderProductLinkView.LinkRequest request = productLinkRequest(99001L);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkRow activeLink = productLinkRow(100001L, assignment);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        stubProductSkuInAssignmentTarget(assignment, "CANMAN-AE-SKU-001");
        when(mapper.selectActiveOrderItemProductLinkByAssignment(307L, 99001L)).thenReturn(activeLink);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                service.linkProductLine(context, request);

        verify(mapper, never()).updateOrderItemProductLinkStatus(any(), any(), any(), any());
        verify(mapper, never()).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
        verify(mapper, never()).insertOrderItemProductLinkAudit(any(Ali1688HistoricalOrderProductLinkAuditRow.class));
        assertThat(result.getStatus()).isEqualTo("linked");
        assertThat(result.getAssignmentId()).isEqualTo(99001L);
        assertThat(result.getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
    }

    @Test
    void linkProductLineRejectsSkuOutsideAssignmentStoreSite() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderProductLinkView.LinkRequest request =
                productLinkRequest(99001L, "XINGYAO-SA-SKU-001", "XY-SA-PARTNER-001");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.countProductSkuInAssignmentTarget(307L, "PRJ108065", "AE", "XINGYAO-SA-SKU-001"))
                .thenReturn(0);

        assertThatThrownBy(() -> service.linkProductLine(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
        verify(mapper, never()).insertOrderItemProductLinkAudit(any(Ali1688HistoricalOrderProductLinkAuditRow.class));
    }

    @Test
    void linkProductLineRejectsReadOnlyProcurementAndWarehouseRoles() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        Ali1688HistoricalOrderProductLinkView.LinkRequest request = productLinkRequest(99001L);

        assertThatThrownBy(() -> service.linkProductLine(readOnlyRoleContext("采购"), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.linkProductLine(readOnlyRoleContext("仓管"), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(mapper, never()).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
        verify(mapper, never()).insertOrderItemProductLinkAudit(any(Ali1688HistoricalOrderProductLinkAuditRow.class));
    }

    @Test
    void assignProductLinesRejectsReadOnlyProcurementAndWarehouseRoles() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        Ali1688HistoricalOrderAssignmentView.AssignRequest request = assignmentRequest(
                "PRJ108065",
                "AE",
                94011L,
                2
        );

        assertThatThrownBy(() -> service.assignProductLines(readOnlyRoleContext("采购"), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.assignProductLines(readOnlyRoleContext("仓管"), request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(mapper, never()).insertOrderItemAssignment(any(Ali1688HistoricalOrderItemAssignmentRow.class));
    }

    @Test
    void operationsSupervisorCanLinkAssignedProductLine() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = operatorRoleContext("运营主管");
        Ali1688HistoricalOrderProductLinkView.LinkRequest request = productLinkRequest(99001L);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        stubProductSkuInAssignmentTarget(assignment, "CANMAN-AE-SKU-001");
        when(mapper.nextOrderItemProductLinkId()).thenReturn(100003L);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                service.linkProductLine(context, request);

        verify(mapper).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
        assertThat(result.getStatus()).isEqualTo("linked");
        assertThat(result.getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
    }

    @Test
    void linkProductLineBatchPersistsMultipleAssignmentsInOneServiceCall() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = operatorRoleContext("运营主管");
        Ali1688HistoricalOrderProductLinkView.LinkBatchRequest request =
                new Ali1688HistoricalOrderProductLinkView.LinkBatchRequest();
        request.setLinks(List.of(productLinkRequest(99001L), productLinkRequest(99002L)));
        Ali1688HistoricalOrderItemAssignmentRow firstAssignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderItemAssignmentRow secondAssignment =
                assignmentRow(99002L, 94012L, "PRJ108065", "AE", 6);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(firstAssignment);
        when(mapper.selectOrderItemAssignmentById(307L, 99002L)).thenReturn(secondAssignment);
        stubProductSkuInAssignmentTarget(firstAssignment, "CANMAN-AE-SKU-001");
        stubProductSkuInAssignmentTarget(secondAssignment, "CANMAN-AE-SKU-001");
        when(mapper.nextOrderItemProductLinkId()).thenReturn(100003L, 100004L);
        when(mapper.nextOrderItemProductLinkAuditId()).thenReturn(101003L, 101004L);

        Ali1688HistoricalOrderProductLinkView.LinkBatchResult result =
                service.linkProductLineBatch(context, request);

        verify(mapper, times(2)).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
        verify(mapper, times(2)).insertOrderItemProductLinkAudit(any(Ali1688HistoricalOrderProductLinkAuditRow.class));
        assertThat(result.getStatus()).isEqualTo("linked");
        assertThat(result.getLinkedLineCount()).isEqualTo(2);
        assertThat(result.getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
    }

    @Test
    void productLinkCandidatesDefaultToAllProductsSoLinkedSkuCanBeReused() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkCandidateRow candidate = productLinkCandidateRow(
                "CANMAN-AE-SKU-001",
                "CM-AE-PARTNER-001",
                "PSKU-CM-AE-001",
                "已关联 canman 商品",
                "linked"
        );

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.listOrderItemProductLinkCandidates(307L, "PRJ108065", "AE", null, null))
                .thenReturn(List.of(candidate));

        List<Ali1688HistoricalOrderProductLinkView.CandidateView> candidates =
                service.listProductLinkCandidates(context, 99001L, null, null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(candidates.get(0).getLinkStatus()).isEqualTo("linked");
    }

    @Test
    void productLinkCandidatesCanFilterAlreadyLinkedProducts() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkCandidateRow candidate = productLinkCandidateRow(
                "CANMAN-AE-SKU-001",
                "CM-AE-PARTNER-001",
                "PSKU-CM-AE-001",
                "已关联 canman 商品",
                "linked"
        );

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.listOrderItemProductLinkCandidates(307L, "PRJ108065", "AE", "linked", null))
                .thenReturn(List.of(candidate));

        List<Ali1688HistoricalOrderProductLinkView.CandidateView> candidates =
                service.listProductLinkCandidates(context, 99001L, "linked", null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(candidates.get(0).getLinkStatus()).isEqualTo("linked");
    }

    @Test
    void productLinkCandidatesRejectDiscontinuedAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        assignment.setTargetType("DISCONTINUED");

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);

        assertThatThrownBy(() -> service.listProductLinkCandidates(context, 99001L, null, null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).listOrderItemProductLinkCandidates(any(), any(), any(), any(), any());
    }

    @Test
    void productLinkCandidatesNormalizeLegacyNoonCoverImageUrls() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkCandidateRow candidate = productLinkCandidateRow(
                "Z08ABF1015D91B98FB287Z",
                "PAPERSAYSB375",
                "a59ce36279b16ec688d16c53949c1c0a",
                "100 Sheets 4 x 6 Photo Paper",
                "unlinked"
        );
        candidate.setProductImageUrl("https://f.nooncdn.com/pzsku/Z08ABF1015D91B98FB287Z/45/_/1778317772/dd325998-7b1d-4b8b-aa24-eae1f811e6b3");

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.listOrderItemProductLinkCandidates(307L, "PRJ108065", "AE", null, null))
                .thenReturn(List.of(candidate));

        List<Ali1688HistoricalOrderProductLinkView.CandidateView> candidates =
                service.listProductLinkCandidates(context, 99001L, null, null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getProductImageUrl())
                .isEqualTo("https://f.nooncdn.com/p/pzsku/Z08ABF1015D91B98FB287Z/45/_/1778317772/dd325998-7b1d-4b8b-aa24-eae1f811e6b3.jpg");
    }

    @Test
    void productLinkCandidatesNormalizeHashedNoonCoverImageUrls() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ69486");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ69486", "SA", 4);
        Ali1688HistoricalOrderProductLinkCandidateRow candidate = productLinkCandidateRow(
                "Z72E210340BAE3D7BC083Z",
                "SGGRB316",
                "a6a791ff61ac2ab2ddd6a059f17bd82a",
                "30cm玫瑰小熊玩偶",
                "unlinked"
        );
        candidate.setProductImageUrl("https://f.nooncdn.com/eff639f2df2651369082d90705ccc7ca|pzsku/Z72E210340BAE3D7BC083Z/45/1768470807/02b50050-583d-484e-bfc5-639dcdcf4201");

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.listOrderItemProductLinkCandidates(307L, "PRJ69486", "SA", null, null))
                .thenReturn(List.of(candidate));

        List<Ali1688HistoricalOrderProductLinkView.CandidateView> candidates =
                service.listProductLinkCandidates(context, 99001L, null, null);

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getProductImageUrl())
                .isEqualTo("https://f.nooncdn.com/p/eff639f2df2651369082d90705ccc7ca|pzsku/Z72E210340BAE3D7BC083Z/45/1768470807/02b50050-583d-484e-bfc5-639dcdcf4201.jpg");
    }

    @Test
    void linkProductLineRejectsUnassignedLineBecauseAssignmentIsRequired() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");

        when(mapper.selectOrderItemAssignmentById(307L, 99099L)).thenReturn(null);

        assertThatThrownBy(() -> service.linkProductLine(context, productLinkRequest(99099L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(mapper, never()).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
    }

    @Test
    void linkProductLineRejectsConsumableAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99002L, 94011L, null, null, 10);
        assignment.setTargetType("CONSUMABLE");

        when(mapper.selectOrderItemAssignmentById(307L, 99002L)).thenReturn(assignment);

        assertThatThrownBy(() -> service.linkProductLine(context, productLinkRequest(99002L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).countProductSkuInAssignmentTarget(any(), any(), any(), any());
        verify(mapper, never()).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
    }

    @Test
    void linkProductLineRejectsDiscontinuedAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        assignment.setTargetType("DISCONTINUED");

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);

        assertThatThrownBy(() -> service.linkProductLine(context, productLinkRequest(99001L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).countProductSkuInAssignmentTarget(any(), any(), any(), any());
        verify(mapper, never()).insertOrderItemProductLink(any(Ali1688HistoricalOrderProductLinkRow.class));
    }

    @Test
    void unlinkProductLineRejectsReadOnlyProcurementRole() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);

        assertThatThrownBy(() -> service.unlinkProductLine(readOnlyRoleContext("采购"), 99001L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(mapper, never()).updateOrderItemProductLinkStatus(any(), any(), any(), any());
        verify(mapper, never()).insertOrderItemProductLinkAudit(any(Ali1688HistoricalOrderProductLinkAuditRow.class));
    }

    @Test
    void listSkuPurchaseHistoryAggregatesLinkedStoreSkuWithPaidAmountAllocation() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                4,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥110.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                5,
                                5,
                                "¥200.00",
                                "¥200.00",
                                "¥200.00"
                        )
                ));

        Ali1688SkuPurchaseHistoryView view =
                service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).hasSize(1);
        Ali1688SkuPurchaseHistoryView.ItemView item = view.getItems().get(0);
        assertThat(item.getStoreCode()).isEqualTo("PRJ108065");
        assertThat(item.getSiteCode()).isEqualTo("AE");
        assertThat(item.getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getTotalQuantity()).isEqualTo(9);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("244.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("27.11"));
        assertThat(item.getRecentUnitPrice()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(item.getRecentPurchaseTime()).isEqualTo("2026-05-20 10:00:00");
        assertThat(item.getLowestUnitPrice()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(item.getHighestUnitPrice()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(item.getAmountBasis()).isEqualTo("paid_amount_allocated");
        assertThat(item.getHistory()).hasSize(2);
    }

    @Test
    void listSkuPurchaseHistoryUsesPersistedManualBatchesForPurchaseMetrics() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                4,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥110.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                5,
                                5,
                                "¥200.00",
                                "¥200.00",
                                "¥200.00"
                        )
                ));
        when(mapper.listSkuPurchaseBatches(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(skuPurchaseBatchRow(
                        102001L,
                        "PRJ108065",
                        "AE",
                        "CANMAN-AE-SKU-001",
                        "批次 1",
                        3,
                        "120.00",
                        "3个/套换6个/套"
                )));
        when(mapper.listSkuPurchaseBatchSources(307L, List.of(102001L)))
                .thenReturn(List.of(
                        skuPurchaseBatchSourceRow(102001L, 99001L, "ALI-001", "2026-05-01 10:00:00"),
                        skuPurchaseBatchSourceRow(102001L, 99002L, "ALI-002", "2026-05-20 10:00:00")
                ));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(1);
        assertThat(item.getTotalQuantity()).isEqualTo(3);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("120.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(item.getRecentUnitPrice()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(item.getAmountBasis()).isEqualTo("manual_batch_adjusted");
        assertThat(item.getPurchaseBatches()).hasSize(1);
        assertThat(item.getPurchaseBatches().get(0).getSources())
                .extracting(Ali1688SkuPurchaseHistoryView.PurchaseBatchSourceView::getAssignmentId)
                .containsExactly(99001L, 99002L);
    }

    @Test
    void listSkuPurchaseHistoryFiltersByAggregatedPurchaseCount() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery repeatPurchaseQuery =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);
        repeatPurchaseQuery.setPurchaseCountMin(2);
        Ali1688SkuPurchaseHistoryQuery noPurchaseQuery =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);
        noPurchaseQuery.setPurchaseCountMax(0);

        when(mapper.listSkuPurchaseHistoryProducts(307L, "PRJ108065", "AE", null))
                .thenReturn(List.of(
                        skuPurchaseProductRow("CANMAN-AE-SKU-001", "CM-AE-PARTNER-001", "PSKU-CM-AE-001", "复购商品"),
                        skuPurchaseProductRow("CANMAN-AE-SKU-002", "CM-AE-PARTNER-002", "PSKU-CM-AE-002", "单次采购商品"),
                        skuPurchaseProductRow("CANMAN-AE-SKU-003", "CM-AE-PARTNER-003", "PSKU-CM-AE-003", "未采购商品")
                ));
        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                4,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥110.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                5,
                                5,
                                "¥200.00",
                                "¥200.00",
                                "¥200.00"
                        ),
                        purchaseHistoryRow(
                                99003L,
                                "ALI-003",
                                "2026-05-25 10:00:00",
                                "CANMAN-AE-SKU-002",
                                2,
                                2,
                                "¥40.00",
                                "¥40.00",
                                "¥40.00"
                        )
                ));

        Ali1688SkuPurchaseHistoryView repeatPurchaseView =
                service.listSkuPurchaseHistory(context, repeatPurchaseQuery);
        Ali1688SkuPurchaseHistoryView noPurchaseView =
                service.listSkuPurchaseHistory(context, noPurchaseQuery);

        assertThat(repeatPurchaseView.getItems())
                .extracting(Ali1688SkuPurchaseHistoryView.ItemView::getSkuParent)
                .containsExactly("CANMAN-AE-SKU-001");
        assertThat(noPurchaseView.getItems())
                .extracting(Ali1688SkuPurchaseHistoryView.ItemView::getSkuParent)
                .containsExactly("CANMAN-AE-SKU-003");
    }

    @Test
    void listSkuPurchaseHistoryFiltersByPriceAnomalyBeforePagination() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);
        query.setPriceAnomalyOnly(true);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99011L,
                                "ALI-NORMAL-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-NORMAL",
                                10,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥100.00"
                        ),
                        purchaseHistoryRow(
                                99012L,
                                "ALI-NORMAL-002",
                                "2026-05-02 10:00:00",
                                "CANMAN-AE-SKU-NORMAL",
                                10,
                                10,
                                "¥110.00",
                                "¥110.00",
                                "¥110.00"
                        ),
                        purchaseHistoryRow(
                                99013L,
                                "ALI-NORMAL-003",
                                "2026-05-03 10:00:00",
                                "CANMAN-AE-SKU-NORMAL",
                                10,
                                10,
                                "¥105.00",
                                "¥105.00",
                                "¥105.00"
                        ),
                        purchaseHistoryRow(
                                99021L,
                                "ALI-ANOMALY-001",
                                "2026-05-04 10:00:00",
                                "CANMAN-AE-SKU-ANOMALY",
                                10,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥100.00"
                        ),
                        purchaseHistoryRow(
                                99022L,
                                "ALI-ANOMALY-002",
                                "2026-05-05 10:00:00",
                                "CANMAN-AE-SKU-ANOMALY",
                                10,
                                10,
                                "¥110.00",
                                "¥110.00",
                                "¥110.00"
                        ),
                        purchaseHistoryRow(
                                99023L,
                                "ALI-ANOMALY-003",
                                "2026-05-06 10:00:00",
                                "CANMAN-AE-SKU-ANOMALY",
                                10,
                                10,
                                "¥300.00",
                                "¥300.00",
                                "¥300.00"
                        )
                ));

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getPagination().getTotal()).isEqualTo(1);
        assertThat(view.getItems())
                .extracting(Ali1688SkuPurchaseHistoryView.ItemView::getSkuParent)
                .containsExactly("CANMAN-AE-SKU-ANOMALY");
        assertThat(view.getItems().get(0).getPriceAnomalyCount()).isEqualTo(1);
    }

    @Test
    void listSkuPurchaseHistoryMarksPriceAnomaliesAndExcludesThemFromStableAverage() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥100.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥110.00",
                                "¥110.00",
                                "¥110.00"
                        ),
                        purchaseHistoryRow(
                                99003L,
                                "ALI-003",
                                "2026-05-25 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥123.00",
                                "¥123.00",
                                "¥123.00"
                        )
                ));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(3);
        assertThat(item.getPriceAnomalyCount()).isEqualTo(1);
        assertThat(item.getStableAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("10.50"));
    }

    @Test
    void listSkuPurchaseHistoryDoesNotMarkTwoOrdersAnomalousWhenPricesStayWithinTenPercent() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥100.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥100.10",
                                "¥100.10",
                                "¥100.10"
                        )
                ));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getPriceAnomalyCount()).isZero();
        assertThat(item.getStableAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("10.01"));
    }

    @Test
    void listSkuPurchaseHistoryMarksBothOrdersAnomalousWhenOnlyTwoPricesDeviateByMoreThanTenPercent() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥100.00",
                                "¥100.00",
                                "¥100.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                10,
                                10,
                                "¥120.00",
                                "¥120.00",
                                "¥120.00"
                        )
                ));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getPriceAnomalyCount()).isEqualTo(2);
        assertThat(item.getStableAverageUnitPrice()).isNull();
    }

    @Test
    void saveSkuPurchaseBatchesReplacesExistingManualBatchesForOneSku() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseBatchView.SaveRequest request = new Ali1688SkuPurchaseBatchView.SaveRequest();
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");
        request.setSkuParent("CANMAN-AE-SKU-001");
        request.setPartnerSku("CM-AE-PARTNER-001");
        request.setPskuCode("PSKU-CM-AE-001");
        Ali1688SkuPurchaseBatchView.BatchRequest batch = new Ali1688SkuPurchaseBatchView.BatchRequest();
        batch.setLabel("批次 1");
        batch.setCountedQuantity(3);
        batch.setCountedCost(new BigDecimal("120.00"));
        batch.setNote("3个/套换6个/套");
        Ali1688SkuPurchaseBatchView.SourceRequest firstSource = new Ali1688SkuPurchaseBatchView.SourceRequest();
        firstSource.setAssignmentId(99001L);
        firstSource.setOrderId(93001L);
        firstSource.setItemId(94001L);
        firstSource.setOrderNo("ALI-001");
        firstSource.setOrderTime("2026-05-01 10:00:00");
        firstSource.setSupplierName("义乌诚信通源头工厂");
        Ali1688SkuPurchaseBatchView.SourceRequest secondSource = new Ali1688SkuPurchaseBatchView.SourceRequest();
        secondSource.setAssignmentId(99002L);
        secondSource.setOrderId(93002L);
        secondSource.setItemId(94002L);
        secondSource.setOrderNo("ALI-002");
        secondSource.setOrderTime("2026-05-20 10:00:00");
        secondSource.setSupplierName("义乌诚信通源头工厂");
        batch.setSources(List.of(firstSource, secondSource));
        request.setBatches(List.of(batch));

        Ali1688HistoricalOrderItemAssignmentRow firstAssignment =
                assignmentRow(99001L, 94001L, "PRJ108065", "AE", 4);
        firstAssignment.setOrderId(93001L);
        Ali1688HistoricalOrderItemAssignmentRow secondAssignment =
                assignmentRow(99002L, 94002L, "PRJ108065", "AE", 5);
        secondAssignment.setOrderId(93002L);
        when(mapper.nextSkuPurchaseBatchId()).thenReturn(102001L);
        when(mapper.nextSkuPurchaseBatchSourceId()).thenReturn(103001L, 103002L);
        when(mapper.selectOrderItemAssignmentById(307L, 99001L))
                .thenReturn(firstAssignment);
        when(mapper.selectOrderItemAssignmentById(307L, 99002L))
                .thenReturn(secondAssignment);
        when(mapper.selectActiveOrderItemProductLinkByAssignment(307L, 99001L))
                .thenReturn(productLinkRow(100001L, firstAssignment));
        when(mapper.selectActiveOrderItemProductLinkByAssignment(307L, 99002L))
                .thenReturn(productLinkRow(100002L, secondAssignment));

        Ali1688SkuPurchaseBatchView.SaveResult result =
                service.saveSkuPurchaseBatches(context, request);

        assertThat(result.getSavedBatchCount()).isEqualTo(1);
        assertThat(result.getSavedSourceCount()).isEqualTo(2);
        verify(mapper).softDeleteSkuPurchaseBatchesForSku(307L, "PRJ108065", "AE", "CANMAN-AE-SKU-001", 307L);
        ArgumentCaptor<Ali1688SkuPurchaseBatchRow> batchCaptor =
                ArgumentCaptor.forClass(Ali1688SkuPurchaseBatchRow.class);
        verify(mapper).insertSkuPurchaseBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getId()).isEqualTo(102001L);
        assertThat(batchCaptor.getValue().getCountedQuantity()).isEqualTo(3);
        assertThat(batchCaptor.getValue().getCountedCost()).isEqualByComparingTo(new BigDecimal("120.00"));
        ArgumentCaptor<Ali1688SkuPurchaseBatchSourceRow> sourceCaptor =
                ArgumentCaptor.forClass(Ali1688SkuPurchaseBatchSourceRow.class);
        verify(mapper, times(2)).insertSkuPurchaseBatchSource(sourceCaptor.capture());
        assertThat(sourceCaptor.getAllValues())
                .extracting(Ali1688SkuPurchaseBatchSourceRow::getAssignmentId)
                .containsExactly(99001L, 99002L);
    }

    @Test
    void listSkuPurchaseHistoryAggregatesMultipleLinesFromSameOrderIntoOneHistoryPoint() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        Ali1688SkuPurchaseHistoryRow sameOrderFirstLine = purchaseHistoryRow(
                99001L,
                "ALI-SAME-001",
                "2026-05-20 10:00:00",
                "CANMAN-AE-SKU-001",
                2,
                2,
                "¥40.00",
                "¥130.00",
                "¥130.00"
        );
        sameOrderFirstLine.setOrderId(93088L);
        sameOrderFirstLine.setItemId(94088L);

        Ali1688SkuPurchaseHistoryRow sameOrderSecondLine = purchaseHistoryRow(
                99002L,
                "ALI-SAME-001",
                "2026-05-20 10:00:00",
                "CANMAN-AE-SKU-001",
                3,
                3,
                "¥90.00",
                "¥130.00",
                "¥130.00"
        );
        sameOrderSecondLine.setOrderId(93088L);
        sameOrderSecondLine.setItemId(94089L);

        Ali1688SkuPurchaseHistoryRow otherOrderLine = purchaseHistoryRow(
                99003L,
                "ALI-OTHER-002",
                "2026-05-21 10:00:00",
                "CANMAN-AE-SKU-001",
                1,
                1,
                "¥10.00",
                "¥10.00",
                "¥10.00"
        );
        otherOrderLine.setOrderId(93089L);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(sameOrderFirstLine, sameOrderSecondLine, otherOrderLine));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getTotalQuantity()).isEqualTo(6);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("23.33"));
        assertThat(item.getLowestUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getHighestUnitPrice()).isEqualByComparingTo(new BigDecimal("26.00"));
        assertThat(item.getHistory()).extracting(Ali1688SkuPurchaseHistoryView.HistoryView::getOrderNo)
                .containsExactly("ALI-OTHER-002", "ALI-SAME-001");

        Ali1688SkuPurchaseHistoryView.HistoryView sameOrderHistory = item.getHistory().stream()
                .filter(history -> "ALI-SAME-001".equals(history.getOrderNo()))
                .findFirst()
                .orElseThrow();
        assertThat(sameOrderHistory.getAssignedQuantity()).isEqualTo(5);
        assertThat(sameOrderHistory.getAllocatedCost()).isEqualByComparingTo(new BigDecimal("130.00"));
        assertThat(sameOrderHistory.getUnitPrice()).isEqualByComparingTo(new BigDecimal("26.00"));
        assertThat(sameOrderHistory.getPriceQuality()).isEqualTo("ready");
    }

    @Test
    void listSkuPurchaseHistoryAllocatesFullPaidAmountWhenAllOrderItemsLinkToSameSkuDespiteGoodsTotalRounding() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        List<Ali1688SkuPurchaseHistoryRow> sameOrderRows = List.of(
                purchaseHistoryRow(99049L, "5118264938393005337", "2026-05-29 14:17:59", "Z6F6FB9180C80122C7EA5Z", 100, 100, "49.75", "200", "218"),
                purchaseHistoryRow(99050L, "5118264938393005337", "2026-05-29 14:17:59", "Z6F6FB9180C80122C7EA5Z", 100, 100, "49.75", "200", "218"),
                purchaseHistoryRow(99051L, "5118264938393005337", "2026-05-29 14:17:59", "Z6F6FB9180C80122C7EA5Z", 100, 100, "49.75", "200", "218"),
                purchaseHistoryRow(99052L, "5118264938393005337", "2026-05-29 14:17:59", "Z6F6FB9180C80122C7EA5Z", 100, 100, "49.75", "200", "218")
        );
        sameOrderRows.forEach(row -> row.setOrderId(930005860L));

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(sameOrderRows);

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(1);
        assertThat(item.getTotalQuantity()).isEqualTo(400);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("218.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("0.55"));
        assertThat(item.getHistory()).hasSize(1);
        assertThat(item.getHistory().get(0).getAllocatedCost()).isEqualByComparingTo(new BigDecimal("218.00"));
    }

    @Test
    void listSkuPurchaseHistoryKeepsMissingPriceOrderOutOfPriceStats() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        Ali1688SkuPurchaseHistoryRow readyOrder = purchaseHistoryRow(
                99001L,
                "ALI-READY-001",
                "2026-05-21 10:00:00",
                "CANMAN-AE-SKU-001",
                2,
                2,
                "¥50.00",
                "¥50.00",
                "¥50.00"
        );
        readyOrder.setOrderId(93091L);

        Ali1688SkuPurchaseHistoryRow missingPriceLine = purchaseHistoryRow(
                99002L,
                "ALI-MISSING-002",
                "2026-05-22 10:00:00",
                "CANMAN-AE-SKU-001",
                3,
                3,
                null,
                "¥90.00",
                "¥90.00"
        );
        missingPriceLine.setOrderId(93092L);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(readyOrder, missingPriceLine));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getTotalQuantity()).isEqualTo(5);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(item.getLowestUnitPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(item.getHighestUnitPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(item.getRecentUnitPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(item.getRecentPurchaseTime()).isEqualTo("2026-05-21 10:00:00");
        assertThat(item.getDataQualityFlags()).containsExactly("missing_price_basis");

        Ali1688SkuPurchaseHistoryView.HistoryView missingHistory = item.getHistory().stream()
                .filter(history -> "ALI-MISSING-002".equals(history.getOrderNo()))
                .findFirst()
                .orElseThrow();
        assertThat(missingHistory.getAssignedQuantity()).isEqualTo(3);
        assertThat(missingHistory.getAllocatedCost()).isNull();
        assertThat(missingHistory.getUnitPrice()).isNull();
        assertThat(missingHistory.getPriceQuality()).isEqualTo("missing_price_basis");
    }

    @Test
    void listSkuPurchaseHistoryDoesNotMergeRowsWhenOrderIdentityIsMissing() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        Ali1688SkuPurchaseHistoryRow firstUnknownOrder = purchaseHistoryRow(
                99001L,
                null,
                "2026-05-20 10:00:00",
                "CANMAN-AE-SKU-001",
                2,
                2,
                "¥40.00",
                "¥40.00",
                "¥40.00"
        );
        firstUnknownOrder.setOrderId(null);

        Ali1688SkuPurchaseHistoryRow secondUnknownOrder = purchaseHistoryRow(
                99002L,
                "",
                "2026-05-21 10:00:00",
                "CANMAN-AE-SKU-001",
                3,
                3,
                "¥90.00",
                "¥90.00",
                "¥90.00"
        );
        secondUnknownOrder.setOrderId(null);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(firstUnknownOrder, secondUnknownOrder));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getHistory()).hasSize(2);
        assertThat(item.getTotalQuantity()).isEqualTo(5);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("130.00"));
    }

    @Test
    void listSkuPurchaseHistoryIncludesStoreProductsWithoutPurchaseHistory() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryProducts(307L, "PRJ108065", "AE", null))
                .thenReturn(List.of(skuPurchaseProductRow(
                        "CANMAN-AE-SKU-NO-HISTORY",
                        "CM-AE-NO-HISTORY",
                        "PSKU-CM-AE-NO-HISTORY",
                        "canman AE 未采购商品"
                )));
        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of());

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).hasSize(1);
        Ali1688SkuPurchaseHistoryView.ItemView item = view.getItems().get(0);
        assertThat(item.getSkuParent()).isEqualTo("CANMAN-AE-SKU-NO-HISTORY");
        assertThat(item.getPartnerSku()).isEqualTo("CM-AE-NO-HISTORY");
        assertThat(item.getPskuCode()).isEqualTo("PSKU-CM-AE-NO-HISTORY");
        assertThat(item.getProductTitle()).isEqualTo("canman AE 未采购商品");
        assertThat(item.getProductTitleCn()).isEqualTo("canman AE 未采购商品中文名");
        assertThat(item.getPurchaseCount()).isZero();
        assertThat(item.getTotalQuantity()).isZero();
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(item.getHistory()).isEmpty();
    }

    @Test
    void listSkuPurchaseHistoryDoesNotUseUnlinkedAssignedLinesAsProductRowsInDefaultView() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of());
        when(mapper.countUnlinkedAssignedStoreSiteLines(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(1);

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).isEmpty();
        assertThat(view.getUnlinkedAssignedLineCount()).isEqualTo(1);
    }

    @Test
    void listSkuPurchaseHistoryFiltersLinkedStoreProductsByActiveProductHistory() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest(
                        "PRJ108065",
                        "AE",
                        null,
                        "linked",
                        null,
                        null,
                        1,
                        20
                );

        when(mapper.listSkuPurchaseHistoryProducts(307L, "PRJ108065", "AE", null))
                .thenReturn(List.of(
                        skuPurchaseProductRow("CANMAN-AE-SKU-001", "CANMAN-FLOWER-AE", "PSKU-FLOWER", "已关联商品"),
                        skuPurchaseProductRow("CANMAN-AE-SKU-002", "CANMAN-PAPER-AE", "PSKU-PAPER", "未关联商品")
                ));
        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(purchaseHistoryRow(
                        99001L,
                        "ALI-001",
                        "2026-05-01 10:00:00",
                        "CANMAN-AE-SKU-001",
                        4,
                        10,
                        "¥100.00",
                        "¥100.00",
                        "¥100.00"
                )));

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).hasSize(1);
        assertThat(view.getItems().get(0).getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(view.getItems().get(0).getLinkStatus()).isEqualTo("linked");
    }

    @Test
    void listSkuPurchaseHistoryFiltersUnlinkedStoreProductsWithoutRenderingUnlinkedOrderLinesAsProducts() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest(
                        "PRJ108065",
                        "AE",
                        null,
                        "unlinked",
                        "2026-05-01",
                        "2026-05-28",
                        1,
                        20
                );

        when(mapper.listSkuPurchaseHistoryProducts(307L, "PRJ108065", "AE", null))
                .thenReturn(List.of(
                        skuPurchaseProductRow("CANMAN-AE-SKU-001", "CANMAN-FLOWER-AE", "PSKU-FLOWER", "已关联商品"),
                        skuPurchaseProductRow("CANMAN-AE-SKU-002", "CANMAN-PAPER-AE", "PSKU-PAPER", "未关联商品")
                ));
        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, "2026-05-01", "2026-05-28"))
                .thenReturn(List.of(purchaseHistoryRow(
                        99001L,
                        "ALI-001",
                        "2026-05-01 10:00:00",
                        "CANMAN-AE-SKU-001",
                        4,
                        10,
                        "¥100.00",
                        "¥100.00",
                        "¥100.00"
                )));
        when(mapper.countUnlinkedAssignedStoreSiteLines(307L, "PRJ108065", "AE", null, "2026-05-01", "2026-05-28"))
                .thenReturn(1);

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).hasSize(1);
        assertThat(view.getItems().get(0).getSkuParent()).isEqualTo("CANMAN-AE-SKU-002");
        assertThat(view.getItems().get(0).getProductTitle()).isEqualTo("未关联商品");
        assertThat(view.getItems().get(0).getLinkStatus()).isEqualTo("unlinked");
        assertThat(view.getItems().get(0).getPurchaseCount()).isZero();
        assertThat(view.getUnlinkedAssignedLineCount()).isEqualTo(1);
        verify(mapper, never()).listUnlinkedSkuPurchaseHistoryRows(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listSkuPurchaseHistoryFallsBackToItemAmountWhenPaidAmountIsMissing() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(purchaseHistoryRow(
                        99001L,
                        "ALI-001",
                        "2026-05-01 10:00:00",
                        "CANMAN-AE-SKU-001",
                        5,
                        10,
                        "¥100.00",
                        "¥100.00",
                        null
                )));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getAmountBasis()).isEqualTo("item_amount_allocated");
        assertThat(item.getHistory().get(0).getAmountBasis()).isEqualTo("item_amount_allocated");
    }

    @Test
    void listSkuPurchaseHistoryKeepsMissingPriceRowsOutOfPriceStats() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(
                        purchaseHistoryRow(
                                99001L,
                                "ALI-001",
                                "2026-05-01 10:00:00",
                                "CANMAN-AE-SKU-001",
                                2,
                                2,
                                "¥20.00",
                                "¥20.00",
                                "¥20.00"
                        ),
                        purchaseHistoryRow(
                                99002L,
                                "ALI-002",
                                "2026-05-20 10:00:00",
                                "CANMAN-AE-SKU-001",
                                3,
                                3,
                                null,
                                null,
                                null
                        )
                ));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(2);
        assertThat(item.getTotalQuantity()).isEqualTo(5);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(item.getAverageUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getLowestUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getHighestUnitPrice()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getHistory()).hasSize(2);
        assertThat(item.getHistory().get(0).getPriceQuality()).isEqualTo("missing_price_basis");
        assertThat(item.getDataQualityFlags()).contains("missing_price_basis");
    }

    @Test
    void listSkuPurchaseHistoryDeduplicatesSameSourceItemAndKeepsPriceReadyRow() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);
        Ali1688SkuPurchaseHistoryRow missingUploadRow =
                purchaseHistoryRow(
                        99001L,
                        "4888302194910114902",
                        "2025-11-17 20:44:54",
                        "CANMAN-AE-SKU-001",
                        20,
                        20,
                        null,
                        "¥42.40",
                        "¥46.90"
                );
        Ali1688SkuPurchaseHistoryRow pricedLocalRow =
                purchaseHistoryRow(
                        99002L,
                        "4888302194910114902",
                        "2025-11-17 20:44:54",
                        "CANMAN-AE-SKU-001",
                        20,
                        20,
                        "¥10.60",
                        "¥42.40",
                        "¥46.90"
                );
        setAli1688SourceItemIdentity(missingUploadRow, "727682641739", "5045758236994", "b645", "b645-03");
        setAli1688SourceItemIdentity(pricedLocalRow, "727682641739", "5045758236994", "b645", "b645-03");

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(missingUploadRow, pricedLocalRow));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(1);
        assertThat(item.getHistory()).hasSize(1);
        assertThat(item.getHistory().get(0).getAssignmentId()).isEqualTo(99002L);
        assertThat(item.getHistory().get(0).getPriceQuality()).isEqualTo("ready");
        assertThat(item.getDataQualityFlags()).doesNotContain("missing_price_basis");
    }

    @Test
    void listSkuPurchaseHistoryDeduplicatesByAssignmentId() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);
        Ali1688SkuPurchaseHistoryRow duplicated =
                purchaseHistoryRow(
                        99001L,
                        "ALI-001",
                        "2026-05-01 10:00:00",
                        "CANMAN-AE-SKU-001",
                        4,
                        10,
                        "¥100.00",
                        "¥100.00",
                        "¥100.00"
                );

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of(duplicated, duplicated));

        Ali1688SkuPurchaseHistoryView.ItemView item =
                service.listSkuPurchaseHistory(context, query).getItems().get(0);

        assertThat(item.getPurchaseCount()).isEqualTo(1);
        assertThat(item.getTotalQuantity()).isEqualTo(4);
        assertThat(item.getTotalCost()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(item.getHistory()).hasSize(1);
    }

    @Test
    void listSkuPurchaseHistoryKeepsUnlinkedFilterOnStoreProductDimensionWhenNoProductsExist() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest(
                        "PRJ108065",
                        "AE",
                        null,
                        "unlinked",
                        "2026-05-01",
                        "2026-05-28",
                        1,
                        20
                );

        when(mapper.listSkuPurchaseHistoryProducts(307L, "PRJ108065", "AE", null))
                .thenReturn(List.of());
        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, "2026-05-01", "2026-05-28"))
                .thenReturn(List.of());
        when(mapper.countUnlinkedAssignedStoreSiteLines(307L, "PRJ108065", "AE", null, "2026-05-01", "2026-05-28"))
                .thenReturn(1);

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).isEmpty();
        assertThat(view.getPagination().getTotal()).isZero();
        assertThat(view.getUnlinkedAssignedLineCount()).isEqualTo(1);
        verify(mapper, never()).listUnlinkedSkuPurchaseHistoryRows(any(), any(), any(), any(), any(), any());
    }

    @Test
    void listSkuPurchaseHistoryReportsUnlinkedAssignedLineCountForActionableEmptyState() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688SkuPurchaseHistoryQuery query =
                Ali1688SkuPurchaseHistoryQuery.fromRequest("PRJ108065", "AE", null, 1, 20);

        when(mapper.listSkuPurchaseHistoryRows(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(List.of());
        when(mapper.countUnlinkedAssignedStoreSiteLines(307L, "PRJ108065", "AE", null, null, null))
                .thenReturn(2);

        Ali1688SkuPurchaseHistoryView view = service.listSkuPurchaseHistory(context, query);

        assertThat(view.getItems()).isEmpty();
        assertThat(view.getUnlinkedAssignedLineCount()).isEqualTo(2);
    }

    @Test
    void listProductLinkAuditsReturnsScopedAssignmentAuditRecords() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderProductLinkAuditRow audit = new Ali1688HistoricalOrderProductLinkAuditRow();
        audit.setId(101001L);
        audit.setOwnerUserId(307L);
        audit.setAssignmentId(99001L);
        audit.setActionType("relink");
        audit.setOldSkuParent("CANMAN-AE-SKU-001");
        audit.setNewSkuParent("CANMAN-AE-SKU-NEW");
        audit.setCreatedBy(307L);
        audit.setCreatedAt("2026-05-28 12:30:00");

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.listOrderItemProductLinkAudits(307L, 99001L)).thenReturn(List.of(audit));

        List<Ali1688HistoricalOrderProductLinkView.AuditView> audits =
                service.listProductLinkAudits(context, 99001L);

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).getAuditId()).isEqualTo(101001L);
        assertThat(audits.get(0).getActionType()).isEqualTo("relink");
        assertThat(audits.get(0).getOldSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(audits.get(0).getNewSkuParent()).isEqualTo("CANMAN-AE-SKU-NEW");
        assertThat(audits.get(0).getCreatedBy()).isEqualTo(307L);
        assertThat(audits.get(0).getCreatedAt()).isEqualTo("2026-05-28 12:30:00");
    }

    @Test
    void adjustAssignmentQuantityRespectsOriginalQuantityAndSessionScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAssignmentView.AdjustRequest request =
                adjustRequest(7);
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "已分配货品", 10);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.sumAssignedQuantityExcludingAssignment(307L, 94011L, 99001L)).thenReturn(2);

        Ali1688HistoricalOrderAssignmentView.AssignResult result =
                service.adjustAssignmentQuantity(context, 99001L, request);

        verify(mapper).updateOrderItemAssignmentQuantity(99001L, 307L, 7, 307L);
        assertThat(result.getAssignedLineCount()).isEqualTo(1);
        assertThat(result.getAssignedQuantity()).isEqualTo(7);
    }

    @Test
    void adjustAssignmentQuantityRejectsCumulativeOverAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);
        Ali1688HistoricalOrderItemRow item = itemRow(94011L, 93001L, "已分配货品", 10);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);
        when(mapper.selectOrderItemForAssignment(307L, 94011L)).thenReturn(item);
        when(mapper.sumAssignedQuantityExcludingAssignment(307L, 94011L, 99001L)).thenReturn(8);

        assertThatThrownBy(() -> service.adjustAssignmentQuantity(context, 99001L, adjustRequest(3)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).updateOrderItemAssignmentQuantity(any(), any(), any(), any());
    }

    @Test
    void adjustAssignmentQuantityRejectsConsumableRecordBecauseConsumablesUseWholeLineQuantity() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = operatorContext();
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99002L, 94011L, null, null, 10);

        when(mapper.selectOrderItemAssignmentById(307L, 99002L)).thenReturn(assignment);

        assertThatThrownBy(() -> service.adjustAssignmentQuantity(context, 99002L, adjustRequest(5)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(mapper, never()).updateOrderItemAssignmentQuantity(any(), any(), any(), any());
    }

    @Test
    void revokeAssignmentMarksRecordRevokedWithoutDeletingAudit() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);

        Ali1688HistoricalOrderAssignmentView.AssignResult result =
                service.revokeAssignment(context, 99001L);

        verify(mapper).revokeOrderItemAssignment(99001L, 307L, 307L);
        assertThat(result.getAssignedLineCount()).isEqualTo(1);
        assertThat(result.getAssignedQuantity()).isEqualTo(0);
    }

    @Test
    void revokeAssignmentDeactivatesActiveProductLinksForAssignment() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);

        service.revokeAssignment(context, 99001L);

        verify(mapper).revokeOrderItemAssignment(99001L, 307L, 307L);
        verify(mapper).deactivateActiveOrderItemProductLinks(307L, 99001L, 307L);
    }

    @Test
    void adjustAndRevokeAssignmentRejectTargetStoreOutsideSessionScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ245027");
        Ali1688HistoricalOrderItemAssignmentRow assignment =
                assignmentRow(99001L, 94011L, "PRJ108065", "AE", 4);

        when(mapper.selectOrderItemAssignmentById(307L, 99001L)).thenReturn(assignment);

        assertThatThrownBy(() -> service.adjustAssignmentQuantity(context, 99001L, adjustRequest(2)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.revokeAssignment(context, 99001L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(mapper, never()).updateOrderItemAssignmentQuantity(any(), any(), any(), any());
        verify(mapper, never()).revokeOrderItemAssignment(any(), any(), any());
    }

    @Test
    void deleteHistoricalOrderSoftDeletesUnassignedOrderFactsAndRecordsAudit() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderCleanupView.DeleteOrderRequest request =
                deleteOrderRequest("PRJ108065", "AE", "不属于任何店铺");
        Ali1688HistoricalOrderRow order = orderRow(93001L, 91001L);

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow(91001L));
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91001L));
        when(mapper.selectOrderById(307L, List.of(91001L), 93001L)).thenReturn(order);

        Ali1688HistoricalOrderCleanupView.DeleteOrderResult result =
                service.deleteHistoricalOrder(context, 93001L, request);

        verify(mapper).deactivateActiveSkuPurchaseBatchesForOrder(307L, 93001L, 307L);
        verify(mapper).deactivateActiveSkuPurchaseBatchSourcesForOrder(307L, 93001L, 307L);
        verify(mapper).deactivateActiveProductLinksForOrder(307L, 93001L, 307L);
        verify(mapper).revokeActiveOrderAssignmentsForOrder(307L, 93001L, 307L);
        verify(mapper).softDeleteOrderHeader(93001L, 307L, 307L, "不属于任何店铺");
        verify(mapper).softDeleteOrderItems(93001L);
        verify(mapper).softDeleteOrderLogistics(93001L);
        assertThat(result.getOrderId()).isEqualTo(93001L);
        assertThat(result.getStatus()).isEqualTo("deleted");
        assertThat(result.getReason()).isEqualTo("不属于任何店铺");
    }

    @Test
    void deleteHistoricalOrderRevokesActiveOrderStateBeforeSoftDeletingFacts() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(authorizationRow(91001L));
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91001L));
        when(mapper.selectOrderById(307L, List.of(91001L), 93001L)).thenReturn(orderRow(93001L, 91001L));

        Ali1688HistoricalOrderCleanupView.DeleteOrderResult result = service.deleteHistoricalOrder(
                context,
                93001L,
                deleteOrderRequest("PRJ108065", "AE", "不属于任何店铺")
        );

        InOrder ordered = inOrder(mapper);
        ordered.verify(mapper).deactivateActiveSkuPurchaseBatchesForOrder(307L, 93001L, 307L);
        ordered.verify(mapper).deactivateActiveSkuPurchaseBatchSourcesForOrder(307L, 93001L, 307L);
        ordered.verify(mapper).deactivateActiveProductLinksForOrder(307L, 93001L, 307L);
        ordered.verify(mapper).revokeActiveOrderAssignmentsForOrder(307L, 93001L, 307L);
        ordered.verify(mapper).softDeleteOrderHeader(93001L, 307L, 307L, "不属于任何店铺");
        ordered.verify(mapper).softDeleteOrderItems(93001L);
        ordered.verify(mapper).softDeleteOrderLogistics(93001L);
        assertThat(result.getStatus()).isEqualTo("deleted");
    }

    @Test
    void deleteHistoricalOrderRejectsStoreOutsideSessionScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ245027");

        assertThatThrownBy(() -> service.deleteHistoricalOrder(
                context,
                93001L,
                deleteOrderRequest("PRJ108065", "AE", "不属于任何店铺")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(mapper, never()).softDeleteOrderHeader(any(), any(), any(), any());
    }

    @Test
    void createDevAuthorizationPersistsOwnerScopeAndAudit() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();
        ArgumentCaptor<Ali1688HistoricalOrderAuthorizationRow> captor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderAuthorizationRow.class);

        when(mapper.nextAuthorizationId()).thenReturn(91001L);

        Ali1688HistoricalOrderWorkbenchView view = service.createDevAuthorization(context);

        verify(mapper).insertAuthorization(captor.capture());
        Ali1688HistoricalOrderAuthorizationRow inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(91001L);
        assertThat(inserted.getOwnerUserId()).isEqualTo(307L);
        assertThat(inserted.getProviderCode()).isEqualTo("ALI1688_DEV");
        assertThat(inserted.getAccountLabel()).isEqualTo("1688 开发授权账号");
        assertThat(inserted.getStatus()).isEqualTo("authorized");
        assertThat(inserted.getScopeSummary()).contains("历史订单");
        assertThat(inserted.getCreatedBy()).isEqualTo(307L);
        assertThat(view.getAuthorization().getStatus()).isEqualTo("authorized");
        assertThat(view.getRoleCapabilities().isCanTriggerSync()).isTrue();
    }

    @Test
    void createExcelUploadSourcePersistsExplicitCurrentStoreBinding() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.SourceCreateRequest request =
                new Ali1688HistoricalOrderExcelImportView.SourceCreateRequest();
        request.setAccountLabel("沁雪冰菏 Excel 导入");
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");
        ArgumentCaptor<Ali1688HistoricalOrderAuthorizationRow> authCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderAuthorizationRow.class);

        when(mapper.selectAuthorizationByProviderAccount(
                307L,
                LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE,
                "excel-upload:307:沁雪冰菏 Excel 导入"
        )).thenReturn(null);
        when(mapper.nextAuthorizationId()).thenReturn(91008L);
        when(mapper.nextOrderStoreBindingId()).thenReturn(96008L);

        Ali1688HistoricalOrderExcelImportView.SourceView view =
                service.createExcelUploadSource(context, request);

        verify(mapper).insertAuthorization(authCaptor.capture());
        Ali1688HistoricalOrderAuthorizationRow inserted = authCaptor.getValue();
        assertThat(inserted.getId()).isEqualTo(91008L);
        assertThat(inserted.getOwnerUserId()).isEqualTo(307L);
        assertThat(inserted.getProviderCode()).isEqualTo(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        assertThat(inserted.getProviderAccountId()).isEqualTo("excel-upload:307:沁雪冰菏 Excel 导入");
        assertThat(inserted.getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        assertThat(inserted.getScopeSummary()).contains("Excel");
        verify(mapper).insertExplicitStoreBinding(
                96008L,
                307L,
                91008L,
                "PRJ108065",
                "AE",
                307L,
                "Excel 上传来源绑定到当前店铺范围。"
        );
        assertThat(view.getAuthorizationId()).isEqualTo(91008L);
        assertThat(view.getProviderCode()).isEqualTo(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        assertThat(view.getStoreCode()).isEqualTo("PRJ108065");
        assertThat(view.getSiteCode()).isEqualTo("AE");
    }

    @Test
    void createExcelUploadSourceRejectsStoreOutsideSessionScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ245027");
        Ali1688HistoricalOrderExcelImportView.SourceCreateRequest request =
                new Ali1688HistoricalOrderExcelImportView.SourceCreateRequest();
        request.setAccountLabel("错误店铺");
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");

        assertThatThrownBy(() -> service.createExcelUploadSource(context, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void listExcelUploadSourcesUsesExplicitCurrentStoreScope() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);
        source.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        source.setAccountLabel("沁雪冰菏 Excel 导入");

        when(mapper.listExcelUploadAuthorizations(
                307L,
                LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE,
                "PRJ108065",
                "AE"
        )).thenReturn(List.of(source));

        List<Ali1688HistoricalOrderExcelImportView.SourceView> sources =
                service.listExcelUploadSources(context, "PRJ108065", "AE");

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getAuthorizationId()).isEqualTo(91008L);
        assertThat(sources.get(0).getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        assertThat(sources.get(0).getStoreCode()).isEqualTo("PRJ108065");
        assertThat(sources.get(0).getSiteCode()).isEqualTo("AE");
    }

    @Test
    void buildWorkbenchShowsExcelImportedRowsForCurrentStoreScopeWithSourceContext() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);
        source.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        source.setAccountLabel("沁雪冰菏 Excel 导入");
        Ali1688HistoricalOrderQuery query = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                "PRJ108065",
                "AE",
                1,
                20
        );

        when(mapper.selectCurrentAuthorization(307L)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));
        when(mapper.selectAuthorizationById(307L, 91008L)).thenReturn(source);
        when(mapper.listOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91008L)), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(orderRow(93008L, 91008L)));
        Ali1688HistoricalOrderItemRow item = missingItemRow();
        item.setOrderId(93008L);
        when(mapper.listOrderItems(307L, List.of(93008L))).thenReturn(List.of(item));
        when(mapper.countOrders(org.mockito.ArgumentMatchers.eq(307L), org.mockito.ArgumentMatchers.eq(List.of(91008L)), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(mapper.countOrderItems(307L, List.of(91008L))).thenReturn(1);

        Ali1688HistoricalOrderWorkbenchView view = service.buildWorkbench(context, query);

        assertThat(view.getAuthorization().getProviderCode())
                .isEqualTo(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        assertThat(view.getAuthorization().getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        assertThat(view.getStoreScope().getMatchedAuthorizationIds()).containsExactly("91008");
        assertThat(view.getOrders()).hasSize(1);
        assertThat(view.getOrders().get(0).getItems()).hasSize(1);
    }

    @Test
    void previewExcelImportCreatesBatchWithoutWritingOrderFacts() throws Exception {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);
        source.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        source.setAccountLabel("沁雪冰菏 Excel 导入");
        Ali1688HistoricalOrderExcelImportView.PreviewRequest request =
                new Ali1688HistoricalOrderExcelImportView.PreviewRequest();
        request.setAuthorizationId(91008L);
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sanitized-1688-order-export.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Ali1688HistoricalOrderExcelFixtureSupport.sanitizedWorkbook()
        );
        ArgumentCaptor<Ali1688HistoricalOrderExcelImportBatchRow> batchCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderExcelImportBatchRow.class);

        when(mapper.selectAuthorizationById(307L, 91008L)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));
        when(mapper.nextExcelImportBatchId()).thenReturn(97001L);

        Ali1688HistoricalOrderExcelImportView.PreviewView view =
                service.previewExcelImport(context, request, file);

        verify(mapper).insertExcelImportBatch(batchCaptor.capture());
        Ali1688HistoricalOrderExcelImportBatchRow batch = batchCaptor.getValue();
        assertThat(batch.getId()).isEqualTo(97001L);
        assertThat(batch.getOwnerUserId()).isEqualTo(307L);
        assertThat(batch.getAuthorizationId()).isEqualTo(91008L);
        assertThat(batch.getStoreCode()).isEqualTo("PRJ108065");
        assertThat(batch.getSiteCode()).isEqualTo("AE");
        assertThat(batch.getFileName()).isEqualTo("sanitized-1688-order-export.xlsx");
        assertThat(batch.getFileHash()).isNotBlank();
        assertThat(batch.getStatus()).isEqualTo("preview_ready");
        assertThat(batch.getOrderHeaderRowCount()).isEqualTo(2);
        assertThat(batch.getProductLineCount()).isEqualTo(3);
        assertThat(batch.getLogisticsLineCount()).isEqualTo(2);
        assertThat(view.getBatchId()).isEqualTo(97001L);
        assertThat(view.getStatus()).isEqualTo("preview_ready");
        assertThat(view.getSummary().getProductLineCount()).isEqualTo(3);
        verify(mapper, never()).upsertOrder(any(Ali1688HistoricalOrderRow.class));
        verify(mapper, never()).upsertOrderItem(any(Ali1688HistoricalOrderItemRow.class));
        verify(mapper, never()).upsertOrderLogistics(any(Ali1688HistoricalOrderLogisticsRow.class));
    }

    @Test
    void previewExcelImportRejectsUnsupportedFileTypesWithStableCode() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.PreviewRequest request = previewRequest();
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);

        when(mapper.selectAuthorizationById(307L, 91008L)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));

        for (String fileName : List.of("orders.csv", "orders.xls")) {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    fileName,
                    "text/plain",
                    "not-xlsx".getBytes(StandardCharsets.UTF_8)
            );
            assertThatThrownBy(() -> service.previewExcelImport(context, request, file))
                    .isInstanceOf(Ali1688HistoricalOrderExcelImportException.class)
                    .extracting("failureCode")
                    .isEqualTo("unsupported_file_type");
        }
    }

    @Test
    void previewExcelImportRejectsOversizedFileWithStableCode() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.PreviewRequest request = previewRequest();
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);
        byte[] oversizedBytes = new byte[21 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                oversizedBytes
        );

        when(mapper.selectAuthorizationById(307L, 91008L)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));

        assertThatThrownBy(() -> service.previewExcelImport(context, request, file))
                .isInstanceOf(Ali1688HistoricalOrderExcelImportException.class)
                .extracting("failureCode")
                .isEqualTo("file_too_large");
    }

    @Test
    void previewExcelImportPersistsValidationFailedBatchForHeaderMismatch() throws Exception {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.PreviewRequest request = previewRequest();
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "wrong-template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Ali1688HistoricalOrderExcelFixtureSupport.workbookWithShiftedHeaders()
        );
        ArgumentCaptor<Ali1688HistoricalOrderExcelImportBatchRow> batchCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderExcelImportBatchRow.class);

        when(mapper.selectAuthorizationById(307L, 91008L)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));
        when(mapper.nextExcelImportBatchId()).thenReturn(97002L);

        Ali1688HistoricalOrderExcelImportView.PreviewView view =
                service.previewExcelImport(context, request, file);

        verify(mapper).insertExcelImportBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().getStatus()).isEqualTo("validation_failed");
        assertThat(batchCaptor.getValue().getFailureCode()).isEqualTo("header_mismatch");
        assertThat(batchCaptor.getValue().getFailureMessage()).contains("表头");
        assertThat(view.getHeaderValidation().isValid()).isFalse();
        assertThat(view.getHeaderValidation().getMessage()).doesNotContain("脱敏地址");
    }

    @Test
    void commitExcelImportWritesPreviewRowsToHistoricalOrderFacts() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.CommitRequest request =
                new Ali1688HistoricalOrderExcelImportView.CommitRequest();
        request.setBatchId(97001L);
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");
        Ali1688HistoricalOrderExcelImportBatchRow batch = excelImportBatch(97001L, 91008L);
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(91008L);
        source.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        source.setAccountLabel("沁雪冰菏 Excel 导入");
        ArgumentCaptor<Ali1688HistoricalOrderRow> orderCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderItemRow> itemCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderLogisticsRow> logisticsCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderLogisticsRow.class);

        when(mapper.selectExcelImportBatch(307L, 97001L)).thenReturn(batch);
        when(mapper.selectAuthorizationById(307L, 91008L)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));
        when(mapper.listExcelImportRows(307L, 97001L)).thenReturn(List.of(
                excelImportRow(1, "ALI-SAFE-20260525-001", false, "脱敏仿真花束 6 支装", "ZTO000000001"),
                excelImportRow(2, "ALI-SAFE-20260525-001", true, "脱敏复古锁心本", null),
                excelImportRow(3, "ALI-SAFE-20260525-002", false, "脱敏标签贴纸", "YTO000000002")
        ));
        when(mapper.nextOrderId()).thenReturn(93011L, 93012L);
        when(mapper.nextOrderItemId()).thenReturn(94011L, 94012L, 94013L);
        when(mapper.nextOrderLogisticsId()).thenReturn(95011L, 95012L);

        Ali1688HistoricalOrderExcelImportView.CommitView view = service.commitExcelImport(context, request);

        verify(mapper, times(2)).upsertOrder(orderCaptor.capture());
        verify(mapper, times(3)).upsertOrderItem(itemCaptor.capture());
        verify(mapper, times(2)).upsertOrderLogistics(logisticsCaptor.capture());
        verify(mapper).markExcelImportBatchCommitted(97001L, 307L, 2, 3, 2, 307L);
        assertThat(view.getStatus()).isEqualTo("committed");
        assertThat(view.getCounts().getInsertedOrderCount()).isEqualTo(2);
        assertThat(view.getCounts().getInsertedItemCount()).isEqualTo(3);
        assertThat(view.getCounts().getInsertedLogisticsCount()).isEqualTo(2);
        assertThat(orderCaptor.getAllValues().get(0).getOwnerUserId()).isEqualTo(307L);
        assertThat(orderCaptor.getAllValues().get(0).getAuthorizationId()).isEqualTo(91008L);
        assertThat(orderCaptor.getAllValues().get(0).getProviderOrderNo()).isEqualTo("ALI-SAFE-20260525-001");
        assertThat(orderCaptor.getAllValues().get(0).getSupplierName()).isEqualTo("义乌脱敏源头工厂");
        assertThat(orderCaptor.getAllValues().get(0).getAdjustmentText()).isNull();
        assertThat(orderCaptor.getAllValues().get(0).getDownstreamOrderNo()).isEqualTo("DOWNSTREAM-SAFE-001");
        assertThat(itemCaptor.getAllValues()).extracting(Ali1688HistoricalOrderItemRow::getTitle)
                .containsExactly("脱敏仿真花束 6 支装", "脱敏复古锁心本", "脱敏标签贴纸");
        assertThat(logisticsCaptor.getAllValues()).extracting(Ali1688HistoricalOrderLogisticsRow::getTrackingNo)
                .containsExactly("ZTO000000001", "YTO000000002");
        verify(mapper, never()).insertSyncTask(any(Ali1688HistoricalOrderSyncTaskRow.class));
    }

    @Test
    void commitExcelImportSkipsUnchangedExistingFactsByNaturalKey() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.CommitRequest request = commitRequest();
        Ali1688HistoricalOrderExcelImportRow row =
                excelImportRow(1, "ALI-SAFE-20260525-001", false, "脱敏仿真花束 6 支装", "ZTO000000001");
        String orderNaturalKey = "91008:ALI-SAFE-20260525-001";
        String itemNaturalKey = "91008:ALI-SAFE-20260525-001:745600000001:SKU-SAFE-1:SAFE-FLOWER:红色:SINGLE-SAFE-001:2";
        String logisticsNaturalKey = "91008:ALI-SAFE-20260525-001:94011:中通快递(ZTO):ZTO000000001";
        stubCommitBatch(List.of(row));
        when(mapper.selectOrderIdByNaturalKey(307L, orderNaturalKey)).thenReturn(93011L);
        when(mapper.selectOrderRawSnapshotByNaturalKey(307L, orderNaturalKey)).thenReturn(row.getRawSnapshotJson());
        when(mapper.selectOrderItemIdByNaturalKey(307L, itemNaturalKey)).thenReturn(94011L);
        when(mapper.selectOrderItemRawSnapshotByNaturalKey(307L, itemNaturalKey)).thenReturn(row.getRawSnapshotJson());
        when(mapper.selectOrderLogisticsIdByNaturalKey(307L, logisticsNaturalKey)).thenReturn(95011L);
        when(mapper.selectOrderLogisticsRawSnapshotByNaturalKey(307L, logisticsNaturalKey)).thenReturn(row.getRawSnapshotJson());

        Ali1688HistoricalOrderExcelImportView.CommitView view = service.commitExcelImport(context, request);

        assertThat(view.getCounts().getSkippedOrderCount()).isEqualTo(1);
        assertThat(view.getCounts().getSkippedItemCount()).isEqualTo(1);
        assertThat(view.getCounts().getSkippedLogisticsCount()).isEqualTo(1);
        verify(mapper, never()).upsertOrder(any(Ali1688HistoricalOrderRow.class));
        verify(mapper, never()).upsertOrderItem(any(Ali1688HistoricalOrderItemRow.class));
        verify(mapper, never()).upsertOrderLogistics(any(Ali1688HistoricalOrderLogisticsRow.class));
    }

    @Test
    void commitExcelImportUpdatesChangedExistingFactsByNaturalKey() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.CommitRequest request = commitRequest();
        Ali1688HistoricalOrderExcelImportRow row =
                excelImportRow(1, "ALI-SAFE-20260525-001", false, "脱敏仿真花束 6 支装", "ZTO000000001");
        String orderNaturalKey = "91008:ALI-SAFE-20260525-001";
        String itemNaturalKey = "91008:ALI-SAFE-20260525-001:745600000001:SKU-SAFE-1:SAFE-FLOWER:红色:SINGLE-SAFE-001:2";
        String logisticsNaturalKey = "91008:ALI-SAFE-20260525-001:94011:中通快递(ZTO):ZTO000000001";
        stubCommitBatch(List.of(row));
        when(mapper.selectOrderIdByNaturalKey(307L, orderNaturalKey)).thenReturn(93011L);
        when(mapper.selectOrderRawSnapshotByNaturalKey(307L, orderNaturalKey)).thenReturn("{\"source\":\"old\"}");
        when(mapper.selectOrderItemIdByNaturalKey(307L, itemNaturalKey)).thenReturn(94011L);
        when(mapper.selectOrderItemRawSnapshotByNaturalKey(307L, itemNaturalKey)).thenReturn("{\"source\":\"old\"}");
        when(mapper.selectOrderLogisticsIdByNaturalKey(307L, logisticsNaturalKey)).thenReturn(95011L);
        when(mapper.selectOrderLogisticsRawSnapshotByNaturalKey(307L, logisticsNaturalKey)).thenReturn("{\"source\":\"old\"}");

        Ali1688HistoricalOrderExcelImportView.CommitView view = service.commitExcelImport(context, request);

        assertThat(view.getCounts().getUpdatedOrderCount()).isEqualTo(1);
        assertThat(view.getCounts().getUpdatedItemCount()).isEqualTo(1);
        assertThat(view.getCounts().getUpdatedLogisticsCount()).isEqualTo(1);
        verify(mapper).upsertOrder(any(Ali1688HistoricalOrderRow.class));
        verify(mapper).upsertOrderItem(any(Ali1688HistoricalOrderItemRow.class));
        verify(mapper).upsertOrderLogistics(any(Ali1688HistoricalOrderLogisticsRow.class));
    }

    @Test
    void commitExcelImportKeepsSameOrderNumberIsolatedAcrossExcelSources() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.CommitRequest request = commitRequest();
        Ali1688HistoricalOrderExcelImportRow row =
                excelImportRow(1, "ALI-SAFE-20260525-001", false, "脱敏仿真花束 6 支装", null);
        ArgumentCaptor<Ali1688HistoricalOrderRow> orderCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderItemRow> itemCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemRow.class);

        stubCommitBatch(91009L, List.of(row));
        when(mapper.nextOrderId()).thenReturn(93021L);
        when(mapper.nextOrderItemId()).thenReturn(94021L);

        Ali1688HistoricalOrderExcelImportView.CommitView view = service.commitExcelImport(context, request);

        verify(mapper).selectOrderIdByNaturalKey(307L, "91009:ALI-SAFE-20260525-001");
        verify(mapper).upsertOrder(orderCaptor.capture());
        verify(mapper).upsertOrderItem(itemCaptor.capture());
        assertThat(orderCaptor.getValue().getAuthorizationId()).isEqualTo(91009L);
        assertThat(orderCaptor.getValue().getOrderNaturalKey()).isEqualTo("91009:ALI-SAFE-20260525-001");
        assertThat(itemCaptor.getValue().getItemNaturalKey())
                .startsWith("91009:ALI-SAFE-20260525-001:");
        assertThat(view.getCounts().getInsertedOrderCount()).isEqualTo(1);
        assertThat(view.getCounts().getInsertedItemCount()).isEqualTo(1);
    }

    @Test
    void commitExcelImportDoesNotDeleteFactsOmittedFromLaterUpload() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportView.CommitRequest request = commitRequest();
        Ali1688HistoricalOrderExcelImportRow remainingRow =
                excelImportRow(1, "ALI-SAFE-20260525-001", false, "脱敏仿真花束 6 支装", null);
        String orderNaturalKey = "91008:ALI-SAFE-20260525-001";
        String itemNaturalKey = "91008:ALI-SAFE-20260525-001:745600000001:SKU-SAFE-1:SAFE-FLOWER:红色:SINGLE-SAFE-001:2";

        stubCommitBatch(List.of(remainingRow));
        when(mapper.selectOrderIdByNaturalKey(307L, orderNaturalKey)).thenReturn(93011L);
        when(mapper.selectOrderRawSnapshotByNaturalKey(307L, orderNaturalKey)).thenReturn(remainingRow.getRawSnapshotJson());
        when(mapper.selectOrderItemIdByNaturalKey(307L, itemNaturalKey)).thenReturn(94011L);
        when(mapper.selectOrderItemRawSnapshotByNaturalKey(307L, itemNaturalKey)).thenReturn(remainingRow.getRawSnapshotJson());

        Ali1688HistoricalOrderExcelImportView.CommitView view = service.commitExcelImport(context, request);

        verify(mapper, never()).upsertOrder(any(Ali1688HistoricalOrderRow.class));
        verify(mapper, never()).upsertOrderItem(any(Ali1688HistoricalOrderItemRow.class));
        verify(mapper, never()).upsertOrderLogistics(any(Ali1688HistoricalOrderLogisticsRow.class));
        verify(mapper).markExcelImportBatchCommitted(97001L, 307L, 1, 1, 0, 307L);
        assertThat(view.getCounts().getSkippedOrderCount()).isEqualTo(1);
        assertThat(view.getCounts().getSkippedItemCount()).isEqualTo(1);
    }

    @Test
    void listExcelImportBatchesReturnsSafeAuditViewsForCurrentStoreScope() throws Exception {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportBatchRow batch = excelImportBatch(97001L, 91008L);
        batch.setFileName("sanitized-1688-order-export.xlsx");
        batch.setFileSize(128_000L);
        batch.setFileHash("safe-sha256");
        batch.setHeaderVersion("ali1688_historical_order_export_v1");
        batch.setOrderHeaderRowCount(2);
        batch.setProductLineCount(3);
        batch.setLogisticsLineCount(2);
        batch.setErrorCount(0);
        batch.setWarningCount(1);
        batch.setCreatedBy(307L);
        batch.setCreatedAt("2026-05-26 12:00:00");
        batch.setAccountLabel("沁雪冰菏 Excel 导入");
        batch.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);

        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));
        when(mapper.listExcelImportBatches(307L, List.of(91008L), "PRJ108065", "AE", 20))
                .thenReturn(List.of(batch));

        List<Ali1688HistoricalOrderExcelImportView.BatchView> view =
                service.listExcelImportBatches(context, "PRJ108065", "AE");

        assertThat(view).hasSize(1);
        assertThat(view.get(0).getBatchId()).isEqualTo(97001L);
        assertThat(view.get(0).getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        assertThat(view.get(0).getFileHash()).isEqualTo("safe-sha256");
        assertThat(view.get(0).getCreatedAt()).isEqualTo("2026-05-26 12:00:00");

        String serialized = new ObjectMapper().writeValueAsString(view.get(0));
        assertThat(serialized)
                .contains("safe-sha256")
                .doesNotContain("rawSnapshotJson")
                .doesNotContain("fileBytes")
                .doesNotContain("13800000000")
                .doesNotContain("浙江省杭州市脱敏地址");
    }

    @Test
    void excelImportBatchDetailReturnsSafeFailureSummaryWithoutRawRowsOrOriginalBytes() throws Exception {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContextWithStores("PRJ108065");
        Ali1688HistoricalOrderExcelImportBatchRow batch = excelImportBatch(97002L, 91008L);
        batch.setStatus("validation_failed");
        batch.setFileName("wrong-template.xlsx");
        batch.setFileHash("safe-failed-hash");
        batch.setFailureCode("header_mismatch");
        batch.setFailureMessage("表头不匹配，请重新导出 1688 历史订单 Excel。");
        batch.setErrorSummaryJson("{\"rowErrors\":2,\"rowWarnings\":0}");
        batch.setAccountLabel("沁雪冰菏 Excel 导入");
        batch.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        batch.setCreatedAt("2026-05-26 12:05:00");

        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(91008L));
        when(mapper.selectExcelImportBatchForDetail(307L, 97002L, List.of(91008L), "PRJ108065", "AE"))
                .thenReturn(batch);

        Ali1688HistoricalOrderExcelImportView.BatchDetailView detail =
                service.excelImportBatchDetail(context, 97002L, "PRJ108065", "AE");

        assertThat(detail.getBatchId()).isEqualTo(97002L);
        assertThat(detail.getFailureCode()).isEqualTo("header_mismatch");
        assertThat(detail.getErrorSummaryJson()).contains("rowErrors");

        String serialized = new ObjectMapper().writeValueAsString(detail);
        assertThat(serialized)
                .contains("wrong-template.xlsx")
                .doesNotContain("rawSnapshotJson")
                .doesNotContain("fileBytes")
                .doesNotContain("receiverAddress")
                .doesNotContain("13800000000");
    }

    @Test
    void revokeAuthorizationMarksCurrentOwnerAuthorizationRevoked() {
        LocalDbAli1688HistoricalOrderService service = new LocalDbAli1688HistoricalOrderService(mapper);
        BusinessAccessContext context = bossContext();

        Ali1688HistoricalOrderWorkbenchView view = service.revokeAuthorization(context, 91001L);

        verify(mapper).revokeAuthorization(91001L, 307L, 307L);
        assertThat(view.getAuthorization().getStatus()).isEqualTo("not_authorized");
        assertThat(view.getRoleCapabilities().isCanTriggerSync()).isFalse();
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

    private BusinessAccessContext bossContextWithStores(String... storeCodes) {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleName("老板")
                .roleLevel(1)
                .storeCodes(Set.of(storeCodes))
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(409L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName("运营")
                .roleLevel(3)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private BusinessAccessContext readOnlyRoleContext(String roleName) {
        return operatorRoleContext(roleName);
    }

    private BusinessAccessContext operatorRoleContext(String roleName) {
        return BusinessAccessContext.builder()
                .sessionUserId(409L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName(roleName)
                .roleLevel(3)
                .storeCodes(Set.of("PRJ108065"))
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private Ali1688HistoricalOrderExcelImportView.PreviewRequest previewRequest() {
        Ali1688HistoricalOrderExcelImportView.PreviewRequest request =
                new Ali1688HistoricalOrderExcelImportView.PreviewRequest();
        request.setAuthorizationId(91008L);
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");
        return request;
    }

    private Ali1688HistoricalOrderExcelImportView.CommitRequest commitRequest() {
        Ali1688HistoricalOrderExcelImportView.CommitRequest request =
                new Ali1688HistoricalOrderExcelImportView.CommitRequest();
        request.setBatchId(97001L);
        request.setStoreCode("PRJ108065");
        request.setSiteCode("AE");
        return request;
    }

    private Ali1688HistoricalOrderAssignmentView.AssignRequest assignmentRequest(
            String targetStoreCode,
            String targetSiteCode,
            Long itemId,
            Integer quantity
    ) {
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                new Ali1688HistoricalOrderAssignmentView.AssignRequest();
        request.setTargetStoreCode(targetStoreCode);
        request.setTargetSiteCode(targetSiteCode);
        Ali1688HistoricalOrderAssignmentView.AssignLineRequest line =
                new Ali1688HistoricalOrderAssignmentView.AssignLineRequest();
        line.setItemId(itemId);
        line.setQuantity(quantity);
        request.setLines(List.of(line));
        return request;
    }

    private Ali1688HistoricalOrderAssignmentView.AssignRequest consumableAssignmentRequest(Long itemId) {
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                new Ali1688HistoricalOrderAssignmentView.AssignRequest();
        request.setTargetType("CONSUMABLE");
        Ali1688HistoricalOrderAssignmentView.AssignLineRequest line =
                new Ali1688HistoricalOrderAssignmentView.AssignLineRequest();
        line.setItemId(itemId);
        request.setLines(List.of(line));
        return request;
    }

    private Ali1688HistoricalOrderAssignmentView.AssignRequest discontinuedAssignmentRequest(
            String targetStoreCode,
            String targetSiteCode,
            Long itemId,
            Integer quantity
    ) {
        Ali1688HistoricalOrderAssignmentView.AssignRequest request =
                assignmentRequest(targetStoreCode, targetSiteCode, itemId, quantity);
        request.setTargetType("DISCONTINUED");
        return request;
    }

    private Ali1688HistoricalOrderAssignmentView.AdjustRequest adjustRequest(Integer quantity) {
        Ali1688HistoricalOrderAssignmentView.AdjustRequest request =
                new Ali1688HistoricalOrderAssignmentView.AdjustRequest();
        request.setQuantity(quantity);
        return request;
    }

    private Ali1688HistoricalOrderCleanupView.DeleteOrderRequest deleteOrderRequest(
            String storeCode,
            String siteCode,
            String reason
    ) {
        Ali1688HistoricalOrderCleanupView.DeleteOrderRequest request =
                new Ali1688HistoricalOrderCleanupView.DeleteOrderRequest();
        request.setStoreCode(storeCode);
        request.setSiteCode(siteCode);
        request.setReason(reason);
        return request;
    }

    private Ali1688HistoricalOrderItemAssignmentRow assignmentRow(
            Long assignmentId,
            Long itemId,
            String targetStoreCode,
            String targetSiteCode,
            Integer assignedQuantity
    ) {
        Ali1688HistoricalOrderItemAssignmentRow row = new Ali1688HistoricalOrderItemAssignmentRow();
        row.setId(assignmentId);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(91008L);
        row.setOrderId(93001L);
        row.setItemId(itemId);
        row.setTargetType(targetStoreCode == null ? "CONSUMABLE" : "STORE_SITE");
        row.setTargetStoreCode(targetStoreCode);
        row.setTargetSiteCode(targetSiteCode);
        row.setAssignedQuantity(assignedQuantity);
        row.setStatus("active");
        return row;
    }

    private Ali1688HistoricalOrderProductLinkView.LinkRequest productLinkRequest(Long assignmentId) {
        return productLinkRequest(assignmentId, "CANMAN-AE-SKU-001", "CM-AE-PARTNER-001");
    }

    private Ali1688HistoricalOrderProductLinkView.LinkRequest productLinkRequest(
            Long assignmentId,
            String skuParent,
            String partnerSku
    ) {
        Ali1688HistoricalOrderProductLinkView.LinkRequest request =
                new Ali1688HistoricalOrderProductLinkView.LinkRequest();
        request.setAssignmentId(assignmentId);
        request.setSkuParent(skuParent);
        request.setPartnerSku(partnerSku);
        request.setPskuCode("PSKU-CM-AE-001");
        request.setProductTitle("canman AE 抽纸盒");
        request.setProductImageUrl("https://img.example.com/canman-ae.jpg");
        return request;
    }

    private Ali1688HistoricalOrderProductLinkRow productLinkRow(
            Long linkId,
            Ali1688HistoricalOrderItemAssignmentRow assignment
    ) {
        Ali1688HistoricalOrderProductLinkRow row = new Ali1688HistoricalOrderProductLinkRow();
        row.setId(linkId);
        row.setOwnerUserId(assignment.getOwnerUserId());
        row.setAuthorizationId(assignment.getAuthorizationId());
        row.setOrderId(assignment.getOrderId());
        row.setItemId(assignment.getItemId());
        row.setAssignmentId(assignment.getId());
        row.setTargetStoreCode(assignment.getTargetStoreCode());
        row.setTargetSiteCode(assignment.getTargetSiteCode());
        row.setSkuParent("CANMAN-AE-SKU-001");
        row.setPartnerSku("CM-AE-PARTNER-001");
        row.setPskuCode("PSKU-CM-AE-001");
        row.setProductTitle("canman AE 抽纸盒");
        row.setProductImageUrl("https://img.example.com/canman-ae.jpg");
        row.setStatus("active");
        return row;
    }

    private Ali1688HistoricalOrderProductLinkCandidateRow productLinkCandidateRow(
            String skuParent,
            String partnerSku,
            String pskuCode,
            String productTitle,
            String linkStatus
    ) {
        Ali1688HistoricalOrderProductLinkCandidateRow row =
                new Ali1688HistoricalOrderProductLinkCandidateRow();
        row.setStoreCode("PRJ108065");
        row.setSiteCode("AE");
        row.setSkuParent(skuParent);
        row.setPartnerSku(partnerSku);
        row.setPskuCode(pskuCode);
        row.setProductTitle(productTitle);
        row.setProductImageUrl("https://img.example.com/" + skuParent + ".jpg");
        row.setLinkStatus(linkStatus);
        row.setLinkedAssignmentCount("linked".equals(linkStatus) ? 1 : 0);
        return row;
    }

    private void stubProductSkuInAssignmentTarget(
            Ali1688HistoricalOrderItemAssignmentRow assignment,
            String skuParent
    ) {
        Ali1688HistoricalOrderProductLinkCandidateRow candidate = productLinkCandidateRow(
                skuParent,
                "CANMAN-AE-SKU-NEW".equals(skuParent) ? "CM-AE-PARTNER-NEW" : "CM-AE-PARTNER-001",
                "PSKU-CM-AE-001",
                "canman AE 抽纸盒",
                "unlinked"
        );
        candidate.setProductImageUrl("https://img.example.com/canman-ae.jpg");
        stubProductSkuInAssignmentTarget(assignment, candidate);
    }

    private void stubProductSkuInAssignmentTarget(
            Ali1688HistoricalOrderItemAssignmentRow assignment,
            Ali1688HistoricalOrderProductLinkCandidateRow candidate
    ) {
        when(mapper.countProductSkuInAssignmentTarget(
                assignment.getOwnerUserId(),
                assignment.getTargetStoreCode(),
                assignment.getTargetSiteCode(),
                candidate.getSkuParent()
        )).thenReturn(1);
        when(mapper.selectOrderItemProductLinkCandidateBySkuParent(
                assignment.getOwnerUserId(),
                assignment.getTargetStoreCode(),
                assignment.getTargetSiteCode(),
                candidate.getSkuParent()
        )).thenReturn(candidate);
    }

    private Ali1688SkuPurchaseHistoryRow purchaseHistoryRow(
            Long assignmentId,
            String orderNo,
            String orderTime,
            String skuParent,
            Integer assignedQuantity,
            Integer itemQuantity,
            String itemAmountText,
            String goodsTotalText,
            String paidAmountText
    ) {
        Ali1688SkuPurchaseHistoryRow row = new Ali1688SkuPurchaseHistoryRow();
        row.setOwnerUserId(307L);
        row.setOrderId(93000L + assignmentId);
        row.setItemId(94000L + assignmentId);
        row.setAssignmentId(assignmentId);
        row.setProductLinkId(100000L + assignmentId);
        row.setStoreCode("PRJ108065");
        row.setSiteCode("AE");
        row.setSkuParent(skuParent);
        row.setPartnerSku("CM-AE-PARTNER-001");
        row.setPskuCode("PSKU-CM-AE-001");
        row.setProductTitle("canman AE 抽纸盒");
        row.setOrderNo(orderNo);
        row.setOrderTime(orderTime);
        row.setSupplierName("义乌诚信通源头工厂");
        row.setAssignedQuantity(assignedQuantity);
        row.setItemQuantity(itemQuantity);
        row.setItemAmountText(itemAmountText);
        row.setGoodsTotalText(goodsTotalText);
        row.setPaidAmountText(paidAmountText);
        return row;
    }

    private Ali1688SkuPurchaseBatchRow skuPurchaseBatchRow(
            Long batchId,
            String storeCode,
            String siteCode,
            String skuParent,
            String batchLabel,
            Integer countedQuantity,
            String countedCost,
            String note
    ) {
        Ali1688SkuPurchaseBatchRow row = new Ali1688SkuPurchaseBatchRow();
        row.setId(batchId);
        row.setOwnerUserId(307L);
        row.setStoreCode(storeCode);
        row.setSiteCode(siteCode);
        row.setSkuParent(skuParent);
        row.setPartnerSku("CM-AE-PARTNER-001");
        row.setPskuCode("PSKU-CM-AE-001");
        row.setBatchLabel(batchLabel);
        row.setBatchSequence(1);
        row.setCountedQuantity(countedQuantity);
        row.setCountedCost(new BigDecimal(countedCost));
        row.setNote(note);
        row.setStatus("active");
        return row;
    }

    private Ali1688SkuPurchaseBatchSourceRow skuPurchaseBatchSourceRow(
            Long batchId,
            Long assignmentId,
            String orderNo,
            String orderTime
    ) {
        Ali1688SkuPurchaseBatchSourceRow row = new Ali1688SkuPurchaseBatchSourceRow();
        row.setId(103000L + assignmentId);
        row.setBatchId(batchId);
        row.setOwnerUserId(307L);
        row.setOrderId(93000L + assignmentId);
        row.setItemId(94000L + assignmentId);
        row.setAssignmentId(assignmentId);
        row.setSourceOrderNo(orderNo);
        row.setSourceOrderTime(orderTime);
        row.setSupplierName("义乌诚信通源头工厂");
        row.setStatus("active");
        return row;
    }

    private void setAli1688SourceItemIdentity(
            Ali1688SkuPurchaseHistoryRow row,
            String offerId,
            String skuId,
            String productCode,
            String singleProductCode
    ) {
        row.setSourceOfferId(offerId);
        row.setSourceSkuId(skuId);
        row.setSourceProductCode(productCode);
        row.setSourceSingleProductCode(singleProductCode);
    }

    private Ali1688SkuPurchaseHistoryRow unlinkedPurchaseHistoryRow(
            Long assignmentId,
            String orderNo,
            String orderTime,
            String productTitle,
            Integer assignedQuantity,
            Integer itemQuantity,
            String itemAmountText,
            String goodsTotalText,
            String paidAmountText
    ) {
        Ali1688SkuPurchaseHistoryRow row = purchaseHistoryRow(
                assignmentId,
                orderNo,
                orderTime,
                null,
                assignedQuantity,
                itemQuantity,
                itemAmountText,
                goodsTotalText,
                paidAmountText
        );
        row.setProductTitle(productTitle);
        row.setProductImageUrl("https://img.example.com/unlinked.jpg");
        row.setSourceOfferId("586206234147");
        row.setSourceSkuId("4001301253326");
        row.setSourceProductCode("003");
        return row;
    }

    private Ali1688SkuPurchaseHistoryProductRow skuPurchaseProductRow(
            String skuParent,
            String partnerSku,
            String pskuCode,
            String productTitle
    ) {
        Ali1688SkuPurchaseHistoryProductRow row = new Ali1688SkuPurchaseHistoryProductRow();
        row.setStoreCode("PRJ108065");
        row.setSiteCode("AE");
        row.setSkuParent(skuParent);
        row.setPartnerSku(partnerSku);
        row.setPskuCode(pskuCode);
        row.setProductTitle(productTitle);
        row.setProductTitleCn(productTitle + "中文名");
        row.setProductImageUrl("https://img.example.com/product.jpg");
        return row;
    }

    private void stubCommitBatch(List<Ali1688HistoricalOrderExcelImportRow> rows) {
        stubCommitBatch(91008L, rows);
    }

    private void stubCommitBatch(Long authorizationId, List<Ali1688HistoricalOrderExcelImportRow> rows) {
        Ali1688HistoricalOrderAuthorizationRow source = authorizationRow(authorizationId);
        source.setProviderCode(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        source.setAccountLabel("沁雪冰菏 Excel 导入");
        when(mapper.selectExcelImportBatch(307L, 97001L)).thenReturn(excelImportBatch(97001L, authorizationId));
        when(mapper.selectAuthorizationById(307L, authorizationId)).thenReturn(source);
        when(mapper.listVisibleAuthorizationIds(307L, "PRJ108065", "AE")).thenReturn(List.of(authorizationId));
        when(mapper.listExcelImportRows(307L, 97001L)).thenReturn(rows);
    }

    private Ali1688HistoricalOrderExcelImportBatchRow excelImportBatch(Long id, Long authorizationId) {
        Ali1688HistoricalOrderExcelImportBatchRow row = new Ali1688HistoricalOrderExcelImportBatchRow();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(authorizationId);
        row.setStoreCode("PRJ108065");
        row.setSiteCode("AE");
        row.setStatus("preview_ready");
        row.setFileHash("safe-hash");
        return row;
    }

    private Ali1688HistoricalOrderExcelImportRow excelImportRow(
            int rowNumber,
            String orderNo,
            boolean continuation,
            String title,
            String trackingNo
    ) {
        Ali1688HistoricalOrderExcelImportRow row = new Ali1688HistoricalOrderExcelImportRow();
        row.setId(98000L + rowNumber);
        row.setBatchId(97001L);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(91008L);
        row.setRowNumber(rowNumber + 1);
        row.setContinuationRow(continuation);
        row.setOrderNo(orderNo);
        row.setBuyerCompanyName("脱敏买家公司 A");
        row.setBuyerMemberName("safe-buyer-a");
        row.setSupplierName(rowNumber == 3 ? "深圳脱敏工厂" : "义乌脱敏源头工厂");
        row.setSellerMemberName(rowNumber == 3 ? "safe-seller-b" : "safe-seller-a");
        row.setGoodsTotalText(rowNumber == 3 ? "36.00" : "128.00");
        row.setAdjustmentText("-8.00");
        row.setPaidAmountText(rowNumber == 3 ? "36.00" : "128.00");
        row.setOrderStatus(rowNumber == 3 ? "等待买家收货" : "交易成功");
        row.setOrderTime(rowNumber == 3 ? "2026-05-25 11:00:00" : "2026-05-25 10:30:00");
        row.setPaidAt(rowNumber == 3 ? null : "2026-05-25 10:35:00");
        row.setShipperName("义乌脱敏仓");
        row.setReceiverName("测试收货人");
        row.setReceiverAddress("浙江省杭州市脱敏地址 1 号");
        row.setReceiverTelephone("057100000000");
        row.setReceiverMobile("13800000000");
        row.setBuyerRemark("脱敏留言");
        row.setTitle(title);
        row.setOfferId("74560000000" + rowNumber);
        row.setSkuId("SKU-SAFE-" + rowNumber);
        row.setProductCode(rowNumber == 2 ? "SAFE-NOTEBOOK" : rowNumber == 3 ? "SAFE-LABEL" : "SAFE-FLOWER");
        row.setModelText(rowNumber == 2 ? "B6 粉色" : rowNumber == 3 ? "白色" : "红色");
        row.setSingleProductCode("SINGLE-SAFE-00" + rowNumber);
        row.setQuantityText(rowNumber == 2 ? "5" : "10");
        row.setUnit(rowNumber == 1 ? "套" : rowNumber == 2 ? "件" : "包");
        row.setUnitPriceText(rowNumber == 2 ? "20.80" : rowNumber == 3 ? "3.60" : "12.80");
        row.setLogisticsCompany(trackingNo == null ? null : rowNumber == 3 ? "圆通速递(YTO)" : "中通快递(ZTO)");
        row.setTrackingNo(trackingNo);
        row.setSourceBatchNo(rowNumber == 3 ? null : "BATCH-SAFE-001");
        row.setDownstreamChannel("AE");
        row.setDownstreamOrderNo(rowNumber == 3 ? "DOWNSTREAM-SAFE-002" : "DOWNSTREAM-SAFE-001");
        row.setInitiatorLoginName("safe-initiator");
        row.setRawSnapshotJson("{\"source\":\"excel_upload\"}");
        return row;
    }

    private Ali1688HistoricalOrderAuthorizationRow authorizationRow(Long id) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_DEV");
        row.setAccountLabel("1688 开发授权账号");
        row.setProviderAccountId("dev-1688-307");
        row.setStatus("authorized");
        row.setScopeSummary("读取 1688 历史订单，不会付款或创建订单。");
        row.setExpiresAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        return row;
    }

    private Ali1688HistoricalOrderRow missingAndSensitiveOrderRow() {
        return orderRow(93001L, 91001L);
    }

    private Ali1688HistoricalOrderRow orderRow(Long id, Long authorizationId) {
        Ali1688HistoricalOrderRow row = new Ali1688HistoricalOrderRow();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setAuthorizationId(authorizationId);
        row.setProviderOrderNo("ALI-ORDER-20260525-MISSING");
        row.setOrderTime("2026-05-25 11:30:00");
        row.setSupplierName(null);
        row.setAmountText(null);
        row.setAmountValue((BigDecimal) null);
        row.setCurrency("CNY");
        row.setOrderStatus("已付款");
        row.setLogisticsStatus(null);
        row.setOriginalUrl(null);
        row.setReceiverPhone("13800138000");
        row.setReceiverAddress("浙江省杭州市西湖区文三路 99 号 3 幢 501 室");
        row.setBuyerRemark("周五前发货，联系采购小王");
        row.setSupplierContact("旺旺：supplier-contact");
        return row;
    }

    private Ali1688HistoricalOrderItemRow missingItemRow() {
        Ali1688HistoricalOrderItemRow row = new Ali1688HistoricalOrderItemRow();
        row.setId(94001L);
        row.setOrderId(93001L);
        row.setOfferId("745612345678");
        row.setTitle("仿真罂粟花束 6 支装 家居装饰");
        row.setSkuText(null);
        row.setQuantity(10);
        row.setUnitPriceText("¥12.80");
        row.setAmountText("¥128.00");
        row.setImageUrl(null);
        return row;
    }

    private Ali1688HistoricalOrderItemRow itemRow(Long id, Long orderId, String title, Integer quantity) {
        Ali1688HistoricalOrderItemRow row = new Ali1688HistoricalOrderItemRow();
        row.setId(id);
        row.setOrderId(orderId);
        row.setOfferId("74561234" + id);
        row.setSkuId("SKU-" + id);
        row.setTitle(title);
        row.setSkuText("默认规格");
        row.setQuantity(quantity);
        row.setUnit("件");
        row.setUnitPriceText("¥10.00");
        row.setAmountText(quantity == null ? null : "¥" + (quantity * 10) + ".00");
        return row;
    }

    private Ali1688HistoricalOrderItemAssignmentSummaryRow assignmentSummary(Long itemId, Integer assignedQuantity) {
        return assignmentSummary(itemId, assignedQuantity, null);
    }

    private Ali1688HistoricalOrderItemAssignmentSummaryRow assignmentSummary(
            Long itemId,
            Integer assignedQuantity,
            String assignmentBreakdownText
    ) {
        Ali1688HistoricalOrderItemAssignmentSummaryRow row = new Ali1688HistoricalOrderItemAssignmentSummaryRow();
        row.setItemId(itemId);
        row.setAssignedQuantity(assignedQuantity);
        row.setAssignmentBreakdownText(assignmentBreakdownText);
        return row;
    }
}
