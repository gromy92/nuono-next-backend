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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class WarehouseForwarderEligibilityStableIdentityTest {

    private ProcurementPurchaseOrderMapper mapper;
    private WarehouseForwarderEligibilityService service;

    @BeforeEach
    void setUp() {
        mapper = mock(ProcurementPurchaseOrderMapper.class);
        service = new WarehouseForwarderEligibilityService(mapper);
        when(mapper.insertProductForwarderTransportEligibility(any(), eq(307L))).thenReturn(1);
    }

    @Test
    void storesNormalizedStableIdentityAndKeepsVariantOnlyAsSnapshot() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.partnerSku = "  psku-001 ";
        UpdateShippingOrderLineEligibilityCommand command = command("UNSUPPORTED");
        when(mapper.nextProductForwarderTransportEligibilityId()).thenReturn(370001L);

        service.updateRule(line, "et", command, 307L);

        ArgumentCaptor<ProductForwarderTransportEligibilityRecord> inserted =
                ArgumentCaptor.forClass(ProductForwarderTransportEligibilityRecord.class);
        verify(mapper).insertProductForwarderTransportEligibility(inserted.capture(), eq(307L));
        assertThat(inserted.getValue()).satisfies(rule -> {
            assertThat(rule.id).isEqualTo(370001L);
            assertThat(rule.logicalStoreId).isEqualTo(108065L);
            assertThat(rule.partnerSku).isEqualTo("PSKU-001");
            assertThat(rule.productVariantId).isEqualTo(9001L);
            assertThat(rule.forwarderCode).isEqualTo("ET");
            assertThat(rule.transportMode).isEqualTo("AIR");
            assertThat(rule.version).isEqualTo(1);
        });
    }

    @Test
    void supportedStatusClosesExceptionWithoutPersistingDefaultRule() {
        ProductForwarderTransportEligibilityRecord current = rule("UNSUPPORTED", 3);
        current.effectiveFrom = LocalDate.of(2026, 7, 20);
        when(mapper.selectActiveProductForwarderTransportEligibilityForUpdate(
                307L, 108065L, "PSKU-001", "SA", "ET", "AIR")).thenReturn(current);
        when(mapper.closeProductForwarderTransportEligibility(
                current.id, current.version, LocalDate.now(), 307L)).thenReturn(1);

        service.updateRule(line(), "ET", command("SUPPORTED"), 307L);

        verify(mapper).closeProductForwarderTransportEligibility(
                current.id, current.version, LocalDate.now(), 307L);
        verify(mapper, never()).insertProductForwarderTransportEligibility(any(), eq(307L));
    }

    @Test
    void newExceptionContinuesHistoricalVersionAfterSupportedGap() {
        when(mapper.selectLatestProductForwarderTransportEligibilityVersionForUpdate(
                307L, 108065L, "PSKU-001", "SA", "ET", "AIR")).thenReturn(3);
        when(mapper.nextProductForwarderTransportEligibilityId()).thenReturn(370004L);

        service.updateRule(line(), "ET", command("UNSUPPORTED"), 307L);

        ArgumentCaptor<ProductForwarderTransportEligibilityRecord> inserted =
                ArgumentCaptor.forClass(ProductForwarderTransportEligibilityRecord.class);
        verify(mapper).insertProductForwarderTransportEligibility(inserted.capture(), eq(307L));
        assertThat(inserted.getValue().version).isEqualTo(4);
    }

    @Test
    void concurrentFirstInsertReturnsRefreshableBusinessError() {
        when(mapper.insertProductForwarderTransportEligibility(any(), eq(307L)))
                .thenThrow(new DuplicateKeyException("duplicate active rule"));

        assertThatThrownBy(() -> service.updateRule(line(), "ET", command("UNSUPPORTED"), 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("承运状态已被其他操作更新，请刷新后重试。");
    }

    @Test
    void variantRotationAndMissingVariantKeepStableRuleWhileAnotherStoreDoesNot() {
        ProductForwarderTransportEligibilityRecord rule = rule("UNSUPPORTED", 3);
        Map<String, ProductForwarderTransportEligibilityRecord> rules = Map.of(
                WarehouseForwarderEligibilityService.key(
                        307L, 108065L, "PSKU-001", "SA", "ET", "AIR"), rule);
        PurchaseOrderLogisticsQuoteLineRecord rotated = line();
        rotated.productVariantId = 9901L;
        PurchaseOrderLogisticsQuoteLineRecord withoutVariant = line();
        withoutVariant.productVariantId = null;
        PurchaseOrderLogisticsQuoteLineRecord anotherStore = line();
        anotherStore.logicalStoreId = 108066L;

        assertThat(status(rotated, rules)).isEqualTo("UNSUPPORTED");
        assertThat(status(withoutVariant, rules)).isEqualTo("UNSUPPORTED");
        assertThat(status(anotherStore, rules)).isEqualTo("SUPPORTED");
    }

    @Test
    void lowercaseWhitespacePskuStillMatchesCanonicalRule() {
        ProductForwarderTransportEligibilityRecord rule = rule("UNSUPPORTED", 3);
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.partnerSku = "  psku-001  ";

        assertThat(status(line, Map.of(WarehouseForwarderEligibilityService.key(
                307L, 108065L, "PSKU-001", "SA", "ET", "AIR"), rule)))
                .isEqualTo("UNSUPPORTED");
    }

    @Test
    void sixSegmentKeyUsesUtf8ByteLengthsWithoutAmbiguousDelimiters() {
        assertThat(WarehouseForwarderEligibilityService.key(
                1L, 2L, "😀", "SA", "ET", "AIR"))
                .isEqualTo("1#11#24#😀2#SA2#ET3#AIR");
    }

    @Test
    void duplicateCurrentRowsFailClosedInsteadOfSelectingOne() {
        ProductForwarderTransportEligibilityRecord first = rule("UNSUPPORTED", 3);
        ProductForwarderTransportEligibilityRecord second = rule("INQUIRY_REQUIRED", 4);
        when(mapper.listCurrentProductForwarderTransportEligibilitiesForUpdate(List.of(scope())))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.requireExportable(List.of(line()), candidate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("数据冲突");
    }

    @Test
    void submittedLineSnapshotCasFailureAbortsInsteadOfRewritingHistory() {
        PurchaseOrderLogisticsQuoteLineRecord line = line();
        line.shippingOrderLineId = 291001L;
        when(mapper.snapshotShippingOrderLineEligibility(290001L, 307L, line, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.snapshot(290001L, 307L, List.of(line), 307L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("承运结论保存失败");
    }

    private String status(
            PurchaseOrderLogisticsQuoteLineRecord line,
            Map<String, ProductForwarderTransportEligibilityRecord> rules
    ) {
        PurchaseOrderLogisticsQuoteChannelLineView view = new PurchaseOrderLogisticsQuoteChannelLineView();
        service.apply(view, line, candidate(), rules);
        return view.eligibilityStatus;
    }

    private static UpdateShippingOrderLineEligibilityCommand command(String status) {
        UpdateShippingOrderLineEligibilityCommand command = new UpdateShippingOrderLineEligibilityCommand();
        command.eligibilityStatus = status;
        return command;
    }

    private static PurchaseOrderLogisticsQuoteLineRecord line() {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.ownerUserId = 307L;
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
}
