package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.infrastructure.mapper.PostSaleProfitLogisticsClosureMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionWriteRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchWriteRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.LockedAttributionRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.RecalculationRunRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.FinanceSaleCandidateRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.HeadhaulCostBatchRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.OrderSaleCandidateRow;
import com.nuono.next.postsaleprofit.PostSaleProfitSourceRecords.PurchaseCostBatchRow;
import com.nuono.next.postsaleprofit.logisticsclosure.PostSaleProfitLogisticsClosureRecords.ConfirmedHeadhaulAllocationRow;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitRecalculationServiceTest {

    @Mock
    private PostSaleProfitMapper mapper;

    @Mock
    private PostSaleProfitLogisticsClosureMapper logisticsClosureMapper;

    @Test
    void previewBuildsBatchRowsFromFinancePurchaseAndHeadhaulSourcesWithoutFxDefaultingToZero() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "3", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(
                        purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "2", "2.00"),
                        purchase("BATCH-B", "PSKU-1", "2026-02-01T10:00:00", "5", "3.00")
                ));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaul("PSKU-1", "0.50")));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getStatus()).isEqualTo("PREVIEW");
        assertThat(view.getSaleCandidateCount()).isEqualTo(1);
        assertThat(view.getBatchCandidateCount()).isEqualTo(2);
        assertThat(view.getRows()).hasSize(2);
        assertThat(view.getRows().get(0).getSourceId()).isEqualTo("BATCH-A");
        assertThat(view.getRows().get(0).getPurchaseBatchTime()).isEqualTo(LocalDateTime.parse("2026-01-01T10:00:00"));
        assertThat(view.getRows().get(0).getPurchaseQuantity()).isEqualByComparingTo("2");
        assertThat(view.getRows().get(0).getPurchaseUnitCostCny()).isEqualByComparingTo("2.00");
        assertThat(view.getRows().get(0).getPurchaseCostCny()).isEqualByComparingTo("4.000000");
        assertThat(view.getRows().get(0).getHeadhaulUnitCostCny()).isEqualByComparingTo("0.50");
        assertThat(view.getRows().get(0).getHeadhaulCostCny()).isEqualByComparingTo("1.000000");
        assertThat(view.getRows().get(0).getSoldQuantity()).isEqualByComparingTo("2");
        assertThat(view.getRows().get(0).getAutoQuantity()).isEqualByComparingTo("2");
        assertThat(view.getRows().get(0).getLockedQuantity()).isZero();
        assertThat(view.getRows().get(0).getNetProceedsLcy()).isEqualByComparingTo("20.000000");
        assertThat(view.getRows().get(0).getCurrency()).isEqualTo("SAR");
        assertThat(view.getRows().get(0).getFxRateToCny()).isNull();
        assertThat(view.getRows().get(0).getProfitCny()).isNull();
        assertThat(view.getRows().get(0).getProfitRate()).isNull();
        assertThat(view.getRows().get(0).getQualityStatuses()).containsExactly("MISSING_FX_RATE");
        assertThat(view.getRows().get(1).getSourceId()).isEqualTo("BATCH-B");
        assertThat(view.getRows().get(1).getPurchaseBatchTime()).isEqualTo(LocalDateTime.parse("2026-02-01T10:00:00"));
        assertThat(view.getRows().get(1).getPurchaseQuantity()).isEqualByComparingTo("5");
        assertThat(view.getRows().get(1).getPurchaseUnitCostCny()).isEqualByComparingTo("3.00");
        assertThat(view.getRows().get(1).getPurchaseCostCny()).isEqualByComparingTo("3.000000");
        assertThat(view.getRows().get(1).getHeadhaulUnitCostCny()).isEqualByComparingTo("0.50");
        assertThat(view.getRows().get(1).getHeadhaulCostCny()).isEqualByComparingTo("0.500000");
        assertThat(view.getRows().get(1).getSoldQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(1).getAutoQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(1).getLockedQuantity()).isZero();
        assertThat(view.getRows().get(1).getNetProceedsLcy()).isEqualByComparingTo("10.000000");
        assertThat(view.getRows().get(1).getCurrency()).isEqualTo("SAR");
        assertThat(view.getRows().get(1).getFxRateToCny()).isNull();
        assertThat(view.getRows().get(1).getProfitCny()).isNull();
        assertThat(view.getRows().get(1).getProfitRate()).isNull();
        assertThat(view.getRows().get(1).getQualityStatuses()).containsExactly("MISSING_FX_RATE");

        verify(mapper).listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo());
        verify(mapper).listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo());
        verify(mapper).listPurchaseCostBatches(307L, "STR108065-NSA", "SA");
        verify(mapper).listHeadhaulCostBatches(307L, "STR108065-NSA", "SA");
    }

    @Test
    void previewMarksSkuAverageHeadhaulAsEstimatedButStillCalculatesProfitWhenFxExists() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "2", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "5", "4.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaulAverage("PSKU-1", "1.50")));
        when(mapper.selectApplicableFxRate(307L, "SA", "SAR", command.getDateFrom(), command.getDateTo()))
                .thenReturn(bd("1.8833"));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getPurchaseCostCny()).isEqualByComparingTo("8.000000");
        assertThat(row.getHeadhaulCostCny()).isEqualByComparingTo("3.000000");
        assertThat(row.getProfitCny()).isEqualByComparingTo("45.499000");
        assertThat(row.getQualityStatuses()).containsExactly("ESTIMATED_HEADHAUL");
    }

    @Test
    void previewCarriesHeadhaulBatchEvidenceWhenUsingActualInTransitComponents() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "2", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "5", "4.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaulBatch("PSKU-1", "53001", "AR26050946895", "YT260509001", "SEA", "RUH", "36", "180.00", "5.00")));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getQualityStatuses()).containsExactly("MISSING_FX_RATE");
        assertThat(row.getHeadhaulUnitCostCny()).isEqualByComparingTo("5.00");
        assertThat(row.getEvidenceJson())
                .contains("\"confidence\":\"confirmed\"")
                .contains("\"sourceType\":\"IN_TRANSIT_BATCH_HEADHAUL\"")
                .contains("\"sourceId\":\"IN_TRANSIT_HEADHAUL_BATCH:53001:SA:PSKU-1\"")
                .contains("\"inTransitBatchId\":53001")
                .contains("\"billNo\":\"AR26050946895\"")
                .contains("\"batchReferenceNo\":\"YT260509001\"")
                .contains("\"transportMode\":\"SEA\"")
                .contains("\"destinationCode\":\"RUH\"")
                .contains("\"freightQuantity\":36")
                .contains("\"headhaulCostCny\":180.00")
                .contains("\"allocationBasis\":\"actual_component_quantity\"")
                .contains("\"evidenceText\":\"actual HEADHAUL component grouped by in_transit_batch + psku + site\"");
    }

    @Test
    void previewPrefersConfirmedLogisticsAllocationOverEstimatedSkuAverageHeadhaul() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "2", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "5", "4.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaulAverage("PSKU-1", "9.99")));
        when(logisticsClosureMapper.listConfirmedHeadhaulAllocations(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(confirmedHeadhaul("BATCH-A", "PSKU-1", "53001", "54001", "2", "2.25")));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper, logisticsClosureMapper)
                .recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getHeadhaulUnitCostCny()).isEqualByComparingTo("2.25");
        assertThat(row.getHeadhaulCostCny()).isEqualByComparingTo("4.500000");
        assertThat(row.getShippingSourceType()).isEqualTo("CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL");
        assertThat(row.getShippingSourceId()).isEqualTo("CONFIRMED_HEADHAUL:BATCH-A");
        assertThat(row.getShippingBatchNo()).isEqualTo("YT260509001");
        assertThat(row.getInTransitBatchId()).isEqualTo("53001");
        assertThat(row.getInTransitReferenceNo()).isEqualTo("YT260509001");
        assertThat(row.getAvailableAtSource()).isEqualTo("CONFIRMED_IN_TRANSIT");
        assertThat(row.getHeadhaulCostSourceType()).isEqualTo("CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL");
        assertThat(row.getQualityStatuses()).containsExactly("MISSING_FX_RATE");
        assertThat(row.getEvidenceJson())
                .contains("\"confidence\":\"confirmed\"")
                .contains("\"sourceType\":\"CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL\"")
                .contains("\"sourceId\":\"CONFIRMED_HEADHAUL:BATCH-A\"")
                .contains("\"purchaseSourceId\":\"BATCH-A\"")
                .contains("\"inTransitGoodsLineId\":54001")
                .contains("\"headhaulUnitCostCny\":2.25")
                .contains("\"allocationBasis\":\"confirmed_allocation_weighted_unit_cost\"");

        ArgumentCaptor<BatchWriteRow> batchCaptor = ArgumentCaptor.forClass(BatchWriteRow.class);
        verify(mapper).insertBatch(batchCaptor.capture());
        BatchWriteRow batchRow = batchCaptor.getValue();
        assertThat(batchRow.shippingSourceType).isEqualTo("CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL");
        assertThat(batchRow.shippingSourceId).isEqualTo("CONFIRMED_HEADHAUL:BATCH-A");
        assertThat(batchRow.shippingBatchNo).isEqualTo("YT260509001");
        assertThat(batchRow.inTransitBatchId).isEqualTo("53001");
        assertThat(batchRow.inTransitReferenceNo).isEqualTo("YT260509001");
        assertThat(batchRow.availableAtSource).isEqualTo("CONFIRMED_IN_TRANSIT");
        assertThat(batchRow.headhaulCostSourceType).isEqualTo("CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL");
    }

    @Test
    void previewKeepsConfirmedBatchProratedHeadhaulEvidenceAfterManualLogisticsClosure() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        ConfirmedHeadhaulAllocationRow confirmed = confirmedHeadhaul("BATCH-A", "PSKU-1", "53001", "54001", "2", "2.25");
        confirmed.allocationBasis = "confirmed_batch_headhaul_prorated_by_shipped_quantity";
        confirmed.evidenceText = "confirmed in-transit goods line uses batch-level HEADHAUL bills prorated by shipped quantity";

        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "2", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "5", "4.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaulAverage("PSKU-1", "9.99")));
        when(logisticsClosureMapper.listConfirmedHeadhaulAllocations(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(confirmed));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper, logisticsClosureMapper)
                .recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        assertThat(view.getRows().get(0).getEvidenceJson())
                .contains("\"allocationBasis\":\"confirmed_batch_headhaul_prorated_by_shipped_quantity\"")
                .contains("\"evidenceText\":\"confirmed in-transit goods line uses batch-level HEADHAUL bills prorated by shipped quantity\"");
    }

    @Test
    void previewKeepsHeadhaulMissingWhenConfirmedLogisticsAllocationHasNoActualHeadhaulCost() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );

        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "2", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "5", "4.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaulAverage("PSKU-1", "9.99")));
        when(logisticsClosureMapper.listConfirmedHeadhaulAllocations(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(confirmedHeadhaulMissingCost("BATCH-A", "PSKU-1", "53001", "54001", "2")));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper, logisticsClosureMapper)
                .recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getShippingSourceType()).isEqualTo("CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL");
        assertThat(row.getShippingSourceId()).isEqualTo("CONFIRMED_HEADHAUL:BATCH-A");
        assertThat(row.getHeadhaulCostSourceType()).isEqualTo("MISSING_HEADHAUL");
        assertThat(row.getHeadhaulUnitCostCny()).isNull();
        assertThat(row.getHeadhaulCostCny()).isNull();
        assertThat(row.getProfitCny()).isNull();
        assertThat(row.getQualityStatuses()).containsExactlyInAnyOrder(
                "MISSING_HEADHAUL",
                "MISSING_FX_RATE"
        );
        assertThat(row.getEvidenceJson())
                .contains("\"confidence\":\"missing\"")
                .contains("\"sourceType\":\"CONFIRMED_IN_TRANSIT_GOODS_LINE_HEADHAUL\"")
                .contains("\"sourceId\":\"CONFIRMED_HEADHAUL:BATCH-A\"")
                .contains("\"reviewReasons\":[\"missing_confirmed_headhaul_component\"]");
    }

    @Test
    void previewKeepsEstimatedHeadhaulVisibleOnUnassignedSoldSkuWithoutPretendingProfitIsComplete() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-UNASSIGNED", "2", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of());
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaulAverage("PSKU-UNASSIGNED", "1.50")));
        when(mapper.selectApplicableFxRate(307L, "SA", "SAR", command.getDateFrom(), command.getDateTo()))
                .thenReturn(bd("1.8833"));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getSourceId()).isEqualTo("UNASSIGNED:PSKU-UNASSIGNED");
        assertThat(row.getPurchaseCostCny()).isNull();
        assertThat(row.getHeadhaulUnitCostCny()).isEqualByComparingTo("1.50");
        assertThat(row.getHeadhaulCostCny()).isEqualByComparingTo("3.000000");
        assertThat(row.getProfitCny()).isNull();
        assertThat(row.getQualityStatuses()).containsExactlyInAnyOrder(
                "MISSING_PURCHASE_COST",
                "ESTIMATED_HEADHAUL",
                "UNASSIGNED_ORDER_QUANTITY"
        );
    }

    @Test
    void previewPersistsRunAndBatchRowsOnlyInPostSaleProfitTables() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "1", "10.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "2", "2.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaul("PSKU-1", "0.50")));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 500L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = 700L;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRunId()).isEqualTo(500L);
        ArgumentCaptor<RecalculationRunRow> runCaptor = ArgumentCaptor.forClass(RecalculationRunRow.class);
        verify(mapper).softDeletePreviewRuns(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo());
        verify(mapper).insertRecalculationRun(runCaptor.capture());
        assertThat(runCaptor.getValue().status).isEqualTo("PREVIEW");
        assertThat(runCaptor.getValue().scopeHash).contains("307|STR108065-NSA|SA|2026-05-01|2026-05-31");
        assertThat(runCaptor.getValue().orderLineCount).isEqualTo(1);
        assertThat(runCaptor.getValue().attributedQuantity).isEqualByComparingTo("1");
        assertThat(runCaptor.getValue().lockedQuantity).isZero();
        assertThat(runCaptor.getValue().unassignedQuantity).isZero();
        assertThat(runCaptor.getValue().missingIssueCount).isEqualTo(1);

        ArgumentCaptor<BatchWriteRow> batchCaptor = ArgumentCaptor.forClass(BatchWriteRow.class);
        verify(mapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().runId).isEqualTo(500L);
        assertThat(batchCaptor.getValue().sourceId).isEqualTo("BATCH-A");
        assertThat(batchCaptor.getValue().partnerSku).isEqualTo("PSKU-1");
        assertThat(batchCaptor.getValue().soldQuantity).isEqualByComparingTo("1");
        assertThat(batchCaptor.getValue().profitCny).isNull();
        assertThat(batchCaptor.getValue().qualityStatusJson).contains("MISSING_FX_RATE");

        ArgumentCaptor<AttributionWriteRow> attributionCaptor = ArgumentCaptor.forClass(AttributionWriteRow.class);
        verify(mapper).insertOrderAttribution(attributionCaptor.capture());
        assertThat(attributionCaptor.getValue().runId).isEqualTo(500L);
        assertThat(attributionCaptor.getValue().batchId).isEqualTo(700L);
        assertThat(attributionCaptor.getValue().ownerUserId).isEqualTo(307L);
        assertThat(attributionCaptor.getValue().storeCode).isEqualTo("STR108065-NSA");
        assertThat(attributionCaptor.getValue().siteCode).isEqualTo("SA");
        assertThat(attributionCaptor.getValue().itemNr).isEqualTo("ITEM-1");
        assertThat(attributionCaptor.getValue().orderNr).isEqualTo("ORDER-ITEM-1");
        assertThat(attributionCaptor.getValue().partnerSku).isEqualTo("PSKU-1");
        assertThat(attributionCaptor.getValue().attributedQuantity).isEqualByComparingTo("1");
        assertThat(attributionCaptor.getValue().attributionMethod).isEqualTo("FIFO");
        assertThat(attributionCaptor.getValue().locked).isFalse();
        assertThat(attributionCaptor.getValue().netProceedsLcy).isEqualByComparingTo("10.000000");
        assertThat(attributionCaptor.getValue().currency).isEqualTo("SAR");
    }

    @Test
    void previewCarriesProductSnapshotsFromPurchaseCostBatchesIntoPersistedRowsAndView() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        PurchaseCostBatchRow purchase = purchase("BATCH-A", "PAPERSAYSB276", "2026-01-01T10:00:00", "2", "2.00");
        setField(purchase, "skuParent", "Z4C9C64AD3F5975A7E978Z");
        setField(purchase, "productTitle", "A5 Hardcover Spiral Notebook Journal");
        setField(purchase, "productImageUrl", "https://f.nooncdn.com/pzsku/Z4C9C64AD3F5975A7E978Z/45/_/1772086852/cover");
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PAPERSAYSB276", "1", "10.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaul("PAPERSAYSB276", "0.50")));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 500L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = 700L;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(field(row, "skuParent")).isEqualTo("Z4C9C64AD3F5975A7E978Z");
        assertThat(field(row, "productTitle")).isEqualTo("A5 Hardcover Spiral Notebook Journal");
        assertThat(field(row, "productImageUrl")).isEqualTo("https://f.nooncdn.com/pzsku/Z4C9C64AD3F5975A7E978Z/45/_/1772086852/cover");

        ArgumentCaptor<BatchWriteRow> batchCaptor = ArgumentCaptor.forClass(BatchWriteRow.class);
        verify(mapper).insertBatch(batchCaptor.capture());
        assertThat(field(batchCaptor.getValue(), "skuParent")).isEqualTo("Z4C9C64AD3F5975A7E978Z");
        assertThat(field(batchCaptor.getValue(), "productTitle")).isEqualTo("A5 Hardcover Spiral Notebook Journal");
        assertThat(field(batchCaptor.getValue(), "productImageUrl")).isEqualTo("https://f.nooncdn.com/pzsku/Z4C9C64AD3F5975A7E978Z/45/_/1772086852/cover");
    }

    @Test
    void previewPersistsPurchaseEvidenceJsonAndReviewStatusForWeakProductLinkEvidence() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        PurchaseCostBatchRow purchase = purchase(
                "ALI1688_PRODUCT_LINK:930002391:103742:PAPERSAYSB077",
                "PAPERSAYSB077",
                "2026-05-14T10:30:00",
                "10",
                "0.85"
        );
        setField(purchase, "sourceType", "ALI1688_PRODUCT_LINK");
        setField(purchase, "providerOrderNo", "930002391");
        setField(purchase, "assignmentId", 103742L);
        setField(purchase, "itemId", 940011199L);
        setField(purchase, "sourceAmount", bd("8.50"));
        setField(purchase, "paidAmount", bd("8.50"));
        setField(purchase, "assignmentQuantity", bd("10"));
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PAPERSAYSB077", "1", "10.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA")).thenReturn(List.of(purchase));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA")).thenReturn(List.of(headhaul("PAPERSAYSB077", "0.50")));
        when(mapper.selectApplicableFxRate(307L, "SA", "SAR", command.getDateFrom(), command.getDateTo()))
                .thenReturn(bd("1.8833"));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 501L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = 701L;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getQualityStatuses()).contains("PURCHASE_SOURCE_REVIEW");
        assertThat(row.getProfitCny()).isNotNull();
        assertThat(row.getEvidenceJson()).contains("\"sourceType\":\"ALI1688_PRODUCT_LINK\"");
        assertThat(row.getEvidenceJson()).contains("\"providerOrderNo\":\"930002391\"");
        assertThat(row.getEvidenceJson()).contains("\"confidence\":\"review\"");
        assertThat(row.getEvidenceJson()).contains("\"reviewReasons\":[\"missing_source_identity\"]");
        ArgumentCaptor<BatchWriteRow> batchCaptor = ArgumentCaptor.forClass(BatchWriteRow.class);
        verify(mapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().evidenceJson).isEqualTo(row.getEvidenceJson());
    }

    @Test
    void previewPersistsBatchFeeBreakdownAndCountsOnlyHardMissingIssues() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        PurchaseCostBatchRow purchase = purchase(
                "ALI1688_PRODUCT_LINK:930002391:103742:PAPERSAYSB077",
                "PAPERSAYSB077",
                "2026-05-14T10:30:00",
                "10",
                "0.85"
        );
        setField(purchase, "sourceType", "ALI1688_PRODUCT_LINK");
        setField(purchase, "providerOrderNo", "930002391");
        FinanceSaleCandidateRow finance = finance("ITEM-1", "PAPERSAYSB077", "1", "10.00");
        finance.referralFeeLcy = bd("1.20");
        finance.fulfillmentFeeLcy = bd("2.30");
        finance.otherFeeNetLcy = bd("-0.40");
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA")).thenReturn(List.of(purchase));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA")).thenReturn(List.of(headhaulAverage("PAPERSAYSB077", "0.50")));
        when(mapper.selectApplicableFxRate(307L, "SA", "SAR", command.getDateFrom(), command.getDateTo()))
                .thenReturn(bd("1.8833"));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 502L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = 702L;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getQualityStatuses()).containsExactlyInAnyOrder("ESTIMATED_HEADHAUL", "PURCHASE_SOURCE_REVIEW");
        assertThat(row.getReferralFeeLcy()).isEqualByComparingTo("1.200000");
        assertThat(row.getFulfillmentFeeLcy()).isEqualByComparingTo("2.300000");
        assertThat(row.getOtherFeeNetLcy()).isEqualByComparingTo("-0.400000");

        ArgumentCaptor<BatchWriteRow> batchCaptor = ArgumentCaptor.forClass(BatchWriteRow.class);
        verify(mapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().referralFeeLcy).isEqualByComparingTo("1.200000");
        assertThat(batchCaptor.getValue().fulfillmentFeeLcy).isEqualByComparingTo("2.300000");
        assertThat(batchCaptor.getValue().otherFeeNetLcy).isEqualByComparingTo("-0.400000");

        ArgumentCaptor<RecalculationRunRow> runCaptor = ArgumentCaptor.forClass(RecalculationRunRow.class);
        verify(mapper).insertRecalculationRun(runCaptor.capture());
        assertThat(runCaptor.getValue().missingIssueCount).isZero();
    }

    @Test
    void previewPersistsOneOrderAttributionPerAllocatedSlice() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "3", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(
                        purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "2", "2.00"),
                        purchase("BATCH-B", "PSKU-1", "2026-02-01T10:00:00", "5", "3.00")
                ));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaul("PSKU-1", "0.50")));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 500L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        final long[] batchIds = {700L};
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = batchIds[0]++;
            return 1;
        }).when(mapper).insertBatch(any());

        new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        ArgumentCaptor<AttributionWriteRow> captor = ArgumentCaptor.forClass(AttributionWriteRow.class);
        verify(mapper, times(2)).insertOrderAttribution(captor.capture());
        assertThat(captor.getAllValues()).extracting(row -> row.batchId).containsExactly(700L, 701L);
        assertThat(captor.getAllValues()).extracting(row -> row.attributedQuantity)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(bd("2"), bd("1"));
    }

    @Test
    void previewPreservesLockedAttributionsFromPreviousPreviewRunBeforeRecalculatingUnlockedQuantity() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "3", "30.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(
                        purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "2", "2.00"),
                        purchase("BATCH-B", "PSKU-1", "2026-02-01T10:00:00", "5", "3.00")
                ));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaul("PSKU-1", "0.50")));
        when(mapper.listLockedAttributionsForScope(
                307L,
                "STR108065-NSA",
                "SA",
                command.getDateFrom(),
                command.getDateTo()
        )).thenReturn(List.of(locked("ITEM-1", "BATCH-B", "2", "订单实际属于 6 月采购批次")));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 500L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        final long[] batchIds = {700L};
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = batchIds[0]++;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(2);
        assertThat(view.getRows().get(0).getSourceId()).isEqualTo("BATCH-A");
        assertThat(view.getRows().get(0).getSoldQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(0).getAutoQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(0).getLockedQuantity()).isZero();
        assertThat(view.getRows().get(1).getSourceId()).isEqualTo("BATCH-B");
        assertThat(view.getRows().get(1).getSoldQuantity()).isEqualByComparingTo("2");
        assertThat(view.getRows().get(1).getAutoQuantity()).isZero();
        assertThat(view.getRows().get(1).getLockedQuantity()).isEqualByComparingTo("2");
        assertThat(view.getRows().get(1).getQualityStatuses()).contains("MANUAL_LOCKED");

        InOrder order = inOrder(mapper);
        order.verify(mapper).listLockedAttributionsForScope(
                307L,
                "STR108065-NSA",
                "SA",
                command.getDateFrom(),
                command.getDateTo()
        );
        order.verify(mapper).softDeletePreviewRuns(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo());

        ArgumentCaptor<AttributionWriteRow> captor = ArgumentCaptor.forClass(AttributionWriteRow.class);
        verify(mapper, times(2)).insertOrderAttribution(captor.capture());
        assertThat(captor.getAllValues()).extracting(row -> row.batchId).containsExactly(701L, 700L);
        assertThat(captor.getAllValues()).extracting(row -> row.attributionMethod).containsExactly("MANUAL", "FIFO");
        assertThat(captor.getAllValues()).extracting(row -> row.attributedQuantity)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(bd("2"), bd("1"));
        assertThat(captor.getAllValues().get(0).locked).isTrue();
        assertThat(captor.getAllValues().get(0).manualReason).isEqualTo("订单实际属于 6 月采购批次");
        assertThat(captor.getAllValues().get(1).locked).isFalse();
        assertThat(captor.getAllValues().get(1).manualReason).isNull();
    }

    @Test
    void previewKeepsSoldSkuVisibleWhenNoPurchaseBatchCanBeMatched() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-MISSING", "2", "20.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of());
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of());
        when(mapper.selectApplicableFxRate(307L, "SA", "SAR", command.getDateFrom(), command.getDateTo()))
                .thenReturn(bd("1.8833"));
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 500L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = 700L;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        PostSaleProfitBatchRowView row = view.getRows().get(0);
        assertThat(row.getSourceId()).isEqualTo("UNASSIGNED:PSKU-MISSING");
        assertThat(row.getPartnerSku()).isEqualTo("PSKU-MISSING");
        assertThat(row.getPurchaseQuantity()).isNull();
        assertThat(row.getSoldQuantity()).isEqualByComparingTo("2");
        assertThat(row.getAutoQuantity()).isZero();
        assertThat(row.getLockedQuantity()).isZero();
        assertThat(row.getNetProceedsLcy()).isEqualByComparingTo("20.000000");
        assertThat(row.getFxRateToCny()).isEqualByComparingTo("1.8833");
        assertThat(row.getProfitCny()).isNull();
        assertThat(row.getQualityStatuses()).containsExactlyInAnyOrder(
                "MISSING_PURCHASE_COST",
                "MISSING_HEADHAUL",
                "UNASSIGNED_ORDER_QUANTITY"
        );

        ArgumentCaptor<BatchWriteRow> batchCaptor = ArgumentCaptor.forClass(BatchWriteRow.class);
        verify(mapper).insertBatch(batchCaptor.capture());
        assertThat(batchCaptor.getValue().sourceId).isEqualTo("UNASSIGNED:PSKU-MISSING");
        assertThat(batchCaptor.getValue().purchaseQuantity).isNull();
        assertThat(batchCaptor.getValue().qualityStatusJson).contains("UNASSIGNED_ORDER_QUANTITY");

        ArgumentCaptor<RecalculationRunRow> runCaptor = ArgumentCaptor.forClass(RecalculationRunRow.class);
        verify(mapper).insertRecalculationRun(runCaptor.capture());
        assertThat(runCaptor.getValue().orderLineCount).isEqualTo(1);
        assertThat(runCaptor.getValue().attributedQuantity).isEqualByComparingTo("2");
        assertThat(runCaptor.getValue().lockedQuantity).isZero();
        assertThat(runCaptor.getValue().unassignedQuantity).isEqualByComparingTo("2");

        ArgumentCaptor<AttributionWriteRow> attributionCaptor = ArgumentCaptor.forClass(AttributionWriteRow.class);
        verify(mapper).insertOrderAttribution(attributionCaptor.capture());
        assertThat(attributionCaptor.getValue().batchId).isEqualTo(700L);
        assertThat(attributionCaptor.getValue().attributionMethod).isEqualTo("UNASSIGNED");
        assertThat(attributionCaptor.getValue().locked).isFalse();
        assertThat(attributionCaptor.getValue().attributedQuantity).isEqualByComparingTo("2");
    }

    @Test
    void previewKeepsFinanceRowsWithMissingPartnerSkuVisibleAsUnknownSkuDiagnostics() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        FinanceSaleCandidateRow missingSkuFinance = finance("ITEM-UNKNOWN", null, "1", "8.00");
        missingSkuFinance.orderTime = null;
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(missingSkuFinance));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of());
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            RecalculationRunRow row = invocation.getArgument(0);
            row.id = 500L;
            return 1;
        }).when(mapper).insertRecalculationRun(any());
        doAnswer(invocation -> {
            BatchWriteRow row = invocation.getArgument(0);
            row.id = 700L;
            return 1;
        }).when(mapper).insertBatch(any());

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        assertThat(view.getRows().get(0).getSourceId()).isEqualTo("UNASSIGNED:UNKNOWN_SKU");
        assertThat(view.getRows().get(0).getPartnerSku()).isEqualTo("UNKNOWN_SKU");
        assertThat(view.getRows().get(0).getSoldQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(0).getQualityStatuses()).contains("UNASSIGNED_ORDER_QUANTITY");

        ArgumentCaptor<RecalculationRunRow> runCaptor = ArgumentCaptor.forClass(RecalculationRunRow.class);
        verify(mapper).insertRecalculationRun(runCaptor.capture());
        assertThat(runCaptor.getValue().orderLineCount).isEqualTo(1);
        assertThat(runCaptor.getValue().attributedQuantity).isEqualByComparingTo("1");
        assertThat(runCaptor.getValue().unassignedQuantity).isEqualByComparingTo("1");

        ArgumentCaptor<AttributionWriteRow> attributionCaptor = ArgumentCaptor.forClass(AttributionWriteRow.class);
        verify(mapper).insertOrderAttribution(attributionCaptor.capture());
        assertThat(attributionCaptor.getValue().partnerSku).isEqualTo("UNKNOWN_SKU");
        assertThat(attributionCaptor.getValue().orderTime).isNull();
        assertThat(attributionCaptor.getValue().attributionMethod).isEqualTo("UNASSIGNED");
    }

    @Test
    void previewUsesConfiguredFxRateToCalculateCnyProfit() {
        PostSaleProfitRecalculationCommand command = new PostSaleProfitRecalculationCommand(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31)
        );
        when(mapper.listFinanceSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of(finance("ITEM-1", "PSKU-1", "1", "10.00")));
        when(mapper.listOrderSaleCandidates(307L, "STR108065-NSA", "SA", command.getDateFrom(), command.getDateTo()))
                .thenReturn(List.of());
        when(mapper.listPurchaseCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(purchase("BATCH-A", "PSKU-1", "2026-01-01T10:00:00", "2", "2.00")));
        when(mapper.listHeadhaulCostBatches(307L, "STR108065-NSA", "SA"))
                .thenReturn(List.of(headhaul("PSKU-1", "0.50")));
        when(mapper.selectApplicableFxRate(307L, "SA", "SAR", command.getDateFrom(), command.getDateTo()))
                .thenReturn(bd("1.8833"));

        PostSaleProfitRecalculationView view = new PostSaleProfitRecalculationService(mapper).recalculatePreview(command);

        assertThat(view.getRows()).hasSize(1);
        assertThat(view.getRows().get(0).getFxRateToCny()).isEqualByComparingTo("1.8833");
        assertThat(view.getRows().get(0).getProfitCny()).isEqualByComparingTo("16.333000");
        assertThat(view.getRows().get(0).getProfitRate()).isEqualByComparingTo("0.86725429");
        assertThat(view.getRows().get(0).getQualityStatuses()).containsExactly("OK");
    }

    private static FinanceSaleCandidateRow finance(String itemNr, String partnerSku, String quantity, String netProceeds) {
        FinanceSaleCandidateRow row = new FinanceSaleCandidateRow();
        row.itemNr = itemNr;
        row.orderNr = "ORDER-" + itemNr;
        row.partnerSku = partnerSku;
        row.soldQuantity = bd(quantity);
        row.netProceedsLcy = bd(netProceeds);
        row.referralFeeLcy = BigDecimal.ZERO;
        row.fulfillmentFeeLcy = BigDecimal.ZERO;
        row.otherFeeNetLcy = BigDecimal.ZERO;
        row.currency = "SAR";
        row.orderTime = LocalDateTime.parse("2026-05-01T12:00:00");
        return row;
    }

    private static PurchaseCostBatchRow purchase(
            String sourceId,
            String partnerSku,
            String purchaseTime,
            String quantity,
            String unitCost
    ) {
        PurchaseCostBatchRow row = new PurchaseCostBatchRow();
        row.sourceId = sourceId;
        row.partnerSku = partnerSku;
        row.purchaseBatchTime = LocalDateTime.parse(purchaseTime);
        row.purchaseQuantity = bd(quantity);
        row.purchaseUnitCostCny = bd(unitCost);
        return row;
    }

    private static HeadhaulCostBatchRow headhaul(String partnerSku, String unitCost) {
        HeadhaulCostBatchRow row = new HeadhaulCostBatchRow();
        row.partnerSku = partnerSku;
        row.headhaulUnitCostCny = bd(unitCost);
        return row;
    }

    private static HeadhaulCostBatchRow headhaulAverage(String partnerSku, String unitCost) {
        HeadhaulCostBatchRow row = headhaul(partnerSku, unitCost);
        row.sourceId = "IN_TRANSIT_HEADHAUL_SKU_AVERAGE:SA:" + partnerSku;
        return row;
    }

    private static HeadhaulCostBatchRow headhaulBatch(
            String partnerSku,
            String inTransitBatchId,
            String billNo,
            String batchReferenceNo,
            String transportMode,
            String destinationCode,
            String quantity,
            String cost,
            String unitCost
    ) {
        HeadhaulCostBatchRow row = headhaul(partnerSku, unitCost);
        row.sourceId = "IN_TRANSIT_HEADHAUL_BATCH:" + inTransitBatchId + ":SA:" + partnerSku;
        row.inTransitBatchId = Long.valueOf(inTransitBatchId);
        row.siteCode = "SA";
        row.freightQuantity = bd(quantity);
        row.headhaulCostCny = bd(cost);
        row.billNo = billNo;
        row.batchReferenceNo = batchReferenceNo;
        row.transportMode = transportMode;
        row.destinationCode = destinationCode;
        row.allocationBasis = "actual_component_quantity";
        row.evidenceText = "actual HEADHAUL component grouped by in_transit_batch + psku + site";
        return row;
    }

    private static ConfirmedHeadhaulAllocationRow confirmedHeadhaul(
            String sourceId,
            String partnerSku,
            String inTransitBatchId,
            String inTransitGoodsLineId,
            String allocatedQuantity,
            String unitCost
    ) {
        ConfirmedHeadhaulAllocationRow row = new ConfirmedHeadhaulAllocationRow();
        row.sourceId = sourceId;
        row.purchaseBatchId = 102001L;
        row.partnerSku = partnerSku;
        row.siteCode = "SA";
        row.inTransitBatchId = Long.valueOf(inTransitBatchId);
        row.inTransitGoodsLineId = Long.valueOf(inTransitGoodsLineId);
        row.billNo = "AR26050946895";
        row.batchReferenceNo = "YT260509001";
        row.allocatedQuantity = bd(allocatedQuantity);
        row.freightQuantity = bd("36");
        row.headhaulUnitCostCny = bd(unitCost);
        row.headhaulCostCny = bd(unitCost).multiply(bd(allocatedQuantity));
        row.allocationBasis = "confirmed_in_transit_goods_line_headhaul";
        row.evidenceText = "confirmed allocation matched to SKU/site HEADHAUL component";
        return row;
    }

    private static ConfirmedHeadhaulAllocationRow confirmedHeadhaulMissingCost(
            String sourceId,
            String partnerSku,
            String inTransitBatchId,
            String inTransitGoodsLineId,
            String allocatedQuantity
    ) {
        ConfirmedHeadhaulAllocationRow row = new ConfirmedHeadhaulAllocationRow();
        row.sourceId = sourceId;
        row.purchaseBatchId = 102001L;
        row.partnerSku = partnerSku;
        row.siteCode = "SA";
        row.inTransitBatchId = Long.valueOf(inTransitBatchId);
        row.inTransitGoodsLineId = Long.valueOf(inTransitGoodsLineId);
        row.batchReferenceNo = "YT260509001";
        row.allocatedQuantity = bd(allocatedQuantity);
        row.allocationBasis = "confirmed_allocation_missing_headhaul_component";
        row.evidenceText = "confirmed allocation exists but SKU/site HEADHAUL component is missing";
        return row;
    }

    private static LockedAttributionRow locked(String itemNr, String sourceId, String quantity, String manualReason) {
        LockedAttributionRow row = new LockedAttributionRow();
        row.itemNr = itemNr;
        row.sourceId = sourceId;
        row.quantity = bd(quantity);
        row.manualReason = manualReason;
        return row;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(target.getClass().getSimpleName() + " should expose field " + fieldName, exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Could not set " + fieldName + " on " + target.getClass().getSimpleName(), exception);
        }
    }

    private static Object field(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException exception) {
            throw new AssertionError(target.getClass().getSimpleName() + " should expose field " + fieldName, exception);
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Could not read " + fieldName + " on " + target.getClass().getSimpleName(), exception);
        }
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
