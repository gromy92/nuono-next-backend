package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WarehouseShippingQuoteAvailabilityTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseLogisticsQuotePriceService priceService;
    private WarehouseShippingQuoteChannelService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        priceService = mock(WarehouseLogisticsQuotePriceService.class);
        service = new WarehouseShippingQuoteChannelService(
                mapper, priceService, mock(WarehouseShippingQuoteProjectionService.class));
    }

    @Test
    void refreshesUnsubmittedExactLineFromLatestCurrentPriceAndClearsStaleAmount() {
        PurchaseOrderLogisticsQuoteLineRecord base = line(null, null);
        PurchaseOrderLogisticsQuoteLineRecord exact = line(280001L, new BigDecimal("61.00"));
        exact.estimatedAmount = new BigDecimal("610.00");
        ShippingOrderSegmentRecord segment = segment();
        PurchaseOrderLogisticsQuoteChannelLineView current = new PurchaseOrderLogisticsQuoteChannelLineView();
        current.unitPrice = new BigDecimal("67.00");
        current.currency = "CNY";
        current.billingUnit = "KG";
        when(priceService.resolve(eq(base), any(), any())).thenReturn(current);

        List<PurchaseOrderLogisticsQuoteLineRecord> resolved =
                service.resolveSelectedLines(List.of(base), List.of(segment), List.of(exact));

        assertThat(resolved).singleElement().satisfies(line -> {
            assertThat(line.id).isEqualTo(280001L);
            assertThat(line.unitPrice).isEqualByComparingTo("67.00");
            assertThat(line.quoteStatus).isEqualTo("CONFIRMED");
            assertThat(line.estimatedAmount).isNull();
        });
    }

    @Test
    void submittedZdMissingSnapshotRemainsFrozenDuringSelectedLineResolution() {
        PurchaseOrderLogisticsQuoteLineRecord base = line(null, null);
        PurchaseOrderLogisticsQuoteLineRecord exact = line(280001L, null);
        exact.shippingSubmitStatus = "SUBMITTED";
        exact.forwarderCode = "ZD";
        exact.routeCode = "ZD-SA-AIR";
        exact.serviceCode = "ZD-SA-AIR";
        ShippingOrderSegmentRecord segment = segment();
        segment.forwarderCode = "ZD";
        segment.routeCode = "ZD-SA-AIR";
        segment.serviceCode = "ZD-SA-AIR";

        List<PurchaseOrderLogisticsQuoteLineRecord> resolved =
                service.resolveSelectedLines(List.of(base), List.of(segment), List.of(exact));

        assertThat(resolved).singleElement().satisfies(line -> {
            assertThat(line.id).isEqualTo(280001L);
            assertThat(line.shippingSubmitStatus).isEqualTo("SUBMITTED");
            assertThat(line.quoteStatus).isEqualTo("PENDING_QUOTE");
            assertThat(line.unitPrice).isNull();
        });
        verify(priceService, never()).resolve(any(), any(), any());
    }

    @Test
    void pendingPositiveLineIsSubmittableAndMaterializesLatestExactSnapshot() {
        PurchaseOrderLogisticsQuoteLineRecord current = line(null, new BigDecimal("67.00"));
        current.quoteStatus = "PENDING_QUOTE";
        PurchaseOrderLogisticsQuoteLineRecord existing = line(280001L, new BigDecimal("61.00"));
        existing.quoteStatus = "PENDING_QUOTE";
        ShippingOrderSegmentRecord segment = segment();
        when(mapper.selectLogisticsQuoteLineByShippingOrderChannelForUpdate(
                290001L, 220002L, "QIKE", "QIKE-SA-AIR", "QIKE-SA-AIR"
        )).thenReturn(existing);
        when(mapper.refreshLogisticsQuoteLineSnapshot(any(), eq(307L))).thenReturn(1);
        when(mapper.confirmLogisticsQuoteLine(any(), eq(307L))).thenReturn(1);

        assertThatCode(() -> service.requireSubmittable(
                List.of(current), List.of(segment), "empty")).doesNotThrowAnyException();
        List<PurchaseOrderLogisticsQuoteLineRecord> facts =
                service.materializeSubmissionFacts(List.of(current), List.of(segment), 307L);

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.id).isEqualTo(280001L);
            assertThat(fact.unitPrice).isEqualByComparingTo("67.00");
            assertThat(fact.quoteStatus).isEqualTo("CONFIRMED");
            assertThat(fact.shippingOrderLineId).isEqualTo(291001L);
        });
        ArgumentCaptor<PurchaseOrderLogisticsQuoteLineRecord> confirmed =
                ArgumentCaptor.forClass(PurchaseOrderLogisticsQuoteLineRecord.class);
        verify(mapper).confirmLogisticsQuoteLine(confirmed.capture(), eq(307L));
        assertThat(confirmed.getValue().unitPrice).isEqualByComparingTo("67.00");
    }

    @Test
    void missingNonZdPriceRemainsBlocking() {
        PurchaseOrderLogisticsQuoteLineRecord missing = line(null, null);

        assertThatThrownBy(() -> service.requireSubmittable(
                List.of(missing), List.of(segment()), "empty"))
                .hasMessageContaining("物流报价缺失");
    }

    @Test
    void forwarderNameAloneDoesNotGrantZdPriceExemption() {
        PurchaseOrderLogisticsQuoteLineRecord missing = line(null, null);
        missing.forwarderName = "众鸫供应链";
        ShippingOrderSegmentRecord segment = segment();
        segment.forwarderName = "众东物流";

        assertThatThrownBy(() -> service.requireSubmittable(
                List.of(missing), List.of(segment), "empty"))
                .hasMessageContaining("物流报价缺失");
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(Long id, BigDecimal price) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.ownerUserId = 307L;
        line.shippingOrderId = 290001L;
        line.shippingOrderSegmentId = 292001L;
        line.shippingOrderLineId = 291001L;
        line.purchaseOrderItemSiteId = 220002L;
        line.forwarderCode = "QIKE";
        line.routeCode = "QIKE-SA-AIR";
        line.serviceCode = "QIKE-SA-AIR";
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        line.unitPrice = price;
        return line;
    }

    private ShippingOrderSegmentRecord segment() {
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = 292001L;
        segment.forwarderCode = "QIKE";
        segment.routeCode = "QIKE-SA-AIR";
        segment.serviceCode = "QIKE-SA-AIR";
        return segment;
    }
}
