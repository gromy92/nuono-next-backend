package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import com.nuono.next.productselection.LocalDbAli1688CollectionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseShippingSubmissionRouteContractTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseLogisticsQuotePriceService priceService;
    private LocalDbProcurementPurchaseOrderService service;
    private PurchaseOrderLogisticsQuoteLineRecord line;
    private ShippingOrderSegmentRecord segment;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        priceService = mock(WarehouseLogisticsQuotePriceService.class);
        service = ProcurementPurchaseOrderServiceTestFactory.create(
                mapper, mock(ProductSelectionMapper.class), mock(LocalDbAli1688CollectionService.class),
                new ObjectMapper(), priceService);
        ShippingOrderRecord order = order();
        line = line();
        segment = segment();
        when(mapper.selectShippingOrderById(290001L)).thenReturn(order);
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        when(mapper.listShippingOrderLines(290001L)).thenReturn(List.of());
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of(segment));
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(line));
    }

    @Test
    void retiredShippingRouteFailsBeforePriceMaterializationOrWrites() {
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR")).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitShippingOrder(access(), "290001"))
                .hasMessageContaining("渠道已失效");

        verifyNoSubmissionFactWrites();
    }

    @Test
    void missingShippingSegmentFailsClosedBeforePriceMaterializationOrWrites() {
        when(mapper.listShippingOrderSegments(290001L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.submitShippingOrder(access(), "290001"))
                .hasMessageContaining("承运状态缺失或异常");

        verifyNoSubmissionFactWrites();
        verify(mapper, never()).listRouteRecommendationCandidates(any(), any());
    }

    @Test
    void changedShippingServiceIdentityFailsBeforePriceMaterializationOrWrites() {
        ForwarderRouteRecommendationRecord current = candidate();
        current.serviceCode = "ET-SA-AIR-NEW";
        when(mapper.listRouteRecommendationCandidates(List.of("SA"), "AIR"))
                .thenReturn(List.of(current));

        assertThatThrownBy(() -> service.submitShippingOrder(access(), "290001"))
                .hasMessageContaining("渠道已失效");

        verifyNoSubmissionFactWrites();
    }

    private void verifyNoSubmissionFactWrites() {
        verify(priceService, never()).resolve(any(), any(), any());
        verify(mapper, never()).nextLogisticsQuoteLineId();
        verify(mapper, never()).insertLogisticsQuoteLine(any(), anyLong());
        verify(mapper, never()).confirmLogisticsQuoteLine(any(), anyLong());
        verify(mapper, never()).submitLogisticsQuoteLinesForShippingOrder(anyLong(), anyLong());
    }

    private ShippingOrderRecord order() {
        ShippingOrderRecord order = new ShippingOrderRecord();
        order.id = 290001L;
        order.ownerUserId = 307L;
        order.shippingOrderNo = "SO-290001";
        order.shippingSubmitStatus = "NOT_SUBMITTED";
        return order;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line() {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = 280001L;
        line.shippingOrderId = 290001L;
        line.shippingOrderSegmentId = 292001L;
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        line.forwarderCode = "ET";
        line.routeCode = "ET-SA-AIR";
        line.serviceCode = "ET-SA-AIR-SVC";
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        return line;
    }

    private ShippingOrderSegmentRecord segment() {
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = 292001L;
        segment.shippingOrderId = 290001L;
        segment.siteCode = "SA";
        segment.transportMode = "AIR";
        segment.forwarderCode = "ET";
        segment.routeCode = "ET-SA-AIR";
        segment.serviceCode = "ET-SA-AIR-SVC";
        segment.shippingSubmitStatus = "NOT_SUBMITTED";
        return segment;
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

    private BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .build();
    }
}
