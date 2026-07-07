package com.nuono.next.postsaleprofit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.PostSaleProfitMapper;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.AttributionReadRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.BatchReadRow;
import com.nuono.next.postsaleprofit.PostSaleProfitPersistenceRecords.RecalculationRunRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitReadServiceTest {

    @Mock
    private PostSaleProfitMapper mapper;

    @Test
    void latestRunReturnsMostRecentRecalculationScopeForStoreSite() {
        RecalculationRunRow row = new RecalculationRunRow();
        row.id = 38L;
        row.storeCode = "STR108065-NSA";
        row.siteCode = "SA";
        row.dateFrom = LocalDate.of(2026, 5, 25);
        row.dateTo = LocalDate.of(2026, 6, 23);
        row.status = "PREVIEW";
        row.orderLineCount = 1232;
        row.attributedQuantity = bd("1232");
        row.missingIssueCount = 79;
        row.finishedAt = LocalDateTime.parse("2026-06-24T10:30:00");
        when(mapper.selectLatestRun(307L, "STR108065-NSA", "SA")).thenReturn(row);

        PostSaleProfitLatestRunView view = new PostSaleProfitReadService(mapper)
                .latestRun(307L, "STR108065-NSA", "SA");

        assertThat(view.isAvailable()).isTrue();
        assertThat(view.getRunId()).isEqualTo(38L);
        assertThat(view.getStoreCode()).isEqualTo("STR108065-NSA");
        assertThat(view.getSiteCode()).isEqualTo("SA");
        assertThat(view.getDateFrom()).isEqualTo("2026-05-25");
        assertThat(view.getDateTo()).isEqualTo("2026-06-23");
        assertThat(view.getStatus()).isEqualTo("PREVIEW");
        assertThat(view.getOrderLineCount()).isEqualTo(1232);
        assertThat(view.getMissingIssueCount()).isEqualTo(79);
        assertThat(view.getFinishedAt()).isEqualTo("2026-06-24T10:30");
    }

    @Test
    void latestRunReturnsUnavailableWhenNoRecalculationExists() {
        when(mapper.selectLatestRun(307L, "STR108065-NSA", "SA")).thenReturn(null);

        PostSaleProfitLatestRunView view = new PostSaleProfitReadService(mapper)
                .latestRun(307L, "STR108065-NSA", "SA");

        assertThat(view.isAvailable()).isFalse();
        assertThat(view.getRunId()).isNull();
        assertThat(view.getDateFrom()).isNull();
        assertThat(view.getDateTo()).isNull();
    }

    @Test
    void listBatchesReturnsRecalculationRequiredWhenNoRunExists() {
        PostSaleProfitBatchQuery query = query();
        when(mapper.selectLatestRunId(307L, "STR108065-NSA", "SA", query.getDateFrom(), query.getDateTo()))
                .thenReturn(null);

        PostSaleProfitBatchListView view = new PostSaleProfitReadService(mapper).listBatches(query);

        assertThat(view.isRecalculationRequired()).isTrue();
        assertThat(view.getRows()).isEmpty();
        assertThat(view.getTotal()).isZero();
        verify(mapper, never()).listBatchRows(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void listBatchesReadsRowsFromLatestRunAndParsesQualityStatusJson() {
        PostSaleProfitBatchQuery query = query();
        when(mapper.selectLatestRunId(307L, "STR108065-NSA", "SA", query.getDateFrom(), query.getDateTo()))
                .thenReturn(500L);
        when(mapper.countBatchRows(500L, query)).thenReturn(1);
        when(mapper.listBatchRows(500L, query, 0, 50))
                .thenReturn(List.of(batchRow()));

        PostSaleProfitBatchListView view = new PostSaleProfitReadService(mapper).listBatches(query);

        assertThat(view.isRecalculationRequired()).isFalse();
        assertThat(view.getTotal()).isEqualTo(1);
        assertThat(view.getRows()).hasSize(1);
        assertThat(view.getRows().get(0).getSourceId()).isEqualTo("BATCH-A");
        assertThat(view.getRows().get(0).getSkuParent()).isEqualTo("Z4C9C64AD3F5975A7E978Z");
        assertThat(view.getRows().get(0).getPartnerSku()).isEqualTo("PSKU-1");
        assertThat(view.getRows().get(0).getProductTitle()).isEqualTo("A5 Notebook");
        assertThat(view.getRows().get(0).getProductImageUrl()).isEqualTo("https://example.test/notebook.jpg");
        assertThat(view.getRows().get(0).getPurchaseBatchTime()).isEqualTo(LocalDateTime.parse("2026-05-01T12:00:00"));
        assertThat(view.getRows().get(0).getPurchaseQuantity()).isEqualByComparingTo("10");
        assertThat(view.getRows().get(0).getPurchaseUnitCostCny()).isEqualByComparingTo("2.50");
        assertThat(view.getRows().get(0).getPurchaseCostCny()).isEqualByComparingTo("2.50");
        assertThat(view.getRows().get(0).getHeadhaulUnitCostCny()).isEqualByComparingTo("0.50");
        assertThat(view.getRows().get(0).getHeadhaulCostCny()).isEqualByComparingTo("0.50");
        assertThat(view.getRows().get(0).getSoldQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(0).getAutoQuantity()).isEqualByComparingTo("1");
        assertThat(view.getRows().get(0).getLockedQuantity()).isZero();
        assertThat(view.getRows().get(0).getNetProceedsLcy()).isEqualByComparingTo("10.00");
        assertThat(view.getRows().get(0).getAverageSalePriceLcy()).isEqualByComparingTo("12.50");
        assertThat(view.getRows().get(0).getGmvLcy()).isEqualByComparingTo("12.50");
        assertThat(view.getRows().get(0).getSalePriceFactCount()).isEqualTo(1);
        assertThat(view.getRows().get(0).getCurrency()).isEqualTo("SAR");
        assertThat(view.getRows().get(0).getFxRateToCny()).isNull();
        assertThat(view.getRows().get(0).getProfitCny()).isNull();
        assertThat(view.getRows().get(0).getProfitRate()).isNull();
        assertThat(view.getRows().get(0).getQualityStatuses()).containsExactly("MISSING_FX_RATE");
    }

    @Test
    void getBatchDetailReturnsOrderAttributionsForBatch() {
        when(mapper.listOrderAttributions(307L, "STR108065-NSA", "SA", 700L))
                .thenReturn(List.of(attributionRow()));

        PostSaleProfitBatchDetailView view = new PostSaleProfitReadService(mapper)
                .getBatchDetail(307L, "STR108065-NSA", "SA", 700L);

        assertThat(view.getBatchId()).isEqualTo(700L);
        assertThat(view.getOrderLines()).hasSize(1);
        PostSaleProfitOrderLineView line = view.getOrderLines().get(0);
        assertThat(line.getId()).isEqualTo("900");
        assertThat(line.getOrderNo()).isEqualTo("ORDER-1");
        assertThat(line.getItemNr()).isEqualTo("ITEM-1");
        assertThat(line.getOrderTime()).isEqualTo("2026-05-02T10:30");
        assertThat(line.getPartnerSku()).isEqualTo("PSKU-1");
        assertThat(line.getAttributedQuantity()).isEqualByComparingTo("2");
        assertThat(line.getAttributionMethod()).isEqualTo("FIFO");
        assertThat(line.isLocked()).isFalse();
        assertThat(line.getNetProceedsLcy()).isEqualByComparingTo("20.00");
        assertThat(line.getCurrency()).isEqualTo("SAR");
    }

    private static PostSaleProfitBatchQuery query() {
        return new PostSaleProfitBatchQuery(
                307L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 31),
                null,
                null,
                false,
                false,
                false,
                1,
                50
        );
    }

    private static BatchReadRow batchRow() {
        BatchReadRow row = new BatchReadRow();
        row.sourceId = "BATCH-A";
        row.skuParent = "Z4C9C64AD3F5975A7E978Z";
        row.partnerSku = "PSKU-1";
        row.productTitle = "A5 Notebook";
        row.productImageUrl = "https://example.test/notebook.jpg";
        row.purchaseBatchTime = LocalDateTime.parse("2026-05-01T12:00:00");
        row.purchaseQuantity = bd("10");
        row.purchaseUnitCostCny = bd("2.50");
        row.purchaseCostCny = bd("2.50");
        row.headhaulUnitCostCny = bd("0.50");
        row.headhaulCostCny = bd("0.50");
        row.soldQuantity = BigDecimal.ONE;
        row.autoQuantity = BigDecimal.ONE;
        row.lockedQuantity = BigDecimal.ZERO;
        row.netProceedsLcy = bd("10.00");
        row.averageSalePriceLcy = bd("12.50");
        row.gmvLcy = bd("12.50");
        row.salePriceFactCount = 1;
        row.currency = "SAR";
        row.fxRateToCny = null;
        row.profitCny = null;
        row.profitRate = null;
        row.qualityStatusJson = "[\"MISSING_FX_RATE\"]";
        return row;
    }

    private static AttributionReadRow attributionRow() {
        AttributionReadRow row = new AttributionReadRow();
        row.id = 900L;
        row.orderNr = "ORDER-1";
        row.itemNr = "ITEM-1";
        row.orderTime = LocalDateTime.parse("2026-05-02T10:30:00");
        row.partnerSku = "PSKU-1";
        row.attributedQuantity = bd("2");
        row.attributionMethod = "FIFO";
        row.locked = false;
        row.netProceedsLcy = bd("20.00");
        row.referralFeeLcy = bd("2.00");
        row.fulfillmentFeeLcy = bd("1.00");
        row.otherFeeNetLcy = bd("0.50");
        row.currency = "SAR";
        return row;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
