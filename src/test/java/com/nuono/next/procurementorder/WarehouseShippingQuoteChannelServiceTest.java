package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.PurchaseOrderLogisticsQuoteChannelLineView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehouseShippingQuoteChannelServiceTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseLogisticsQuotePriceService priceService;
    private WarehouseShippingQuoteChannelService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        priceService = mock(WarehouseLogisticsQuotePriceService.class);
        service = new WarehouseShippingQuoteChannelService(
                mapper,
                priceService,
                mock(WarehouseShippingQuoteProjectionService.class)
        );
    }

    @Test
    void createsIndependentWarehouseQuoteRowForAnotherChannel() {
        PurchaseOrderLogisticsQuoteLineRecord base = line(null, null, null, null);
        ForwarderRouteRecommendationRecord qike = candidate(
                "QIKE",
                "QIKE-SAU-AIR-FBN-RUH-20260523",
                "QIKE-SAU-AIR-FBN-RUH-20260523"
        );
        when(mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                290001L,
                220002L,
                qike.forwarderCode,
                qike.routeCode,
                qike.serviceCode
        )).thenReturn(null);
        when(mapper.nextLogisticsQuoteLineId()).thenReturn(280003L);

        PurchaseOrderLogisticsQuoteLineRecord created = service.requireChannelLine(base, qike, 307L);

        assertThat(created.id).isEqualTo(280003L);
        assertThat(created.forwarderCode).isEqualTo("QIKE");
        assertThat(created.routeCode).isEqualTo("QIKE-SAU-AIR-FBN-RUH-20260523");
        assertThat(created.quoteStatus).isEqualTo("PENDING_QUOTE");
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> inserted =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).insertLogisticsQuoteLine(inserted.capture(), eq(307L));
        assertThat(inserted.getValue()).isSameAs(created);
    }

    @Test
    void resolvesEachCandidateFromItsOwnConfirmedWarehouseQuote() {
        PurchaseOrderLogisticsQuoteLineRecord base = line(null, null, null, null);
        PurchaseOrderLogisticsQuoteLineRecord zd = line(
                280001L,
                "ZD",
                "ZD-SAU-AIR-FBN-RUH",
                "ZD-SAU-AIR-FBN-RUH"
        );
        PurchaseOrderLogisticsQuoteLineRecord qike = line(
                280002L,
                "QIKE",
                "QIKE-SAU-AIR-FBN-RUH-20260523",
                "QIKE-SAU-AIR-FBN-RUH-20260523"
        );
        ForwarderRouteRecommendationRecord zdCandidate =
                candidate("ZD", "ZD-SAU-AIR-FBN-RUH", "ZD-SAU-AIR-FBN-RUH");
        ForwarderRouteRecommendationRecord qikeCandidate = candidate(
                "QIKE",
                "QIKE-SAU-AIR-FBN-RUH-20260523",
                "QIKE-SAU-AIR-FBN-RUH-20260523"
        );
        PurchaseOrderLogisticsQuoteChannelLineView zdView =
                new PurchaseOrderLogisticsQuoteChannelLineView();
        PurchaseOrderLogisticsQuoteChannelLineView qikeView =
                new PurchaseOrderLogisticsQuoteChannelLineView();
        when(priceService.resolve(base, zdCandidate, zd)).thenReturn(zdView);
        when(priceService.resolve(base, qikeCandidate, qike)).thenReturn(qikeView);
        Map<Long, List<PurchaseOrderLogisticsQuoteLineRecord>> confirmations =
                Map.of(290001L, List.of(zd, qike));

        assertThat(service.resolvePrice(base, zdCandidate, confirmations)).isSameAs(zdView);
        assertThat(service.resolvePrice(base, qikeCandidate, confirmations)).isSameAs(qikeView);
    }

    @Test
    void materializesPriceExemptZdChannelBeforeSubmission() {
        PurchaseOrderLogisticsQuoteLineRecord base = line(null, null, null, null);
        base.shippingOrderSegmentId = 292001L;
        ShippingOrderSegmentRecord segment = segment(292001L, "CHIC", "ZD-SAU-AIR-FBN-RUH", null);
        when(mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                290001L,
                220002L,
                "CHIC",
                "ZD-SAU-AIR-FBN-RUH",
                null
        )).thenReturn(null);
        when(mapper.nextLogisticsQuoteLineId()).thenReturn(280003L);

        List<PurchaseOrderLogisticsQuoteLineRecord> facts =
                service.materializeSubmissionFacts(List.of(base), List.of(segment), 307L);

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.id).isEqualTo(280003L);
            assertThat(fact.quoteStatus).isEqualTo("CONFIRMED");
            assertThat(fact.forwarderCode).isEqualTo("CHIC");
            assertThat(fact.routeCode).isEqualTo("ZD-SAU-AIR-FBN-RUH");
        });
        verify(mapper).insertLogisticsQuoteLine(facts.get(0), 307L);
        verify(mapper).confirmLogisticsQuoteLine(facts.get(0), 307L);
    }

    @Test
    void refreshesEverySelectedSegmentUsingItsOwnChannel() {
        ShippingOrderSegmentRecord air = segment(292001L, "ZD", "ZD-SAU-AIR-FBN-RUH", "AIR");
        ShippingOrderSegmentRecord sea = segment(292002L, "YT", "YT-SAU-SEA-FBN-RUH", "SEA");

        service.refreshSelectedSegmentStates(
                290001L,
                307L,
                List.of(air, sea),
                java.util.Set.of(),
                307L
        );

        verify(mapper).refreshShippingOrderSegmentState(
                eq(290001L),
                eq(List.of(292001L)),
                org.mockito.ArgumentMatchers.argThat(line ->
                        "ZD".equals(line.forwarderCode) && "ZD-SAU-AIR-FBN-RUH".equals(line.routeCode)),
                eq(307L)
        );
        verify(mapper).refreshShippingOrderSegmentState(
                eq(290001L),
                eq(List.of(292002L)),
                org.mockito.ArgumentMatchers.argThat(line ->
                        "YT".equals(line.forwarderCode) && "YT-SAU-SEA-FBN-RUH".equals(line.routeCode)),
                eq(307L)
        );
        verify(mapper).refreshShippingOrderHeaderState(290001L, 307L, 307L);
    }

    private static PurchaseOrderLogisticsQuoteLineRecord line(
            Long id,
            String forwarderCode,
            String routeCode,
            String serviceCode
    ) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.shippingOrderId = 290001L;
        line.shippingOrderLineId = 291001L;
        line.purchaseOrderItemSiteId = 220002L;
        line.quantity = 10;
        line.forwarderCode = forwarderCode;
        line.routeCode = routeCode;
        line.serviceCode = serviceCode;
        line.unitPrice = id == null ? null : new BigDecimal(id.equals(280001L) ? "65" : "79");
        line.quoteStatus = id == null ? "PENDING_QUOTE" : "CONFIRMED";
        return line;
    }

    private static ForwarderRouteRecommendationRecord candidate(
            String forwarderCode,
            String routeCode,
            String serviceCode
    ) {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = forwarderCode;
        candidate.forwarderName = forwarderCode;
        candidate.routeCode = routeCode;
        candidate.routeName = routeCode;
        candidate.serviceCode = serviceCode;
        candidate.serviceName = serviceCode;
        candidate.currency = "CNY";
        candidate.billingUnit = "KG";
        return candidate;
    }

    private static ShippingOrderSegmentRecord segment(
            Long id,
            String forwarderCode,
            String routeCode,
            String serviceCode
    ) {
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = id;
        segment.forwarderCode = forwarderCode;
        segment.forwarderName = forwarderCode;
        segment.routeCode = routeCode;
        segment.routeName = routeCode;
        segment.serviceCode = serviceCode;
        segment.serviceName = serviceCode;
        return segment;
    }
}
