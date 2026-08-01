package com.nuono.next.procurementorder;

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
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.ForwarderRouteRecommendationRecord;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarehouseLogisticsQuotePersistenceTest {

    @Test
    void submittedSnapshotIsNotRefreshedWhenCreatingShippingOrder() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        PurchaseOrderLogisticsQuoteLineRecord submitted = line("SUBMITTED");

        assertThatCode(() -> WarehouseShippingQuoteSnapshotRefresher.refreshUnlessSubmitted(
                mapper, submitted, 307L)).doesNotThrowAnyException();

        verify(mapper, never()).refreshLogisticsQuoteLineSnapshot(any(), eq(307L));
    }

    @Test
    void mutableSnapshotRefreshFailsClosedOnConcurrentSubmission() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        PurchaseOrderLogisticsQuoteLineRecord mutable = line("NOT_SUBMITTED");
        when(mapper.refreshLogisticsQuoteLineSnapshot(any(), eq(307L))).thenReturn(0);

        assertThatThrownBy(() -> WarehouseShippingQuoteSnapshotRefresher.refreshUnlessSubmitted(
                mapper, mutable, 307L)).hasMessageContaining("快照已变化");
    }

    @Test
    void exportMarkFailsClosedOnPartialUpdate() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        when(mapper.markLogisticsQuoteLinesExported(200001L, List.of(1L, 2L), 307L)).thenReturn(1);

        assertThatThrownBy(() -> WarehouseLogisticsQuoteExportPersistence.markPurchaseOrderExported(
                mapper, 200001L, List.of(1L, 2L), 307L))
                .hasMessageContaining("导出状态已变化");
    }

    @Test
    void selectionCasClearsAnotherChannelsStalePriceAndRemark() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        PurchaseOrderLogisticsQuoteLineRecord persisted = line("NOT_SUBMITTED");
        persisted.forwarderCode = "QIKE";
        persisted.routeCode = "QIKE-SA-AIR";
        persisted.unitPrice = new BigDecimal("67");
        persisted.currency = "CNY";
        persisted.billingUnit = "KG";
        persisted.estimatedAmount = new BigDecimal("670");
        persisted.remark = "启客旧备注";
        PurchaseOrderLogisticsQuoteLineRecord missing = line("NOT_SUBMITTED");
        missing.unitPrice = null;
        when(mapper.persistLogisticsQuoteLineSelection(persisted, 307L)).thenReturn(1);

        WarehouseLogisticsQuoteSelectionPersistence.persistExact(
                mapper, persisted, missing, candidate("ET", "ET-SA-AIR"), 307L);

        org.assertj.core.api.Assertions.assertThat(persisted.forwarderCode).isEqualTo("ET");
        org.assertj.core.api.Assertions.assertThat(persisted.unitPrice).isNull();
        org.assertj.core.api.Assertions.assertThat(persisted.currency).isNull();
        org.assertj.core.api.Assertions.assertThat(persisted.billingUnit).isNull();
        org.assertj.core.api.Assertions.assertThat(persisted.estimatedAmount).isNull();
        org.assertj.core.api.Assertions.assertThat(persisted.remark).isNull();
        org.assertj.core.api.Assertions.assertThat(persisted.quoteStatus).isEqualTo("PENDING_QUOTE");
    }

    @Test
    void selectionCasFailsClosedOnConcurrentSubmission() {
        ProcurementPurchaseOrderMapper mapper = mock(ProcurementPurchaseOrderMapper.class);
        PurchaseOrderLogisticsQuoteLineRecord line = line("NOT_SUBMITTED");
        line.unitPrice = BigDecimal.ONE;

        assertThatThrownBy(() -> WarehouseLogisticsQuoteSelectionPersistence.persist(
                mapper, List.of(line), candidate("ET", "ET-SA-AIR"), 307L))
                .hasMessageContaining("选择已变化");
    }

    @Test
    void mutableScopeIncludesOnlyNormalizedExactNotSubmittedRows() {
        PurchaseOrderLogisticsQuoteLineRecord mutable = line("  not_submitted  ");
        PurchaseOrderLogisticsQuoteLineRecord nullStatus = line(null);
        PurchaseOrderLogisticsQuoteLineRecord blank = line("  ");
        PurchaseOrderLogisticsQuoteLineRecord unknown = line("UNKNOWN");
        PurchaseOrderLogisticsQuoteLineRecord future = line("FUTURE");
        PurchaseOrderLogisticsQuoteLineRecord submitted = line("SUBMITTED");

        org.assertj.core.api.Assertions.assertThat(
                WarehouseLogisticsQuoteMutationScope.mutableOnly(List.of(
                        mutable, nullStatus, blank, unknown, future, submitted)))
                .containsExactly(mutable);
    }

    private ForwarderRouteRecommendationRecord candidate(String forwarder, String route) {
        ForwarderRouteRecommendationRecord candidate = new ForwarderRouteRecommendationRecord();
        candidate.forwarderCode = forwarder;
        candidate.routeCode = route;
        return candidate;
    }

    private PurchaseOrderLogisticsQuoteLineRecord line(String submitStatus) {
        PurchaseOrderLogisticsQuoteLineRecord line = new PurchaseOrderLogisticsQuoteLineRecord();
        line.id = 280001L;
        line.shippingSubmitStatus = submitStatus;
        return line;
    }
}
