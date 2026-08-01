package com.nuono.next.procurementorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineEligibilityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderLineRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ShippingOrderSegmentRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class WarehouseForwarderEligibilityWorkflowTest {

    @Test
    void locksOrderThenSharedVariantBeforeReadingOrWritingRule() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityService eligibilityService = mock(WarehouseForwarderEligibilityService.class);
        WarehouseLogisticsQuoteOptionService optionService = mock(WarehouseLogisticsQuoteOptionService.class);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper, eligibilityService, optionService);
        ShippingOrderRecord visible = order(290001L);
        ShippingOrderRecord locked = order(290001L);
        ShippingOrderLineRecord shippingLine = new ShippingOrderLineRecord();
        shippingLine.id = 291001L;
        shippingLine.productVariantId = 9001L;
        shippingLine.shippingSubmitStatus = "NOT_SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord quoteLine = new PurchaseOrderLogisticsQuoteLineRecord();
        quoteLine.shippingOrderLineId = 291001L;
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = "ET";
        LogisticsQuoteExportOption option = new LogisticsQuoteExportOption();
        option.candidate = candidate;
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.forwarderCode = "ET";
        command.eligibilityStatus = "UNSUPPORTED";
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(locked);
        when(mapper.selectShippingOrderLineById(290001L, 291001L, 307L)).thenReturn(shippingLine);
        when(mapper.lockProductVariantsForForwarderEligibility(307L, List.of(9001L)))
                .thenReturn(List.of(9001L));
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(quoteLine));
        when(optionService.collect(List.of(quoteLine))).thenReturn(List.of(option));

        workflow.updateRule(visible, 291001L, command, 307L);

        InOrder locksBeforeReads = inOrder(mapper, optionService, eligibilityService);
        locksBeforeReads.verify(mapper).selectShippingOrderByIdForUpdate(290001L, 307L);
        locksBeforeReads.verify(mapper).selectShippingOrderLineById(290001L, 291001L, 307L);
        locksBeforeReads.verify(mapper).lockProductVariantsForForwarderEligibility(307L, List.of(9001L));
        locksBeforeReads.verify(mapper).listLogisticsQuoteCandidatesByShippingOrder(290001L);
        locksBeforeReads.verify(optionService).collect(List.of(quoteLine));
        locksBeforeReads.verify(eligibilityService).updateRule(quoteLine, "ET", command, 307L);
    }

    @Test
    void deduplicatesAndSortsSharedVariantLocksAcrossOrders() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper,
                mock(WarehouseForwarderEligibilityService.class),
                mock(WarehouseLogisticsQuoteOptionService.class)
        );
        when(mapper.lockProductVariantsForForwarderEligibility(307L, List.of(8001L, 9001L)))
                .thenReturn(List.of(8001L, 9001L));

        workflow.lockEligibilityScopes(307L, List.of(9001L, 8001L, 9001L));

        verify(mapper).lockProductVariantsForForwarderEligibility(307L, List.of(8001L, 9001L));
    }

    @Test
    void suppressesHistoricalPriceWhenCurrentRuleIsUnsupported() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityService eligibilityService =
                new WarehouseForwarderEligibilityService(mapper);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper, eligibilityService, mock(WarehouseLogisticsQuoteOptionService.class));
        ShippingOrderLineRecord line = new ShippingOrderLineRecord();
        line.id = 291001L;
        line.shippingOrderSegmentId = 292001L;
        line.ownerUserId = 307L;
        line.productVariantId = 9001L;
        line.partnerSku = "PSKU-1";
        line.siteCode = "SA";
        line.plannedTransportMode = "SEA";
        line.shippingSubmitStatus = "NOT_SUBMITTED";
        line.quoteStatus = "CONFIRMED";
        line.unitPrice = new BigDecimal("1540.0000");
        line.currency = "RMB";
        line.billingUnit = "CBM";
        ShippingOrderSegmentRecord segment = new ShippingOrderSegmentRecord();
        segment.id = 292001L;
        segment.siteCode = "SA";
        segment.transportMode = "SEA";
        segment.forwarderCode = "YT";
        ProductForwarderTransportEligibilityRecord rule = new ProductForwarderTransportEligibilityRecord();
        rule.ownerUserId = 307L;
        rule.productVariantId = 9001L;
        rule.siteCode = "SA";
        rule.forwarderCode = "YT";
        rule.transportMode = "SEA";
        rule.eligibilityStatus = "UNSUPPORTED";
        when(mapper.listCurrentProductForwarderTransportEligibilities(307L, List.of(9001L)))
                .thenReturn(List.of(rule));

        workflow.applyToShippingLines(List.of(line), List.of(segment));

        assertEquals("UNSUPPORTED", line.eligibilityStatus);
        assertEquals("PENDING_QUOTE", line.quoteStatus);
        assertNull(line.unitPrice);
        assertNull(line.currency);
        assertNull(line.billingUnit);
    }

    private static ShippingOrderRecord order(Long id) {
        ShippingOrderRecord order = new ShippingOrderRecord();
        order.id = id;
        order.ownerUserId = 307L;
        order.shippingSubmitStatus = "NOT_SUBMITTED";
        return order;
    }
}
