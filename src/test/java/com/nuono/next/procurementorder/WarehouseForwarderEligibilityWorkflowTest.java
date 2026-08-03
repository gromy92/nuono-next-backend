package com.nuono.next.procurementorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void locksOrderThenStableScopeAnchorBeforeReadingOrWritingRule() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityService eligibilityService = mock(WarehouseForwarderEligibilityService.class);
        WarehouseLogisticsQuoteOptionService optionService = mock(WarehouseLogisticsQuoteOptionService.class);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper, eligibilityService, optionService);
        ShippingOrderRecord visible = order(290001L);
        ShippingOrderRecord locked = order(290001L);
        ShippingOrderLineRecord shippingLine = new ShippingOrderLineRecord();
        shippingLine.id = 291001L;
        shippingLine.ownerUserId = 307L;
        shippingLine.logicalStoreId = 108065L;
        shippingLine.partnerSku = "PSKU-1";
        shippingLine.productVariantId = 9001L;
        shippingLine.shippingSubmitStatus = "NOT_SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord quoteLine = new PurchaseOrderLogisticsQuoteLineRecord();
        quoteLine.shippingOrderLineId = 291001L;
        quoteLine.ownerUserId = 307L;
        quoteLine.logicalStoreId = 108065L;
        quoteLine.partnerSku = "PSKU-1";
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = "ET";
        LogisticsQuoteExportOption option = new LogisticsQuoteExportOption();
        option.candidate = candidate;
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.forwarderCode = "ET";
        command.eligibilityStatus = "UNSUPPORTED";
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(locked);
        when(mapper.selectShippingOrderLineById(290001L, 291001L, 307L)).thenReturn(shippingLine);
        List<ProductForwarderEligibilityScopeAnchorRecord> scopes = List.of(scope(108065L, "PSKU-1"));
        when(mapper.lockProductForwarderEligibilityScopeAnchors(scopes)).thenReturn(scopes);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(quoteLine));
        when(optionService.collectForDecision(List.of(quoteLine))).thenReturn(List.of(option));

        workflow.updateRule(visible, 291001L, command, 307L);

        InOrder locksBeforeReads = inOrder(mapper, optionService, eligibilityService);
        locksBeforeReads.verify(mapper).selectShippingOrderByIdForUpdate(290001L, 307L);
        locksBeforeReads.verify(mapper).selectShippingOrderLineById(290001L, 291001L, 307L);
        locksBeforeReads.verify(mapper).ensureProductForwarderEligibilityScopeAnchors(scopes);
        locksBeforeReads.verify(mapper).lockProductForwarderEligibilityScopeAnchors(scopes);
        locksBeforeReads.verify(mapper).listLogisticsQuoteCandidatesByShippingOrder(290001L);
        locksBeforeReads.verify(optionService).collectForDecision(List.of(quoteLine));
        locksBeforeReads.verify(eligibilityService).updateRule(quoteLine, "ET", command, 307L);
    }

    @Test
    void deduplicatesAndSortsStableScopeLocksByUnsignedUtf8Order() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper,
                mock(WarehouseForwarderEligibilityService.class),
                mock(WarehouseLogisticsQuoteOptionService.class)
        );
        List<ProductForwarderEligibilityScopeAnchorRecord> requested = List.of(
                scope(108065L, "\uE000"), scope(108065L, "😀"), scope(108064L, "Z"));
        List<ProductForwarderEligibilityScopeAnchorRecord> expected = List.of(
                scope(108064L, "Z"), scope(108065L, "\uE000"), scope(108065L, "😀"));
        when(mapper.lockProductForwarderEligibilityScopeAnchors(expected)).thenReturn(expected);

        workflow.lockEligibilityScopes(307L, List.of(
                requested.get(1), requested.get(0), requested.get(2), requested.get(0)));

        verify(mapper).ensureProductForwarderEligibilityScopeAnchors(expected);
        verify(mapper).lockProductForwarderEligibilityScopeAnchors(expected);
    }

    @Test
    void missingStableIdentityAndIncompleteLockResultFailClosed() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper, mock(WarehouseForwarderEligibilityService.class),
                mock(WarehouseLogisticsQuoteOptionService.class));
        ShippingOrderLineRecord missingStore = shippingLine(291001L, null, "PSKU-1");

        assertThrows(IllegalArgumentException.class,
                () -> workflow.lockShippingLineEligibilityScopes(307L, List.of(missingStore)));

        List<ProductForwarderEligibilityScopeAnchorRecord> scopes = List.of(scope(108065L, "PSKU-1"));
        when(mapper.lockProductForwarderEligibilityScopeAnchors(scopes)).thenReturn(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> workflow.lockEligibilityScopes(307L, scopes));
    }

    @Test
    void refreshedQuoteLineMustMatchItsLockedShippingLineScope() {
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mock(ProcurementPurchaseOrderMapper.class), mock(WarehouseForwarderEligibilityService.class),
                mock(WarehouseLogisticsQuoteOptionService.class));
        ShippingOrderLineRecord locked = shippingLine(291001L, 108065L, "PSKU-1");
        PurchaseOrderLogisticsQuoteLineRecord refreshed = new PurchaseOrderLogisticsQuoteLineRecord();
        refreshed.shippingOrderLineId = 291001L;
        refreshed.ownerUserId = 307L;
        refreshed.logicalStoreId = 108065L;
        refreshed.partnerSku = "PSKU-CHANGED";

        assertThrows(IllegalArgumentException.class,
                () -> workflow.requireShippingLineScopesUnchanged(
                        307L, List.of(locked), List.of(refreshed)));
    }

    @Test
    void refreshedImportQuoteLineMustMatchOriginalBoundScope() {
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mock(ProcurementPurchaseOrderMapper.class), mock(WarehouseForwarderEligibilityService.class),
                mock(WarehouseLogisticsQuoteOptionService.class));
        PurchaseOrderLogisticsQuoteLineRecord locked = quoteLine(280001L, 220002L, "PSKU-1");
        PurchaseOrderLogisticsQuoteLineRecord refreshed = quoteLine(280001L, 220002L, "PSKU-CHANGED");
        WarehouseForwarderEligibilityQuoteScopeLock lockedScopes =
                new WarehouseForwarderEligibilityQuoteScopeLock(307L, List.of(locked));

        assertThrows(IllegalArgumentException.class,
                () -> workflow.requireQuoteLineScopesUnchanged(
                        lockedScopes, List.of(refreshed)));
    }

    @Test
    void shippingImportMatchesDocumentLineWhenCandidateHasNoQuoteId() {
        PurchaseOrderLogisticsQuoteLineRecord locked = quoteLine(null, 220002L, "PSKU-1");
        locked.shippingOrderLineId = 291001L;
        PurchaseOrderLogisticsQuoteLineRecord refreshed = quoteLine(280001L, 220002L, "PSKU-1");
        refreshed.shippingOrderLineId = 291001L;
        WarehouseForwarderEligibilityQuoteScopeLock lockedScopes =
                new WarehouseForwarderEligibilityQuoteScopeLock(307L, List.of(locked));

        lockedScopes.requireUnchanged(List.of(refreshed));
    }

    @Test
    void shippingImportRejectsDifferentDocumentLineOrStableScope() {
        PurchaseOrderLogisticsQuoteLineRecord locked = quoteLine(null, 220002L, "PSKU-1");
        locked.shippingOrderLineId = 291001L;
        WarehouseForwarderEligibilityQuoteScopeLock lockedScopes =
                new WarehouseForwarderEligibilityQuoteScopeLock(307L, List.of(locked));
        PurchaseOrderLogisticsQuoteLineRecord anotherLine = quoteLine(280001L, 220002L, "PSKU-1");
        anotherLine.shippingOrderLineId = 291002L;
        PurchaseOrderLogisticsQuoteLineRecord anotherStore = quoteLine(280001L, 220002L, "PSKU-1");
        anotherStore.shippingOrderLineId = 291001L;
        anotherStore.logicalStoreId = 108066L;
        PurchaseOrderLogisticsQuoteLineRecord anotherPsku = quoteLine(280001L, 220002L, "PSKU-2");
        anotherPsku.shippingOrderLineId = 291001L;

        assertThrows(IllegalArgumentException.class, () -> lockedScopes.requireUnchanged(List.of(anotherLine)));
        assertThrows(IllegalArgumentException.class, () -> lockedScopes.requireUnchanged(List.of(anotherStore)));
        assertThrows(IllegalArgumentException.class, () -> lockedScopes.requireUnchanged(List.of(anotherPsku)));
    }

    @Test
    void refreshedImportQuoteLinesCannotRepeatOneLockedBinding() {
        WarehouseForwarderEligibilityQuoteScopeLock lockedScopes =
                new WarehouseForwarderEligibilityQuoteScopeLock(
                        307L, List.of(quoteLine(280001L, 220002L, "PSKU-1")));
        PurchaseOrderLogisticsQuoteLineRecord repeated = quoteLine(280001L, 220002L, "PSKU-1");

        assertThrows(IllegalArgumentException.class,
                () -> lockedScopes.requireUnchanged(List.of(repeated, repeated)));
    }

    @Test
    void ruleEditFailsWhenRefreshedQuoteLineMovedToAnotherStableScope() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        WarehouseForwarderEligibilityWorkflow workflow = new WarehouseForwarderEligibilityWorkflow(
                mapper, mock(WarehouseForwarderEligibilityService.class),
                mock(WarehouseLogisticsQuoteOptionService.class));
        ShippingOrderRecord order = order(290001L);
        ShippingOrderLineRecord shippingLine = shippingLine(291001L, 108065L, "PSKU-1");
        shippingLine.shippingSubmitStatus = "NOT_SUBMITTED";
        PurchaseOrderLogisticsQuoteLineRecord refreshed = quoteLine(280001L, 220002L, "PSKU-CHANGED");
        refreshed.shippingOrderLineId = 291001L;
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        when(mapper.selectShippingOrderByIdForUpdate(290001L, 307L)).thenReturn(order);
        when(mapper.selectShippingOrderLineById(290001L, 291001L, 307L)).thenReturn(shippingLine);
        List<ProductForwarderEligibilityScopeAnchorRecord> scopes = List.of(scope(108065L, "PSKU-1"));
        when(mapper.lockProductForwarderEligibilityScopeAnchors(scopes)).thenReturn(scopes);
        when(mapper.listLogisticsQuoteCandidatesByShippingOrder(290001L)).thenReturn(List.of(refreshed));

        assertThrows(IllegalArgumentException.class,
                () -> workflow.updateRule(order, 291001L, command, 307L));
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
        line.logicalStoreId = 108065L;
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
        rule.logicalStoreId = 108065L;
        rule.partnerSku = "PSKU-1";
        rule.productVariantId = 9001L;
        rule.siteCode = "SA";
        rule.forwarderCode = "YT";
        rule.transportMode = "SEA";
        rule.eligibilityStatus = "UNSUPPORTED";
        when(mapper.listCurrentProductForwarderTransportEligibilities(
                List.of(scope(108065L, "PSKU-1"))))
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

    private static ProductForwarderEligibilityScopeAnchorRecord scope(Long logicalStoreId, String partnerSku) {
        return new ProductForwarderEligibilityScopeAnchorRecord(307L, logicalStoreId, partnerSku);
    }

    private static ShippingOrderLineRecord shippingLine(Long id, Long logicalStoreId, String partnerSku) {
        ShippingOrderLineRecord line = new ShippingOrderLineRecord();
        line.id = id;
        line.ownerUserId = 307L;
        line.logicalStoreId = logicalStoreId;
        line.partnerSku = partnerSku;
        return line;
    }

    private static PurchaseOrderLogisticsQuoteLineRecord quoteLine(Long id, Long itemSiteId, String partnerSku) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = id;
        line.ownerUserId = 307L;
        line.logicalStoreId = 108065L;
        line.purchaseOrderItemSiteId = itemSiteId;
        line.partnerSku = partnerSku;
        return line;
    }
}
