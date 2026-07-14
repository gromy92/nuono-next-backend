package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostSaleProfitAttributionServiceTest {

    private final PostSaleProfitAttributionService service = new PostSaleProfitAttributionService();

    @Test
    void fifoSplitsSoldQuantityByPurchaseTime() {
        PostSaleProfitAttributionResult result = service.attribute(
                List.of(sale("ITEM-1", "PSKU-1", "8", "80.00")),
                List.of(
                        batch("BATCH-A", "PSKU-1", "2026-01-02T09:00:00", "5", "2.00", "0.50", "2.00"),
                        batch("BATCH-B", "PSKU-1", "2026-02-02T09:00:00", "10", "3.00", "0.50", "2.00")
                ),
                List.of()
        );

        assertThat(result.batchResults()).hasSize(2);
        assertBatch(result.batchResults().get(0), "BATCH-A", "5", "50.000000", "87.500000", "5", "0");
        assertBatch(result.batchResults().get(1), "BATCH-B", "3", "30.000000", "49.500000", "3", "0");
    }

    @Test
    void missingCostLeavesProfitNullAndAddsQualityStatus() {
        PostSaleProfitAttributionResult result = service.attribute(
                List.of(sale("ITEM-1", "PSKU-1", "2", "20.00")),
                List.of(batch("BATCH-MISSING", "PSKU-1", "2026-01-02T09:00:00", "2", null, null, "2.00")),
                List.of()
        );

        PostSaleProfitBatchResult row = result.batchResults().get(0);
        assertThat(row.soldQuantity()).isEqualByComparingTo("2");
        assertThat(row.purchaseUnitCostCny()).isNull();
        assertThat(row.headhaulUnitCostCny()).isNull();
        assertThat(row.profitCny()).isNull();
        assertThat(row.qualityStatuses())
                .containsExactlyInAnyOrder(
                        PostSaleProfitQualityStatus.MISSING_PURCHASE_COST,
                        PostSaleProfitQualityStatus.MISSING_HEADHAUL
                );
    }

    @Test
    void lockedAttributionsConsumeQuantityBeforeAutoFifo() {
        PostSaleProfitAttributionResult result = service.attribute(
                List.of(sale("ITEM-1", "PSKU-1", "8", "80.00")),
                List.of(
                        batch("BATCH-A", "PSKU-1", "2026-01-02T09:00:00", "5", "2.00", "0.50", "2.00"),
                        batch("BATCH-B", "PSKU-1", "2026-02-02T09:00:00", "10", "3.00", "0.50", "2.00")
                ),
                List.of(new PostSaleProfitLockedAttribution("ITEM-1", "BATCH-B", bd("2")))
        );

        assertThat(result.batchResults()).hasSize(2);
        assertThat(result.batchResults().get(0).sourceId()).isEqualTo("BATCH-A");
        assertThat(result.batchResults().get(0).soldQuantity()).isEqualByComparingTo("5");
        assertThat(result.batchResults().get(0).autoQuantity()).isEqualByComparingTo("5");
        assertThat(result.batchResults().get(0).lockedQuantity()).isEqualByComparingTo("0");
        assertThat(result.batchResults().get(1).sourceId()).isEqualTo("BATCH-B");
        assertThat(result.batchResults().get(1).soldQuantity()).isEqualByComparingTo("3");
        assertThat(result.batchResults().get(1).autoQuantity()).isEqualByComparingTo("1");
        assertThat(result.batchResults().get(1).lockedQuantity()).isEqualByComparingTo("2");
        assertThat(result.attributions())
                .extracting(PostSaleProfitOrderAttributionSlice::attributionMethod)
                .contains(PostSaleProfitAttributionMethod.MANUAL, PostSaleProfitAttributionMethod.FIFO);
    }

    @Test
    void unassignedDiagnosticBatchKeepsSoldOrdersVisibleWithoutPretendingCostIsZero() {
        PostSaleProfitAttributionResult result = service.attribute(
                List.of(sale("ITEM-1", "PSKU-MISSING", "2", "20.00")),
                List.of(new PostSaleProfitBatchCandidate(
                        "UNASSIGNED:PSKU-MISSING",
                        "PSKU-MISSING",
                        null,
                        bd("2"),
                        null,
                        null,
                        bd("1.8833")
                )),
                List.of()
        );

        assertThat(result.batchResults()).hasSize(1);
        PostSaleProfitBatchResult row = result.batchResults().get(0);
        assertThat(row.sourceId()).isEqualTo("UNASSIGNED:PSKU-MISSING");
        assertThat(row.availableQuantity()).isNull();
        assertThat(row.soldQuantity()).isEqualByComparingTo("2");
        assertThat(row.autoQuantity()).isZero();
        assertThat(row.lockedQuantity()).isZero();
        assertThat(row.purchaseCostCny()).isNull();
        assertThat(row.headhaulCostCny()).isNull();
        assertThat(row.fxRateToCny()).isEqualByComparingTo("1.8833");
        assertThat(row.profitCny()).isNull();
        assertThat(row.qualityStatuses()).containsExactlyInAnyOrder(
                PostSaleProfitQualityStatus.MISSING_PURCHASE_COST,
                PostSaleProfitQualityStatus.MISSING_HEADHAUL,
                PostSaleProfitQualityStatus.UNASSIGNED_ORDER_QUANTITY
        );
        assertThat(result.attributions()).hasSize(1);
        assertThat(result.attributions().get(0).attributionMethod()).isEqualTo(PostSaleProfitAttributionMethod.UNASSIGNED);
    }

    private static PostSaleProfitSaleCandidate sale(String itemNr, String partnerSku, String quantity, String netProceeds) {
        return new PostSaleProfitSaleCandidate(
                itemNr,
                "ORDER-" + itemNr,
                partnerSku,
                bd(quantity),
                bd(netProceeds),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "SAR",
                LocalDateTime.parse("2026-03-01T10:00:00")
        );
    }

    private static PostSaleProfitBatchCandidate batch(
            String sourceId,
            String partnerSku,
            String purchaseTime,
            String quantity,
            String purchaseUnitCost,
            String headhaulUnitCost,
            String fxRate
    ) {
        return new PostSaleProfitBatchCandidate(
                sourceId,
                partnerSku,
                LocalDateTime.parse(purchaseTime),
                bd(quantity),
                nullable(purchaseUnitCost),
                nullable(headhaulUnitCost),
                nullable(fxRate)
        );
    }

    private static void assertBatch(
            PostSaleProfitBatchResult row,
            String sourceId,
            String soldQuantity,
            String netProceeds,
            String profit,
            String autoQuantity,
            String lockedQuantity
    ) {
        assertThat(row.sourceId()).isEqualTo(sourceId);
        assertThat(row.soldQuantity()).isEqualByComparingTo(soldQuantity);
        assertThat(row.netProceedsLcy()).isEqualByComparingTo(netProceeds);
        assertThat(row.profitCny()).isEqualByComparingTo(profit);
        assertThat(row.autoQuantity()).isEqualByComparingTo(autoQuantity);
        assertThat(row.lockedQuantity()).isEqualByComparingTo(lockedQuantity);
        assertThat(row.qualityStatuses()).containsExactly(PostSaleProfitQualityStatus.OK);
    }

    private static BigDecimal nullable(String value) {
        return value == null ? null : bd(value);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
