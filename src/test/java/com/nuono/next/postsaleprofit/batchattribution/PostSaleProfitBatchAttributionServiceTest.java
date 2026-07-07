package com.nuono.next.postsaleprofit.batchattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.PostSaleProfitBatchAttributionMapper;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionCandidateRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionCurrentStockRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionLineRow;
import com.nuono.next.postsaleprofit.batchattribution.PostSaleProfitBatchAttributionRecords.BatchAttributionSkuRow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PostSaleProfitBatchAttributionServiceTest {

    @Mock
    private PostSaleProfitBatchAttributionMapper mapper;

    @Test
    void classifiesCurrentStockRowsIntoOperationalFifoStatuses() {
        when(mapper.listSkuRows(307L, "STR108065-NSA", "SA", null, null, null))
                .thenReturn(List.of(
                        row("NO-ASN", 10, 0, 0, 1, 1, 0, 0),
                        row("BRIDGE-NO-PO", 12, 1, 1, 1, 1, 1, 0),
                        row("NO-PO", 8, 1, 1, 0, 0, 0, 0),
                        row("READY", 6, 1, 1, 1, 1, 0, 0),
                        row("AMBIGUOUS", 5, 2, 2, 1, 1, 0, 0)
                ));
        PostSaleProfitBatchAttributionService service = new PostSaleProfitBatchAttributionService(mapper);

        var view = service.listSummary(307L, "STR108065-NSA", "SA", null, null, null);

        assertThat(view.getSummary().getStockedSkuCount()).isEqualTo(5);
        assertThat(view.getSummary().getSellableStockQuantity()).isEqualByComparingTo("41");
        assertThat(view.getSummary().getFifoReadySkuCount()).isEqualTo(1);
        assertThat(view.getSummary().getMissingAsnSkuCount()).isEqualTo(1);
        assertThat(view.getSummary().getMissingPurchaseSkuCount()).isEqualTo(1);
        assertThat(view.getSummary().getAmbiguousSkuCount()).isEqualTo(1);
        assertThat(view.getSummary().getLogisticsLinkedPurchaseMissingSkuCount()).isEqualTo(1);
        assertThat(view.getRows()).extracting("partnerSku")
                .containsExactly("NO-ASN", "BRIDGE-NO-PO", "NO-PO", "READY", "AMBIGUOUS");
        assertThat(view.getRows()).extracting("attributionStatus")
                .containsExactly(
                        "missing_asn",
                        "logistics_linked_purchase_missing",
                        "missing_purchase",
                        "fifo_ready",
                        "ambiguous"
                );
        assertThat(view.getRows()).extracting("blocker")
                .containsExactly(
                        "stock_has_no_successful_asn_line",
                        "existing_asn_logistics_bridge_lacks_purchase_order_fields",
                        "no_purchase_order_for_sku_site",
                        "unique_fifo_candidate",
                        "multiple_candidate_dimensions"
                );
    }

    @Test
    void skuDetailAppendsCurrentStockTailAfterSoldProfitBatches() {
        when(mapper.listProfitBatchLines(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1"))
                .thenReturn(List.of(profitLine()));
        when(mapper.listCandidateBatchLines(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1"))
                .thenReturn(List.of());
        when(mapper.selectCurrentStock(307L, "STR108065-NSA", "SA", "PSKU-1"))
                .thenReturn(currentStock("PSKU-1", 9));
        PostSaleProfitBatchAttributionService service = new PostSaleProfitBatchAttributionService(mapper);

        var detail = service.getSkuDetail(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1");

        assertThat(detail.getPartnerSku()).isEqualTo("PSKU-1");
        assertThat(detail.getSoldQuantity()).isEqualByComparingTo("3");
        assertThat(detail.getSellableStockQuantity()).isEqualByComparingTo("9");
        assertThat(detail.getLines()).extracting("lineType")
                .containsExactly("profit_batch", "current_stock_tail");
        assertThat(detail.getLines().get(0).getSourceId()).isEqualTo("ALI1688_PRODUCT_LINK:1:2:PSKU-1");
        assertThat(detail.getLines().get(1).getStockQuantity()).isEqualByComparingTo("9");
        assertThat(detail.getLines().get(1).getLineStatus()).isEqualTo("stock_waiting_fifo_attribution");
    }

    @Test
    void skuDetailAllocatesCurrentStockTailAcrossPurchaseCandidatesByFifoAfterSoldBatches() {
        when(mapper.listProfitBatchLines(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1"))
                .thenReturn(List.of(profitLine("ALI1688_PRODUCT_LINK:1:2:PSKU-1", 8, 10)));
        when(mapper.selectCurrentStock(307L, "STR108065-NSA", "SA", "PSKU-1"))
                .thenReturn(currentStock("PSKU-1", 12));
        when(mapper.listCandidateBatchLines(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1"))
                .thenReturn(List.of(
                        candidate("ALI1688_PRODUCT_LINK:1:2:PSKU-1", "2026-02-01T10:00:00", 10, "PO-001", "XGGEKSA04071", "A05623021PN", "45.00"),
                        candidate("ALI1688_PRODUCT_LINK:3:4:PSKU-1", "2026-03-01T10:00:00", 20, "PO-002", "XGGEKSA04082", "A05623022PN", "90.00")
                ));
        PostSaleProfitBatchAttributionService service = new PostSaleProfitBatchAttributionService(mapper);

        var detail = service.getSkuDetail(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1");

        assertThat(detail.getSoldQuantity()).isEqualByComparingTo("8");
        assertThat(detail.getLines()).extracting("lineType")
                .containsExactly("profit_batch", "current_stock_tail", "current_stock_tail");
        assertThat(detail.getLines()).extracting("sourceId")
                .containsExactly(
                        "ALI1688_PRODUCT_LINK:1:2:PSKU-1",
                        "ALI1688_PRODUCT_LINK:1:2:PSKU-1",
                        "ALI1688_PRODUCT_LINK:3:4:PSKU-1"
                );
        assertThat(detail.getLines()).extracting("stockQuantity")
                .containsExactly(new BigDecimal("0"), new BigDecimal("2"), new BigDecimal("10"));
        assertThat(detail.getLines().get(1).getLineStatus()).isEqualTo("stock_fifo_candidate");
        assertThat(detail.getLines().get(1).getLogisticsBatchNo()).isEqualTo("XGGEKSA04071");
        assertThat(detail.getLines().get(2).getAsnNo()).isEqualTo("A05623022PN");
    }

    @Test
    void skuDetailKeepsUnallocatedCurrentStockTailWhenCandidatePoolCannotCoverStock() {
        when(mapper.listProfitBatchLines(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1"))
                .thenReturn(List.of());
        when(mapper.selectCurrentStock(307L, "STR108065-NSA", "SA", "PSKU-1"))
                .thenReturn(currentStock("PSKU-1", 9));
        when(mapper.listCandidateBatchLines(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1"))
                .thenReturn(List.of(candidate("ALI1688_PRODUCT_LINK:1:2:PSKU-1", "2026-02-01T10:00:00", 3, "PO-001", null, null, null)));
        PostSaleProfitBatchAttributionService service = new PostSaleProfitBatchAttributionService(mapper);

        var detail = service.getSkuDetail(307L, "STR108065-NSA", "SA", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31), "PSKU-1");

        assertThat(detail.getLines()).extracting("sourceId")
                .containsExactly("ALI1688_PRODUCT_LINK:1:2:PSKU-1", "OFFICIAL_WAREHOUSE_CURRENT_STOCK:PSKU-1");
        assertThat(detail.getLines()).extracting("stockQuantity")
                .containsExactly(new BigDecimal("3"), new BigDecimal("6"));
        assertThat(detail.getLines().get(0).getLineStatus()).isEqualTo("stock_fifo_candidate_missing_links");
        assertThat(detail.getLines().get(1).getLineStatus()).isEqualTo("stock_waiting_fifo_attribution");
        assertThat(detail.getLines().get(1).getPurchaseCostCny()).isNull();
    }

    private static BatchAttributionSkuRow row(
            String partnerSku,
            int stockQuantity,
            int asnCount,
            int inTransitBatchCount,
            int purchaseOrderCount,
            int purchaseOrderNoCount,
            int bridgeLogisticsCount,
            int bridgePurchaseOrderCount
    ) {
        BatchAttributionSkuRow row = new BatchAttributionSkuRow();
        row.partnerSku = partnerSku;
        row.skuParent = "PARENT";
        row.productTitle = "Product " + partnerSku;
        row.stockQuantity = new BigDecimal(stockQuantity);
        row.sellableStockQuantity = new BigDecimal(stockQuantity);
        row.soldQuantity = BigDecimal.ZERO;
        row.asnCount = asnCount;
        row.inTransitBatchCount = inTransitBatchCount;
        row.purchaseOrderCount = purchaseOrderCount;
        row.purchaseOrderNoCount = purchaseOrderNoCount;
        row.bridgeLogisticsCount = bridgeLogisticsCount;
        row.bridgePurchaseOrderCount = bridgePurchaseOrderCount;
        return row;
    }

    private static BatchAttributionLineRow profitLine() {
        return profitLine("ALI1688_PRODUCT_LINK:1:2:PSKU-1", 3, 20);
    }

    private static BatchAttributionLineRow profitLine(String sourceId, int soldQuantity, int purchaseQuantity) {
        BatchAttributionLineRow row = new BatchAttributionLineRow();
        row.lineType = "profit_batch";
        row.sourceId = sourceId;
        row.purchaseBatchTime = LocalDateTime.of(2026, 2, 1, 10, 0);
        row.purchaseQuantity = new BigDecimal(purchaseQuantity);
        row.soldQuantity = new BigDecimal(soldQuantity);
        row.purchaseCostCny = new BigDecimal("45.00");
        row.logisticsBatchNo = "XGGEKSA04071";
        row.asnNo = "A05623021PN";
        row.lineStatus = "sold_profit_batch";
        row.evidenceText = "post_sale_profit_batch";
        return row;
    }

    private static BatchAttributionCandidateRow candidate(
            String sourceId,
            String purchaseBatchTime,
            int purchaseQuantity,
            String purchaseOrderNo,
            String logisticsBatchNo,
            String asnNo,
            String purchaseCostCny
    ) {
        BatchAttributionCandidateRow row = new BatchAttributionCandidateRow();
        row.sourceType = "procurement_purchase_order_item_site";
        row.sourceId = sourceId;
        row.purchaseBatchTime = LocalDateTime.parse(purchaseBatchTime);
        row.purchaseQuantity = new BigDecimal(purchaseQuantity);
        row.purchaseOrderNo = purchaseOrderNo;
        row.logisticsBatchNo = logisticsBatchNo;
        row.asnNo = asnNo;
        row.purchaseCostCny = purchaseCostCny == null ? null : new BigDecimal(purchaseCostCny);
        row.evidenceText = "procurement_purchase_order_item_site candidate";
        return row;
    }

    private static BatchAttributionCurrentStockRow currentStock(String partnerSku, int quantity) {
        BatchAttributionCurrentStockRow row = new BatchAttributionCurrentStockRow();
        row.partnerSku = partnerSku;
        row.stockQuantity = new BigDecimal(quantity);
        row.sellableStockQuantity = new BigDecimal(quantity);
        return row;
    }
}
