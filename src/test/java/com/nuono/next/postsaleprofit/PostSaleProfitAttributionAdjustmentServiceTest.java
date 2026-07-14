package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionTotalsRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionWriteRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchMoveRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchStatusRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.TransferAttributionRow;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitAttributionAdjustmentServiceTest {

    @Mock
    private PostSaleProfitMapper mapper;

    @Test
    void lockBatchMarksAllAttributionsManualLockedAndAddsManualStatusToBatch() {
        when(mapper.selectBatchStatus(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(batchStatus("[\"MISSING_FX_RATE\"]", "3", "0"));

        PostSaleProfitAttributionAdjustmentView view = new PostSaleProfitAttributionAdjustmentService(mapper)
                .setBatchLock(new PostSaleProfitBatchLockCommand(
                        307L,
                        "STR108065-NSA",
                        "SA",
                        700L,
                        true,
                        "人工确认这批订单归属"
                ));

        verify(mapper).updateBatchAttributionLockState(
                307L,
                "STR108065-NSA",
                "SA",
                700L,
                true,
                "MANUAL",
                "人工确认这批订单归属"
        );
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateBatchLockSummary(
                org.mockito.ArgumentMatchers.eq(307L),
                org.mockito.ArgumentMatchers.eq("STR108065-NSA"),
                org.mockito.ArgumentMatchers.eq("SA"),
                org.mockito.ArgumentMatchers.eq(700L),
                org.mockito.ArgumentMatchers.eq(bd("3")),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ZERO),
                statusCaptor.capture()
        );
        assertThat(statusCaptor.getValue()).isEqualTo("[\"MISSING_FX_RATE\",\"MANUAL_LOCKED\"]");
        assertThat(view.getBatchId()).isEqualTo(700L);
        assertThat(view.getQualityStatuses()).containsExactly("MISSING_FX_RATE", "MANUAL_LOCKED");
    }

    @Test
    void unlockBatchRestoresFifoAttributionsAndRemovesManualStatusFromBatch() {
        when(mapper.selectBatchStatus(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(batchStatus("[\"MISSING_FX_RATE\",\"MANUAL_LOCKED\"]", "3", "3"));

        PostSaleProfitAttributionAdjustmentView view = new PostSaleProfitAttributionAdjustmentService(mapper)
                .setBatchLock(new PostSaleProfitBatchLockCommand(
                        307L,
                        "STR108065-NSA",
                        "SA",
                        700L,
                        false,
                        null
                ));

        verify(mapper).updateBatchAttributionLockState(
                307L,
                "STR108065-NSA",
                "SA",
                700L,
                false,
                "FIFO",
                null
        );
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).updateBatchLockSummary(
                org.mockito.ArgumentMatchers.eq(307L),
                org.mockito.ArgumentMatchers.eq("STR108065-NSA"),
                org.mockito.ArgumentMatchers.eq("SA"),
                org.mockito.ArgumentMatchers.eq(700L),
                org.mockito.ArgumentMatchers.eq(BigDecimal.ZERO),
                org.mockito.ArgumentMatchers.eq(bd("3")),
                statusCaptor.capture()
        );
        assertThat(statusCaptor.getValue()).isEqualTo("[\"MISSING_FX_RATE\"]");
        assertThat(view.getBatchId()).isEqualTo(700L);
        assertThat(view.getQualityStatuses()).containsExactly("MISSING_FX_RATE");
    }

    @Test
    void moveAttributionTransfersQuantityToTargetBatchAndRecalculatesBothBatches() {
        when(mapper.selectBatchForMove(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(batchMove(700L, 500L, "PSKU-1", "2.00", "0.50", null, "[\"MISSING_FX_RATE\"]"));
        when(mapper.selectBatchForMove(307L, "STR108065-NSA", "SA", 701L))
                .thenReturn(batchMove(701L, 500L, "PSKU-1", "3.00", "0.50", null, "[\"MISSING_FX_RATE\"]"));
        when(mapper.listTransferableAttributions(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(java.util.List.of(transferAttribution("3", "30.00", "3.00", "1.50", "0.30")));
        when(mapper.selectBatchAttributionTotals(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(totals("2", "2", "0", "20.00", "2.00", "1.00", "0.20", "SAR"));
        when(mapper.selectBatchAttributionTotals(307L, "STR108065-NSA", "SA", 701L))
                .thenReturn(totals("1", "0", "1", "10.00", "1.00", "0.50", "0.10", "SAR"));

        PostSaleProfitAttributionMoveView view = new PostSaleProfitAttributionAdjustmentService(mapper)
                .moveAttribution(new PostSaleProfitAttributionMoveCommand(
                        307L,
                        "STR108065-NSA",
                        "SA",
                        700L,
                        701L,
                        bd("1"),
                        "订单实际属于 6 月采购批次"
                ));

        verify(mapper).updateAttributionSlice(
                307L,
                "STR108065-NSA",
                "SA",
                900L,
                bd("2.000000"),
                bd("20.000000"),
                bd("2.000000"),
                bd("1.000000"),
                bd("0.200000")
        );
        ArgumentCaptor<AttributionWriteRow> movedCaptor = ArgumentCaptor.forClass(AttributionWriteRow.class);
        verify(mapper).insertOrderAttribution(movedCaptor.capture());
        assertThat(movedCaptor.getValue().runId).isEqualTo(500L);
        assertThat(movedCaptor.getValue().batchId).isEqualTo(701L);
        assertThat(movedCaptor.getValue().itemNr).isEqualTo("ITEM-1");
        assertThat(movedCaptor.getValue().attributedQuantity).isEqualByComparingTo("1.000000");
        assertThat(movedCaptor.getValue().attributionMethod).isEqualTo("MANUAL");
        assertThat(movedCaptor.getValue().locked).isTrue();
        assertThat(movedCaptor.getValue().manualReason).isEqualTo("订单实际属于 6 月采购批次");
        assertThat(movedCaptor.getValue().netProceedsLcy).isEqualByComparingTo("10.000000");

        verify(mapper).updateBatchFinancialSummary(
                307L,
                "STR108065-NSA",
                "SA",
                700L,
                bd("2"),
                bd("2"),
                bd("0"),
                bd("20.00"),
                bd("2.00"),
                bd("1.00"),
                bd("0.20"),
                "SAR",
                bd("4.000000"),
                bd("1.000000"),
                null,
                null,
                "[\"MISSING_FX_RATE\"]"
        );
        verify(mapper).updateBatchFinancialSummary(
                307L,
                "STR108065-NSA",
                "SA",
                701L,
                bd("1"),
                bd("0"),
                bd("1"),
                bd("10.00"),
                bd("1.00"),
                bd("0.50"),
                bd("0.10"),
                "SAR",
                bd("3.000000"),
                bd("0.500000"),
                null,
                null,
                "[\"MISSING_FX_RATE\",\"MANUAL_LOCKED\"]"
        );
        assertThat(view.getSourceBatchId()).isEqualTo(700L);
        assertThat(view.getTargetBatchId()).isEqualTo(701L);
        assertThat(view.getMovedQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void moveAttributionRejectsDifferentSkuTargetBatch() {
        when(mapper.selectBatchForMove(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(batchMove(700L, 500L, "PSKU-1", "2.00", "0.50", null, "[\"MISSING_FX_RATE\"]"));
        when(mapper.selectBatchForMove(307L, "STR108065-NSA", "SA", 701L))
                .thenReturn(batchMove(701L, 500L, "PSKU-2", "3.00", "0.50", null, "[\"MISSING_FX_RATE\"]"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> new PostSaleProfitAttributionAdjustmentService(mapper)
                        .moveAttribution(new PostSaleProfitAttributionMoveCommand(
                                307L,
                                "STR108065-NSA",
                                "SA",
                                700L,
                                701L,
                                bd("1"),
                                "wrong batch"
                        ))
        );

        assertThat(error.getStatus().value()).isEqualTo(400);
        verify(mapper, never()).insertOrderAttribution(ArgumentMatchers.any());
    }

    private static BatchStatusRow batchStatus(String qualityStatusJson, String soldQuantity, String lockedQuantity) {
        BatchStatusRow row = new BatchStatusRow();
        row.id = 700L;
        row.qualityStatusJson = qualityStatusJson;
        row.soldQuantity = bd(soldQuantity);
        row.lockedQuantity = bd(lockedQuantity);
        return row;
    }

    private static BatchMoveRow batchMove(
            Long id,
            Long runId,
            String partnerSku,
            String purchaseUnitCost,
            String headhaulUnitCost,
            String fxRate,
            String qualityStatusJson
    ) {
        BatchMoveRow row = new BatchMoveRow();
        row.id = id;
        row.runId = runId;
        row.partnerSku = partnerSku;
        row.purchaseUnitCostCny = bd(purchaseUnitCost);
        row.headhaulUnitCostCny = bd(headhaulUnitCost);
        row.fxRateToCny = fxRate == null ? null : bd(fxRate);
        row.qualityStatusJson = qualityStatusJson;
        return row;
    }

    private static TransferAttributionRow transferAttribution(
            String quantity,
            String net,
            String referral,
            String fulfillment,
            String other
    ) {
        TransferAttributionRow row = new TransferAttributionRow();
        row.id = 900L;
        row.runId = 500L;
        row.financeItemNr = "ITEM-1";
        row.orderNr = "ORDER-1";
        row.itemNr = "ITEM-1";
        row.orderTime = LocalDateTime.parse("2026-05-02T10:30:00");
        row.partnerSku = "PSKU-1";
        row.sku = "SKU-1";
        row.attributedQuantity = bd(quantity);
        row.netProceedsLcy = bd(net);
        row.referralFeeLcy = bd(referral);
        row.fulfillmentFeeLcy = bd(fulfillment);
        row.otherFeeNetLcy = bd(other);
        row.currency = "SAR";
        return row;
    }

    private static AttributionTotalsRow totals(
            String sold,
            String auto,
            String locked,
            String net,
            String referral,
            String fulfillment,
            String other,
            String currency
    ) {
        AttributionTotalsRow row = new AttributionTotalsRow();
        row.soldQuantity = bd(sold);
        row.autoQuantity = bd(auto);
        row.lockedQuantity = bd(locked);
        row.netProceedsLcy = bd(net);
        row.referralFeeLcy = bd(referral);
        row.fulfillmentFeeLcy = bd(fulfillment);
        row.otherFeeNetLcy = bd(other);
        row.currency = currency;
        return row;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
