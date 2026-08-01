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
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteReportExportView;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehousePurchaseQuoteExportContractTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseLogisticsQuotePriceService priceService;
    private LocalDbProcurementPurchaseOrderService service;
    private ForwarderRouteRecommendationRecord candidate;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        priceService = mock(WarehouseLogisticsQuotePriceService.class);
        service = ProcurementPurchaseOrderServiceTestFactory.create(
                mapper,
                mock(ProductSelectionMapper.class),
                mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(),
                priceService
        );
        candidate = candidate();
        when(mapper.selectOrderByIdForUpdate(200001L)).thenReturn(order());
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR"))
                .thenReturn(List.of(candidate));
        when(mapper.lockProductVariantsForForwarderEligibility(eq(307L), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        when(mapper.refreshLogisticsQuoteLineSnapshot(any(), eq(307L))).thenReturn(1);
        when(mapper.persistLogisticsQuoteLineSelection(any(), eq(307L))).thenReturn(1);
        when(mapper.markLogisticsQuoteLinesExported(eq(200001L), any(), eq(307L)))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(1)).size());
        when(mapper.nextOperationLogId()).thenReturn(240001L);
    }

    @Test
    void allCurrentPricesExportAndPersistTheExplicitChannel() {
        PurchaseOrderLogisticsQuoteLineRecord first = line(280001L, 220001L, 9001L);
        PurchaseOrderLogisticsQuoteLineRecord second = line(280002L, 220002L, 9002L);
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(first, second));
        when(priceService.resolve(any(), eq(candidate), any())).thenAnswer(invocation ->
                price((PurchaseOrderLogisticsQuoteLineRecord) invocation.getArgument(0), "67"));

        PurchaseOrderLogisticsQuoteReportExportView result = service.exportLogisticsQuoteReport(
                access(), "200001", "ET", "ET-SA-AIR");

        assertThat(result.rowCount).isEqualTo(2);
        assertThat(result.pendingCount).isZero();
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> persisted =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper, org.mockito.Mockito.times(2))
                .persistLogisticsQuoteLineSelection(persisted.capture(), eq(307L));
        assertThat(persisted.getAllValues()).allSatisfy(line -> {
            assertThat(line.forwarderCode).isEqualTo("ET");
            assertThat(line.routeCode).isEqualTo("ET-SA-AIR");
            assertThat(line.unitPrice).isEqualByComparingTo("67");
        });
        verify(mapper).markLogisticsQuoteLinesExported(
                200001L, List.of(280001L, 280002L), 307L);
    }

    @Test
    void mixedCurrentAndMissingPricesExportAllRowsAndClearOldChannelFacts() {
        PurchaseOrderLogisticsQuoteLineRecord priced = line(280001L, 220001L, 9001L);
        PurchaseOrderLogisticsQuoteLineRecord missing = line(280002L, 220002L, 9002L);
        missing.forwarderCode = "OLD";
        missing.routeCode = "OLD-SA-AIR";
        missing.unitPrice = new BigDecimal("99");
        missing.currency = "USD";
        missing.billingUnit = "LB";
        missing.estimatedAmount = new BigDecimal("990");
        missing.remark = "旧渠道备注";
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(priced, missing));
        when(priceService.resolve(any(), eq(candidate), any())).thenAnswer(invocation -> {
            PurchaseOrderLogisticsQuoteLineRecord line = invocation.getArgument(0);
            return line.purchaseOrderItemSiteId.equals(220001L) ? price(line, "67") : price(line, null);
        });

        PurchaseOrderLogisticsQuoteReportExportView result = service.exportLogisticsQuoteReport(
                access(), "200001", "ET", "ET-SA-AIR");

        assertThat(result.rowCount).isEqualTo(2);
        assertThat(result.pendingCount).isEqualTo(1);
        assertThat(missing.forwarderCode).isEqualTo("ET");
        assertThat(missing.unitPrice).isNull();
        assertThat(missing.currency).isNull();
        assertThat(missing.billingUnit).isNull();
        assertThat(missing.estimatedAmount).isNull();
        assertThat(missing.remark).isNull();
    }

    @Test
    void allSubmittedRowsAreRejectedWithoutSelectionWrites() {
        PurchaseOrderLogisticsQuoteLineRecord submitted = line(280001L, 220001L, 9001L);
        submitted.shippingSubmitStatus = "SUBMITTED";
        when(mapper.listLogisticsQuoteCandidatesByOrder(200001L)).thenReturn(List.of(submitted));
        when(priceService.resolve(any(), eq(candidate), any())).thenReturn(price(submitted, "67"));

        assertThatThrownBy(() -> service.exportLogisticsQuoteReport(
                access(), "200001", "ET", "ET-SA-AIR"))
                .hasMessageContaining("未提交商品");

        verify(mapper, never()).persistLogisticsQuoteLineSelection(any(), anyLong());
        verify(mapper, never()).markLogisticsQuoteLinesExported(anyLong(), any(), anyLong());
    }

    private PurchaseOrderLogisticsQuoteChannelLineView price(
            PurchaseOrderLogisticsQuoteLineRecord line,
            String value
    ) {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();
        view.purchaseOrderItemSiteId = String.valueOf(line.purchaseOrderItemSiteId);
        view.partnerSku = line.partnerSku;
        if (value != null) {
            view.unitPrice = new BigDecimal(value);
            view.currency = "CNY";
            view.billingUnit = "KG";
        }
        return view;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(Long id, Long itemSiteId, Long variantId) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.ownerUserId = 307L;
        line.logicalStoreId = 301L;
        line.purchaseOrderId = 200001L;
        line.purchaseOrderItemId = itemSiteId - 10000;
        line.purchaseOrderItemSiteId = itemSiteId;
        line.productMasterId = variantId - 1000;
        line.productVariantId = variantId;
        line.partnerSku = "PSKU-" + itemSiteId;
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        line.quantity = 1;
        line.fulfillmentType = "WAREHOUSE_RECEIPT";
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        return line;
    }

    private ForwarderRouteRecommendationRecord candidate() {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.siteCode = "SA";
        candidate.transportMode = "AIR";
        candidate.forwarderCode = "ET";
        candidate.forwarderName = "易通物流";
        candidate.routeCode = "ET-SA-AIR";
        candidate.routeName = "易通空运";
        candidate.serviceCode = "ET-SA-AIR";
        candidate.serviceName = "易通空运";
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
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR-1"))
                .storeOwnerUserIds(Map.of("STR-1", 307L))
                .build();
    }
}
