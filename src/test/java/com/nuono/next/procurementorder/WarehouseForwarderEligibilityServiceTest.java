package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WarehouseForwarderEligibilityServiceTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseForwarderEligibilityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        service = new WarehouseForwarderEligibilityService(mapper);
    }

    @Test
    void missingRuleDefaultsToSupported() {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();

        service.apply(view, line(), candidate(), Map.of());

        assertThat(view.eligibilityStatus).isEqualTo("SUPPORTED");
    }

    @Test
    void projectionAndMutationDecisionUseSeparateReadContracts() {
        service.loadCurrent(List.of(line()));
        service.loadCurrentForDecision(List.of(line()));

        verify(mapper).listCurrentProductForwarderTransportEligibilities(List.of(scope()));
        verify(mapper).listCurrentProductForwarderTransportEligibilitiesForUpdate(List.of(scope()));
    }

    @Test
    void incompleteBusinessScopeIsUnknownAndBlocksExport() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.logicalStoreId = null;
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();

        service.apply(view, line, candidate(), Map.of());

        assertThat(view.eligibilityStatus).isEqualTo("UNKNOWN");
        assertThatThrownBy(() -> service.requireExportable(List.of(line), candidate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("承运状态异常")
                .hasMessageContaining("PSKU-001");
    }

    @Test
    void missingLineSiteOrModeCannotBeFilledFromCandidate() {
        PurchaseOrderLogisticsQuoteLineRecord missingSite = line();
        missingSite.siteCode = null;
        PurchaseOrderLogisticsQuoteLineRecord missingMode = line();
        missingMode.plannedTransportMode = null;

        assertThat(status(missingSite, candidate())).isEqualTo("UNKNOWN");
        assertThat(status(missingMode, candidate())).isEqualTo("UNKNOWN");
    }

    @Test
    void lineAndCandidateScopeMismatchIsUnknown() {
        PurchaseOrderLogisticsQuoteLineRecord siteMismatch = line();
        siteMismatch.siteCode = "AE";
        PurchaseOrderLogisticsQuoteLineRecord modeMismatch = line();
        modeMismatch.plannedTransportMode = "SEA";

        assertThat(status(siteMismatch, candidate())).isEqualTo("UNKNOWN");
        assertThat(status(modeMismatch, candidate())).isEqualTo("UNKNOWN");
    }

    @Test
    void missingSelectedSegmentIsUnknown() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();

        service.applySelected(List.of(line), List.of());

        assertThat(line.eligibilityStatus).isEqualTo("UNKNOWN");
    }

    @Test
    void unsupportedRuleSuppressesPriceAndBlocksExport() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        ProductForwarderTransportEligibilityRecord rule = rule("UNSUPPORTED", 3);
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();
        view.unitPrice = new BigDecimal("65");
        view.currency = "CNY";
        view.billingUnit = "KG";
        when(mapper.listCurrentProductForwarderTransportEligibilitiesForUpdate(List.of(scope())))
                .thenReturn(List.of(rule));

        service.apply(
                view,
                line,
                candidate(),
                Map.of(WarehouseForwarderEligibilityService.key(
                        307L, 108065L, "PSKU-001", "SA", "ET", "AIR"), rule)
        );

        assertThat(view.eligibilityStatus).isEqualTo("UNSUPPORTED");
        assertThat(view.unitPrice).isNull();
        assertThatThrownBy(() -> service.requireExportable(List.of(line), candidate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("审核单未导出")
                .hasMessageContaining("PSKU-001");
    }

    @Test
    void inquiryRequiredCanExportButCannotSubmit() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.eligibilityStatus = "INQUIRY_REQUIRED";

        service.requireExportable(List.of(line), candidate());

        assertThatThrownBy(() -> service.requireSubmittable(List.of(line)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("需向货代询价确认");
    }

    @Test
    void submittedLineUsesEligibilitySnapshotInsteadOfCurrentRule() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.eligibilityStatus = "SUPPORTED";
        line.shippingSubmitStatus = "SUBMITTED";
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();

        service.apply(
                view,
                line,
                candidate(),
                Map.of(
                        WarehouseForwarderEligibilityService.key(
                                307L, 108065L, "PSKU-001", "SA", "ET", "AIR"),
                        rule("UNSUPPORTED", 3)
                )
        );

        assertThat(view.eligibilityStatus).isEqualTo("SUPPORTED");
    }

    @Test
    void blankSubmittedSnapshotIsUnknownAndBlocksSubmission() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.shippingSubmitStatus = "SUBMITTED";
        line.eligibilityStatus = " ";
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();

        service.apply(view, line, candidate(), Map.of());

        assertThat(view.eligibilityStatus).isEqualTo("UNKNOWN");
        line.eligibilityStatus = view.eligibilityStatus;
        assertThatThrownBy(() -> service.requireSubmittable(List.of(line)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("缺失或异常");
    }

    @Test
    void corruptedCurrentRuleFailsClosedBeforeExport() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        ProductForwarderTransportEligibilityRecord rule = rule("FUTURE_STATUS", 3);
        when(mapper.listCurrentProductForwarderTransportEligibilitiesForUpdate(List.of(scope())))
                .thenReturn(List.of(rule));

        assertThatThrownBy(() -> service.requireExportable(List.of(line), candidate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("承运状态异常");
    }

    @Test
    void productMaintenanceReadDefaultsToSupportedAndReturnsCurrentException() {
        ProductForwarderEligibilityProductScope productScope = productScope();

        assertThat(service.currentStatus(productScope)).isEqualTo("SUPPORTED");

        when(mapper.listCurrentProductForwarderTransportEligibilities(List.of(scope())))
                .thenReturn(List.of(rule("INQUIRY_REQUIRED", 4)));
        assertThat(service.currentStatus(productScope)).isEqualTo("INQUIRY_REQUIRED");
    }

    @Test
    void productListReadsExceptionalStatusesForOneRouteInOneQuery() {
        when(mapper.listCurrentProductForwarderTransportEligibilitiesForRoute(
                307L, 108065L, "SA", "ET", "AIR"
        )).thenReturn(List.of(rule("UNSUPPORTED", 3)));

        Map<String, String> result = service.currentStatusesForRoute(
                307L, 108065L, "sa", "et", "air"
        );

        assertThat(result).containsExactly(Map.entry("PSKU-001", "UNSUPPORTED"));
    }

    @Test
    void productMaintenanceUsesSharedScopeLockAndRuleWriter() {
        ProductForwarderEligibilityScopeAnchorRecord anchor = scope();
        when(mapper.lockProductForwarderEligibilityScopeAnchors(List.of(anchor)))
                .thenReturn(List.of(anchor));
        when(mapper.nextProductForwarderTransportEligibilityId()).thenReturn(470001L);
        when(mapper.insertProductForwarderTransportEligibility(any(), eq(901L))).thenReturn(1);

        String status = service.updateProductRule(productScope(), "UNSUPPORTED", 901L);

        assertThat(status).isEqualTo("UNSUPPORTED");
        verify(mapper).ensureProductForwarderEligibilityScopeAnchors(List.of(anchor));
        verify(mapper).insertProductForwarderTransportEligibility(
                org.mockito.ArgumentMatchers.argThat(row ->
                        "PSKU-001".equals(row.partnerSku)
                                && "ET".equals(row.forwarderCode)
                                && "AIR".equals(row.transportMode)
                                && "UNSUPPORTED".equals(row.eligibilityStatus)),
                eq(901L)
        );
    }

    private static PurchaseOrderLogisticsQuoteLineRecord line() {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.ownerUserId = 307L;
        line.shippingOrderId = 290001L;
        line.shippingOrderLineId = 291001L;
        line.productMasterId = 8001L;
        line.productVariantId = 9001L;
        line.logicalStoreId = 108065L;
        line.sourceStoreCode = "STR108065-NSA";
        line.partnerSku = "PSKU-001";
        line.siteCode = "SA";
        line.plannedTransportMode = "AIR";
        return line;
    }

    private static ForwarderRouteRecommendationRecord candidate() {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = "ET";
        candidate.siteCode = "SA";
        candidate.transportMode = "AIR";
        return candidate;
    }

    private static ProductForwarderTransportEligibilityRecord rule(String status, int version) {
        ProductForwarderTransportEligibilityRecord rule = new ProductForwarderTransportEligibilityRecord();
        rule.id = 370000L + version;
        rule.ownerUserId = 307L;
        rule.productVariantId = 9001L;
        rule.logicalStoreId = 108065L;
        rule.partnerSku = "PSKU-001";
        rule.siteCode = "SA";
        rule.forwarderCode = "ET";
        rule.transportMode = "AIR";
        rule.eligibilityStatus = status;
        rule.version = version;
        return rule;
    }

    private static ProductForwarderEligibilityScopeAnchorRecord scope() {
        return new ProductForwarderEligibilityScopeAnchorRecord(307L, 108065L, "PSKU-001");
    }

    private static ProductForwarderEligibilityProductScope productScope() {
        return new ProductForwarderEligibilityProductScope(
                307L,
                108065L,
                8001L,
                9001L,
                "STR108065-NSA",
                "PSKU-001",
                "SA",
                "ET",
                "AIR"
        );
    }

    private String status(
            PurchaseOrderLogisticsQuoteLineRecord line,
            ForwarderRouteRecommendationRecord candidate
    ) {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();
        service.apply(view, line, candidate, Map.of());
        return view.eligibilityStatus;
    }
}
