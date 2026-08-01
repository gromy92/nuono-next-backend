package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProcurementPurchaseOrderMapper;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderCommands.UpdateShippingOrderLineEligibilityCommand;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class WarehouseForwarderEligibilityServiceTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseForwarderEligibilityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        service = new WarehouseForwarderEligibilityService(mapper);
        when(mapper.insertProductForwarderTransportEligibility(any(), eq(307L))).thenReturn(1);
    }

    @Test
    void missingRuleDefaultsToSupported() {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();

        service.apply(view, line(), candidate(), Map.of());

        assertThat(view.eligibilityStatus).isEqualTo("SUPPORTED");
    }

    @Test
    void incompleteBusinessScopeIsUnknownAndBlocksExport() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.productVariantId = null;
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
        when(mapper.listCurrentProductForwarderTransportEligibilities(307L, List.of(9001L)))
                .thenReturn(List.of(rule));

        service.apply(
                view,
                line,
                candidate(),
                Map.of(WarehouseForwarderEligibilityService.key(307L, 9001L, "SA", "ET", "AIR"), rule)
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
                        WarehouseForwarderEligibilityService.key(307L, 9001L, "SA", "ET", "AIR"),
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
        when(mapper.listCurrentProductForwarderTransportEligibilities(307L, List.of(9001L)))
                .thenReturn(List.of(rule));

        assertThatThrownBy(() -> service.requireExportable(List.of(line), candidate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("承运状态异常");
    }

    @Test
    void storesSelectedStatusWithoutAdditionalBusinessFields() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.eligibilityStatus = "UNSUPPORTED";
        when(mapper.nextProductForwarderTransportEligibilityId()).thenReturn(370001L);

        service.updateRule(line, "ET", command, 307L);

        ArgumentCaptor<ProductForwarderTransportEligibilityRecord> inserted =
                ArgumentCaptor.forClass(ProductForwarderTransportEligibilityRecord.class);
        verify(mapper).insertProductForwarderTransportEligibility(inserted.capture(), eq(307L));
        assertThat(inserted.getValue()).satisfies(rule -> {
            assertThat(rule.id).isEqualTo(370001L);
            assertThat(rule.productVariantId).isEqualTo(9001L);
            assertThat(rule.forwarderCode).isEqualTo("ET");
            assertThat(rule.transportMode).isEqualTo("AIR");
            assertThat(rule.eligibilityStatus).isEqualTo("UNSUPPORTED");
            assertThat(rule.version).isEqualTo(1);
        });
    }

    @Test
    void supportedStatusClosesExceptionWithoutPersistingDefaultRule() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        ProductForwarderTransportEligibilityRecord current = rule("UNSUPPORTED", 3);
        current.effectiveFrom = LocalDate.of(2026, 7, 20);
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.eligibilityStatus = "SUPPORTED";
        when(mapper.selectActiveProductForwarderTransportEligibilityForUpdate(
                307L, 9001L, "SA", "ET", "AIR"
        )).thenReturn(current);
        when(mapper.closeProductForwarderTransportEligibility(
                current.id, current.version, LocalDate.now(), 307L
        )).thenReturn(1);

        service.updateRule(line, "ET", command, 307L);

        verify(mapper).closeProductForwarderTransportEligibility(
                current.id, current.version, LocalDate.now(), 307L
        );
        verify(mapper, never()).insertProductForwarderTransportEligibility(any(), eq(307L));
    }

    @Test
    void newExceptionContinuesHistoricalVersionAfterSupportedGap() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.eligibilityStatus = "UNSUPPORTED";
        when(mapper.selectLatestProductForwarderTransportEligibilityVersionForUpdate(
                307L, 9001L, "SA", "ET", "AIR"
        )).thenReturn(3);
        when(mapper.nextProductForwarderTransportEligibilityId()).thenReturn(370004L);

        service.updateRule(line, "ET", command, 307L);

        ArgumentCaptor<ProductForwarderTransportEligibilityRecord> inserted =
                ArgumentCaptor.forClass(ProductForwarderTransportEligibilityRecord.class);
        verify(mapper).insertProductForwarderTransportEligibility(inserted.capture(), eq(307L));
        assertThat(inserted.getValue().version).isEqualTo(4);
    }

    @Test
    void concurrentFirstInsertReturnsRefreshableBusinessError() {
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.eligibilityStatus = "UNSUPPORTED";
        when(mapper.insertProductForwarderTransportEligibility(any(), eq(307L)))
                .thenThrow(new DuplicateKeyException("duplicate active rule"));

        assertThatThrownBy(() -> service.updateRule(line(), "ET", command, 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("承运状态已被其他操作更新，请刷新后重试。");
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
        rule.siteCode = "SA";
        rule.forwarderCode = "ET";
        rule.transportMode = "AIR";
        rule.eligibilityStatus = status;
        rule.version = version;
        return rule;
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
