package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderRecord;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehousePurchaseSubmissionContractTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseLogisticsQuotePriceService priceService;
    private LocalDbProcurementPurchaseOrderService service;
    private ForwarderRouteRecommendationRecord candidate;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        priceService = mock(WarehouseLogisticsQuotePriceService.class);
        service = ProcurementPurchaseOrderServiceTestFactory.create(
                mapper, mock(ProductSelectionMapper.class), mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(), priceService);
        candidate = candidate();
        when(mapper.selectOrderByIdForUpdate(200001L)).thenReturn(order());
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR"))
                .thenReturn(List.of(candidate));
        when(mapper.lockProductVariantsForForwarderEligibility(eq(307L), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(mapper.confirmLogisticsQuoteLine(any(), eq(307L))).thenReturn(1);
        when(mapper.nextOperationLogId()).thenReturn(240001L);
    }

    @Test
    void submitRefreshesLatestPriceForPersistedExactChannel() {
        PurchaseOrderLogisticsQuoteLineRecord line = line(280001L, 9001L, "NOT_SUBMITTED");
        line.unitPrice = new BigDecimal("61");
        line.estimatedAmount = new BigDecimal("610");
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));
        when(priceService.resolve(line, candidate, line)).thenReturn(price("67"));
        when(mapper.countMissingLogisticsQuotePrices(200001L)).thenReturn(0);
        when(mapper.submitLogisticsQuoteLinesForShipping(200001L, 307L)).thenReturn(1);

        var result = service.submitShipping(access(), "200001");

        assertThat(result.submittedLineCount).isEqualTo(1);
        assertThat(line.unitPrice).isEqualByComparingTo("67");
        assertThat(line.estimatedAmount).isNull();
        verify(mapper).confirmLogisticsQuoteLine(line, 307L);
    }

    @Test
    void retiredRouteFailsBeforePriceReadOrWrite() {
        PurchaseOrderLogisticsQuoteLineRecord line = line(280001L, 9001L, "NOT_SUBMITTED");
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR")).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("渠道已失效");

        verify(priceService, never()).resolve(any(), any(), any());
        verify(mapper, never()).confirmLogisticsQuoteLine(any(), anyLong());
        verify(mapper, never()).submitLogisticsQuoteLinesForShipping(anyLong(), anyLong());
    }

    @Test
    void changedServiceIdentityFailsBeforePriceRead() {
        PurchaseOrderLogisticsQuoteLineRecord line = line(280001L, 9001L, "NOT_SUBMITTED");
        line.serviceCode = "OLD-SERVICE";
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("渠道已失效");

        verify(priceService, never()).resolve(any(), any(), any());
        verify(mapper, never()).confirmLogisticsQuoteLine(any(), anyLong());
    }

    @Test
    void replayFailsBeforeRouteOrPriceRefresh() {
        PurchaseOrderLogisticsQuoteLineRecord submitted = line(280001L, 9001L, "SUBMITTED");
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(submitted));

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("已提交仓库");

        verify(mapper, never()).listRouteRecommendationCandidates(any(), any());
        verify(priceService, never()).resolve(any(), any(), any());
        verify(mapper, never()).confirmLogisticsQuoteLine(any(), anyLong());
    }

    @Test
    void emptyPurchaseCannotBeRecordedAsSubmitted() {
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("没有可提交发货的报价行");

        verify(mapper, never()).submitLogisticsQuoteLinesForShipping(anyLong(), anyLong());
        verify(mapper, never()).nextOperationLogId();
    }

    @Test
    void latestMissingPriceIsPersistedAsMissingThenBlocksSubmission() {
        PurchaseOrderLogisticsQuoteLineRecord line = line(280001L, 9001L, "NOT_SUBMITTED");
        line.unitPrice = new BigDecimal("61");
        line.currency = "CNY";
        line.billingUnit = "KG";
        line.estimatedAmount = new BigDecimal("610");
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));
        when(priceService.resolve(line, candidate, line)).thenReturn(price(null));
        when(mapper.countMissingLogisticsQuotePrices(200001L)).thenReturn(1);

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("物流单价缺失");

        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> captured =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(captured.capture(), eq(307L));
        assertThat(captured.getValue().unitPrice).isNull();
        assertThat(captured.getValue().currency).isNull();
        assertThat(captured.getValue().billingUnit).isNull();
        assertThat(captured.getValue().estimatedAmount).isNull();
        verify(mapper, never()).submitLogisticsQuoteLinesForShipping(anyLong(), anyLong());
    }

    @Test
    void unsupportedMissingPriceReportsCarrierRejectionBeforeMissingPriceGate() {
        PurchaseOrderLogisticsQuoteLineRecord line = line(280001L, 9001L, "NOT_SUBMITTED");
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(line));
        when(priceService.resolve(line, candidate, line)).thenReturn(price(null));
        ProductForwarderTransportEligibilityRecord rule = new ProductForwarderTransportEligibilityRecord();
        rule.ownerUserId = 307L;
        rule.productVariantId = 9001L;
        rule.siteCode = "SA";
        rule.forwarderCode = "ET";
        rule.transportMode = "AIR";
        rule.eligibilityStatus = "UNSUPPORTED";
        when(mapper.listCurrentProductForwarderTransportEligibilities(307L, List.of(9001L)))
                .thenReturn(List.of(rule));

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("当前不接");

        verify(mapper, never()).countMissingLogisticsQuotePrices(anyLong());
        verify(mapper, never()).submitLogisticsQuoteLinesForShipping(anyLong(), anyLong());
    }

    @Test
    void partialSubmitCountFailsWithoutSuccessLog() {
        PurchaseOrderLogisticsQuoteLineRecord first = line(280001L, 9001L, "NOT_SUBMITTED");
        PurchaseOrderLogisticsQuoteLineRecord second = line(280002L, 9002L, "NOT_SUBMITTED");
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(first, second));
        when(priceService.resolve(any(), eq(candidate), any())).thenReturn(price("67"));
        when(mapper.countMissingLogisticsQuotePrices(200001L)).thenReturn(0);
        when(mapper.submitLogisticsQuoteLinesForShipping(200001L, 307L)).thenReturn(1);

        assertThatThrownBy(() -> service.submitShipping(access(), "200001"))
                .hasMessageContaining("提交状态已变化");

        verify(mapper, never()).nextOperationLogId();
    }

    private PurchaseOrderLogisticsQuoteChannelLineView price(String value) {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();
        if (value != null) {
            view.unitPrice = new BigDecimal(value);
            view.currency = "CNY";
            view.billingUnit = "KG";
        }
        return view;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(Long id, Long variantId, String submitStatus) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.ownerUserId = 307L;
        line.logicalStoreId = 301L;
        line.purchaseOrderId = 200001L;
        line.purchaseOrderItemSiteId = id - 60000;
        line.productVariantId = variantId;
        line.partnerSku = "PSKU-" + variantId;
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        line.forwarderCode = "ET";
        line.routeCode = "ET-SA-AIR";
        line.serviceCode = "ET-SA-AIR-SVC";
        line.shippingSubmitStatus = submitStatus;
        return line;
    }

    private ForwarderRouteRecommendationRecord candidate() {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.siteCode = "SA";
        candidate.transportMode = "AIR";
        candidate.forwarderCode = "ET";
        candidate.routeCode = "ET-SA-AIR";
        candidate.serviceCode = "ET-SA-AIR-SVC";
        return candidate;
    }

    private PurchaseOrderRecord order() {
        PurchaseOrderRecord order = new PurchaseOrderRecord();
        order.id = 200001L;
        order.ownerUserId = 307L;
        order.orderNo = "PO-200001";
        order.status = "READY";
        order.anchorStoreCodeCache = "STR-1";
        return order;
    }

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder().sessionUserId(307L).businessOwnerUserId(307L)
                .storeCodes(Set.of("STR-1")).storeOwnerUserIds(Map.of("STR-1", 307L)).build();
    }
}
