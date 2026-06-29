package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyRematchCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.StockCorrectionCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyRematchSummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyAsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ScheduledDeliveryAccuracySummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundStageRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnLineMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptHistoryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptSummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotSourceRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryWarehouseStockRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ProductStockSourceCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionEventRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockSourceRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InboundStatisticsView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ProductInboundHistoryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyRematchResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsQuery;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsRowView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductListDatasetView;
import com.nuono.next.product.ProductMasterFetchCommand;
import com.nuono.next.product.ProductReadModelService;
import com.nuono.next.store.LocalDbStoreInitializationService;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LocalDbOfficialWarehouseStatisticsServiceTest {

    private final FakeStatisticsMapper mapper = new FakeStatisticsMapper();
    private final LocalDbOfficialWarehouseStatisticsService service = new LocalDbOfficialWarehouseStatisticsService(mapper);

    @Test
    void fallbackStockStartsAsPendingConfirmationAndExcludesFbpStock() {
        mapper.stockSources.add(stockSource(9001L, 42, 8, 999));

        StockStatisticsView view = service.stockStatistics(access(), stockQuery());

        assertThat(view.summary.currentStock).isEqualTo(50);
        assertThat(view.summary.effectiveStock).isZero();
        assertThat(view.summary.pendingConfirmationStock).isEqualTo(50);
        assertThat(view.summary.returnStock).isZero();
        assertThat(view.summary.failedOrExceptionStock).isZero();
        assertThat(view.summary.skuCount).isEqualTo(1);
        assertThat(view.rows).hasSize(1);
        StockStatisticsRowView row = view.rows.get(0);
        assertThat(row.currentStock).isEqualTo(50);
        assertThat(row.pendingConfirmationStock).isEqualTo(50);
        assertThat(row.effectiveStock).isZero();
        assertThat(row.inventoryConfidence).isEqualTo("PENDING_CONFIRMATION_ONLY");
        assertThat(row.sourceType).isEqualTo("PRODUCT_SITE_OFFER_FALLBACK");
    }

    @Test
    void inventorySnapshotRowsOverrideFallbackAndPreserveClassifiedBuckets() {
        mapper.stockSources.add(stockSource(9001L, 42, 8, 999));
        mapper.inventorySnapshots.add(inventorySnapshot(9001L, 7, 2, 3, 1));

        StockStatisticsView view = service.stockStatistics(access(), stockQuery());

        assertThat(mapper.listStockSourcesCallCount).isZero();
        assertThat(view.summary.currentStock).isEqualTo(13);
        assertThat(view.summary.effectiveStock).isEqualTo(7);
        assertThat(view.summary.returnStock).isEqualTo(2);
        assertThat(view.summary.failedOrExceptionStock).isEqualTo(3);
        assertThat(view.summary.pendingConfirmationStock).isEqualTo(1);
        assertThat(view.rows).hasSize(1);
        StockStatisticsRowView row = view.rows.get(0);
        assertThat(row.currentStock).isEqualTo(13);
        assertThat(row.effectiveStock).isEqualTo(7);
        assertThat(row.returnStock).isEqualTo(2);
        assertThat(row.failedOrExceptionStock).isEqualTo(3);
        assertThat(row.pendingConfirmationStock).isEqualTo(1);
        assertThat(row.sourceType).isEqualTo("FBN_INVENTORY_API");
        assertThat(row.inventoryConfidence).isEqualTo("CLASSIFIED_INVENTORY");
        assertThat(row.warehouseStocks).hasSize(1);
        assertThat(row.warehouseStocks.get(0).warehouseCode).isEqualTo("RUH01");
        assertThat(row.warehouseStocks.get(0).currentStock).isEqualTo(13);
    }

    @Test
    void productPerspectiveUsesCommonProductListAndOverlaysInventory() {
        ProductReadModelService productReadModelService = mock(ProductReadModelService.class);
        when(productReadModelService.loadListDataset(any(ProductMasterFetchCommand.class))).thenReturn(productDataset(
                productItem("PAPERSAYSB422", "PAPERSAYSB422", "ZSA_PAPERSAYSB422", "N422", "中文商品名"),
                productItem("NOONOSTOCK", "NOONOSTOCK", "ZSA_NOONOSTOCK", "N000", "无库存商品")
        ));
        LocalDbOfficialWarehouseStatisticsService productPerspectiveService =
                new LocalDbOfficialWarehouseStatisticsService(mapper, productReadModelService);
        mapper.inventorySnapshots.add(inventorySnapshot(9001L, 7, 2, 3, 1));
        mapper.inventoryWarehouseStocks.add(warehouseStock(9001L, "RUH01", 5, 1, 0, 0));
        mapper.inventoryWarehouseStocks.add(warehouseStock(9001L, "JED01", 2, 1, 3, 1));

        StockStatisticsView view = productPerspectiveService.stockStatistics(access(), stockQuery());

        assertThat(view.summary.skuCount).isEqualTo(2);
        assertThat(view.summary.currentStock).isEqualTo(13);
        assertThat(view.rows).hasSize(2);
        StockStatisticsRowView stocked = view.rows.get(0);
        assertThat(stocked.skuParent).isEqualTo("PAPERSAYSB422");
        assertThat(stocked.titleCn).isEqualTo("中文商品名");
        assertThat(stocked.titleEn).isEqualTo("English title");
        assertThat(stocked.pskuCode).isEqualTo("ZSA_PAPERSAYSB422");
        assertThat(stocked.imageUrl).isEqualTo("https://image.test/PAPERSAYSB422.jpg");
        assertThat(stocked.currentStock).isEqualTo(13);
        assertThat(stocked.inventoryConfidence).isEqualTo("CLASSIFIED_INVENTORY");
        assertThat(stocked.warehouseStocks).extracting(stock -> stock.warehouseCode)
                .containsExactly("RUH01", "JED01");
        assertThat(stocked.warehouseStocks).extracting(stock -> stock.currentStock)
                .containsExactly(6L, 7L);
        StockStatisticsRowView noInventory = view.rows.get(1);
        assertThat(noInventory.skuParent).isEqualTo("NOONOSTOCK");
        assertThat(noInventory.currentStock).isZero();
        assertThat(noInventory.inventoryConfidence).isEqualTo("NO_INVENTORY_RECORD");
        assertThat(noInventory.sourceType).isEqualTo("PRODUCT_MASTER_LIST");
    }

    @Test
    void manualCorrectionMovesQuantitiesOutOfPendingWithoutOverwritingFallbackStock() {
        mapper.stockSources.add(stockSource(9001L, 42, 8, 999));
        mapper.corrections.add(correction(1L, 9001L, "PENDING_CONFIRMATION", "SELLABLE", 12));
        mapper.corrections.add(correction(2L, 9001L, "PENDING_CONFIRMATION", "RETURNED", 3));
        mapper.corrections.add(correction(3L, 9001L, "PENDING_CONFIRMATION", "DAMAGED", 5));

        StockStatisticsView view = service.stockStatistics(access(), stockQuery());

        assertThat(view.summary.currentStock).isEqualTo(50);
        assertThat(view.summary.effectiveStock).isEqualTo(12);
        assertThat(view.summary.returnStock).isEqualTo(3);
        assertThat(view.summary.failedOrExceptionStock).isEqualTo(5);
        assertThat(view.summary.pendingConfirmationStock).isEqualTo(30);
        StockStatisticsRowView row = view.rows.get(0);
        assertThat(row.effectiveStock).isEqualTo(12);
        assertThat(row.returnStock).isEqualTo(3);
        assertThat(row.failedOrExceptionStock).isEqualTo(5);
        assertThat(row.pendingConfirmationStock).isEqualTo(30);
        assertThat(row.sourceType).isEqualTo("MANUAL_CORRECTION");
    }

    @Test
    void correctionCannotMakeBucketsExceedCurrentStockWithoutAnomalyFlag() {
        mapper.stockSources.add(stockSource(9001L, 10, 0, 0));
        mapper.corrections.add(correction(1L, 9001L, "PENDING_CONFIRMATION", "SELLABLE", 12));

        StockStatisticsView view = service.stockStatistics(access(), stockQuery());

        StockStatisticsRowView row = view.rows.get(0);
        assertThat(row.currentStock).isEqualTo(10);
        assertThat(row.effectiveStock).isEqualTo(10);
        assertThat(row.pendingConfirmationStock).isZero();
        assertThat(row.anomalyFlags).containsExactly("BUCKET_QUANTITY_EXCEEDS_CURRENT_STOCK");
        assertThat(view.summary.exceptionSkuCount).isEqualTo(1);
    }

    @Test
    void inboundStatisticsUsesNoonAsnStatusInsteadOfLocalStatus() {
        mapper.inboundRows.add(inbound(1L, "LINES_CREATED", "grn_completed", "SCHEDULED", 100));
        mapper.inboundRows.add(inbound(2L, "LINES_CREATED", "receiving", "RUNNING", 20));
        mapper.inboundRows.add(inbound(3L, "FAILED", "expired", "FAILED", 5));

        InboundStatisticsView view = service.inboundStatistics(access(), "STR108065-NSA", "SA", null, null, null, null);

        assertThat(view.summary.asnCount).isEqualTo(3);
        assertThat(view.summary.totalQuantity).isEqualTo(125);
        assertThat(view.summary.grnCompletedAsnCount).isEqualTo(1);
        assertThat(view.summary.receivingAsnCount).isEqualTo(1);
        assertThat(view.summary.failedAsnCount).isEqualTo(1);
        assertThat(view.summary.appointmentScheduledCount).isEqualTo(1);
        assertThat(view.summary.appointmentPendingCount).isEqualTo(1);
        assertThat(view.summary.appointmentFailedCount).isEqualTo(1);
        assertThat(view.summary.lineReceiptReportConnected).isFalse();
    }

    @Test
    void inboundStatisticsConnectsLatestFbnReceivedReportSummaryEvenWhenLocalAsnMissing() {
        mapper.inboundRows.add(inbound(1L, "LINES_CREATED", "receiving", "SCHEDULED", 4));
        mapper.inboundReceiptSummary.asnCount = 2;
        mapper.inboundReceiptSummary.receiptLineCount = 3;
        mapper.inboundReceiptSummary.expectedQuantity = 10L;
        mapper.inboundReceiptSummary.receivedQuantity = 9L;
        mapper.inboundReceiptSummary.qcFailedQuantity = 1L;
        mapper.inboundReceiptSummary.unidentifiedQuantity = 0L;
        mapper.inboundReceiptSummary.normalLineCount = 1;
        mapper.inboundReceiptSummary.qcFailedLineCount = 1;
        mapper.inboundReceiptSummary.shortReceivedLineCount = 1;
        mapper.inboundReceiptSummary.overReceivedLineCount = 0;
        mapper.inboundReceiptSummary.unidentifiedLineCount = 0;
        mapper.inboundReceiptSummary.matchedLineCount = 1;
        mapper.inboundReceiptSummary.noLocalAsnLineCount = 2;
        mapper.inboundReceiptSummary.lineUnmatchedLineCount = 0;
        mapper.inboundReceiptSummary.productUnmatchedLineCount = 0;
        mapper.inboundReceiptSummary.receiptExceptionLineCount = 2;
        mapper.inboundReceiptSummary.latestImportId = 623001L;
        mapper.inboundReceiptSummary.latestImportedAt = "2026-06-18 15:55:00";

        InboundStatisticsView view = service.inboundStatistics(access(), "STR108065-NSA", "SA", null, null, null, null);

        assertThat(view.summary.lineReceiptReportConnected).isTrue();
        assertThat(view.summary.asnCount).isEqualTo(2);
        assertThat(view.summary.totalQuantity).isEqualTo(10);
        assertThat(view.summary.receiptLineCount).isEqualTo(3);
        assertThat(view.summary.expectedQuantity).isEqualTo(10);
        assertThat(view.summary.receivedQuantity).isEqualTo(9);
        assertThat(view.summary.qcFailedQuantity).isEqualTo(1);
        assertThat(view.summary.shortReceivedLineCount).isEqualTo(1);
        assertThat(view.summary.qcFailedLineCount).isEqualTo(1);
        assertThat(view.summary.noLocalAsnLineCount).isEqualTo(2);
        assertThat(view.summary.receiptExceptionLineCount).isEqualTo(2);
        assertThat(view.summary.latestReceiptImportId).isEqualTo("623001");
        assertThat(view.summary.latestReceiptImportedAt).isEqualTo("2026-06-18 15:55:00");
    }

    @Test
    void inboundStatisticsConnectsScheduledDeliveryAccuracySummaryAfterRematch() {
        mapper.inboundRows.add(inbound(1L, "LINES_CREATED", "receiving", "SCHEDULED", 4));
        mapper.scheduledDeliveryAccuracySummary.latestImportId = 623003L;
        mapper.scheduledDeliveryAccuracySummary.latestImportedAt = "2026-06-22 18:20:00";
        mapper.scheduledDeliveryAccuracySummary.asnCount = 78;
        mapper.scheduledDeliveryAccuracySummary.scheduledQuantity = 4200L;
        mapper.scheduledDeliveryAccuracySummary.grnQuantity = 4186L;
        mapper.scheduledDeliveryAccuracySummary.inboundQuantityVariance = 14L;
        mapper.scheduledDeliveryAccuracySummary.putawayCompletedAsnCount = 61;
        mapper.scheduledDeliveryAccuracySummary.cancelledAsnCount = 0;
        mapper.scheduledDeliveryAccuracySummary.expiredAsnCount = 17;
        mapper.scheduledDeliveryAccuracySummary.matchedAsnCount = 78;
        mapper.scheduledDeliveryAccuracySummary.noLocalAsnCount = 0;
        mapper.scheduledDeliveryAccuracySummary.exceptionAsnCount = 17;

        InboundStatisticsView view = service.inboundStatistics(access(), "STR108065-NSA", "SA", null, null, null, null);

        assertThat(view.summary.scheduledDeliveryAccuracyConnected).isTrue();
        assertThat(view.summary.latestScheduledDeliveryAccuracyImportId).isEqualTo("623003");
        assertThat(view.summary.latestScheduledDeliveryAccuracyImportedAt).isEqualTo("2026-06-22 18:20:00");
        assertThat(view.summary.scheduledDeliveryAccuracyAsnCount).isEqualTo(78);
        assertThat(view.summary.scheduledQuantity).isEqualTo(4200);
        assertThat(view.summary.grnQuantity).isEqualTo(4186);
        assertThat(view.summary.inboundQuantityVariance).isEqualTo(14);
        assertThat(view.summary.putawayCompletedAsnCount).isEqualTo(61);
        assertThat(view.summary.cancelledAsnCount).isZero();
        assertThat(view.summary.expiredAsnCount).isEqualTo(17);
        assertThat(view.summary.matchedScheduledDeliveryAccuracyAsnCount).isEqualTo(78);
        assertThat(view.summary.noLocalScheduledDeliveryAccuracyAsnCount).isZero();
        assertThat(view.summary.scheduledDeliveryAccuracyExceptionAsnCount).isEqualTo(17);
    }

    @Test
    void rematchScheduledDeliveryAccuracyReattachesRowsAfterLocalAsnSync() {
        mapper.rematchSummaries.add(rematchSummary(78, 0, 78));
        mapper.rematchedRows = 61;
        mapper.rematchSummaries.add(rematchSummary(78, 61, 17));
        ScheduledDeliveryAccuracyRematchCommand command = new ScheduledDeliveryAccuracyRematchCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        ScheduledDeliveryAccuracyRematchResultView result =
                service.rematchScheduledDeliveryAccuracy(access(), "623003", command);

        assertThat(mapper.rematchImportId).isEqualTo(623003L);
        assertThat(mapper.rematchOperatorUserId).isEqualTo(307L);
        assertThat(result.importId).isEqualTo("623003");
        assertThat(result.storeCode).isEqualTo("STR108065-NSA");
        assertThat(result.siteCode).isEqualTo("SA");
        assertThat(result.totalRows).isEqualTo(78);
        assertThat(result.noLocalAsnRowsBefore).isEqualTo(78);
        assertThat(result.rematchedRows).isEqualTo(61);
        assertThat(result.matchedRowsAfter).isEqualTo(61);
        assertThat(result.noLocalAsnRowsAfter).isEqualTo(17);
    }

    @Test
    void stockCorrectionWritesAppendOnlyEventAndReturnsRecalculatedRow() {
        mapper.stockSources.add(stockSource(9001L, 20, 0, 0));
        StockCorrectionCommand command = new StockCorrectionCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        command.targetRefType = "PRODUCT_SITE_OFFER_FALLBACK";
        command.targetRefId = "9001";
        command.productVariantId = "8001";
        command.productSiteOfferId = "9001";
        command.fromStockBucket = "PENDING_CONFIRMATION";
        command.toStockBucket = "SELLABLE";
        command.quantity = 6;
        command.warehouseCode = "NOON_FALLBACK";
        command.reasonCode = "MANUAL_CONFIRMED";
        command.reasonText = "运营确认可售";

        StockStatisticsRowView row = service.correctStock(access(), command);

        assertThat(mapper.insertedCorrections).hasSize(1);
        StockCorrectionInsertRecord inserted = mapper.insertedCorrections.get(0);
        assertThat(inserted.correctionType).isEqualTo("CLASSIFY_STOCK");
        assertThat(inserted.targetRefType).isEqualTo("PRODUCT_SITE_OFFER_FALLBACK");
        assertThat(inserted.toStockBucket).isEqualTo("SELLABLE");
        assertThat(inserted.quantity).isEqualTo(6);
        assertThat(row.effectiveStock).isEqualTo(6);
        assertThat(row.pendingConfirmationStock).isEqualTo(14);
    }

    @Test
    void productInboundHistoryIsReadOnlyAndFilteredByProductSiteOffer() {
        mapper.productInboundHistoryRows.add(inboundHistory(9001L, "A05500001", 12, 11, 1, "QC_FAILED"));
        mapper.productInboundHistoryRows.add(inboundHistory(9002L, "A05500002", 5, 5, 0, "NORMAL"));
        mapper.productStockSourceCandidates.add(sourceCandidate("700001", "BATCH-001", "200001", "PO-001", 30));

        ProductInboundHistoryView view = service.productInboundHistory(access(), "STR108065-NSA", "SA", "9001");

        assertThat(mapper.historyProductSiteOfferId).isEqualTo(9001L);
        assertThat(view.summary.receiptLineCount).isEqualTo(1);
        assertThat(view.summary.expectedQuantity).isEqualTo(12);
        assertThat(view.summary.receivedQuantity).isEqualTo(11);
        assertThat(view.summary.qcFailedQuantity).isEqualTo(1);
        assertThat(view.rows).hasSize(1);
        assertThat(view.rows.get(0).noonAsnNr).isEqualTo("A05500001");
        assertThat(view.rows.get(0).receiptStatus).isEqualTo("QC_FAILED");
        assertThat(view.sourceCandidates).hasSize(1);
        assertThat(view.sourceCandidates.get(0).logisticsBatchNo).isEqualTo("BATCH-001");
        assertThat(view.sourceCandidates.get(0).purchaseOrderNo).isEqualTo("PO-001");
        assertThat(view.sourceCandidates.get(0).quantity).isEqualTo(30);
        assertThat(view.sourceCandidates.get(0).relationBasis).isEqualTo("同商品 / 同站点 / 物流批次来源");
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/warehouse/official-warehouse"))
                .build();
    }

    private static StockStatisticsQuery stockQuery() {
        StockStatisticsQuery query = new StockStatisticsQuery();
        query.storeCode = "STR108065-NSA";
        query.siteCode = "SA";
        return query;
    }

    private static ProductListDatasetView productDataset(
            LocalDbStoreInitializationService.StoreInitializationProductListItemView... items
    ) {
        ProductListDatasetView view = new ProductListDatasetView();
        view.setOwnerUserId(307L);
        view.setStoreCode("STR108065-NSA");
        view.setProjectCode("PRJ108065");
        view.setItems(List.of(items));
        return view;
    }

    private static LocalDbStoreInitializationService.StoreInitializationProductListItemView productItem(
            String skuParent,
            String partnerSku,
            String pskuCode,
            String offerCode,
            String titleCn
    ) {
        LocalDbStoreInitializationService.StoreInitializationProductListItemView item =
                new LocalDbStoreInitializationService.StoreInitializationProductListItemView();
        item.setReferenceStoreCode("STR108065-NSA");
        item.setSkuParent(skuParent);
        item.setPartnerSku(partnerSku);
        item.setPskuCode(pskuCode);
        item.setOfferCode(offerCode);
        item.setTitle("English title");
        item.setTitleCn(titleCn);
        item.setImageUrl("https://image.test/" + skuParent + ".jpg");
        return item;
    }

    private static StockSourceRecord stockSource(Long productSiteOfferId, int fbnStock, int supermallStock, int fbpStock) {
        StockSourceRecord record = new StockSourceRecord();
        record.ownerUserId = 307L;
        record.logicalStoreId = 7001L;
        record.storeCode = "STR108065-NSA";
        record.storeName = "canman";
        record.siteCode = "SA";
        record.projectCode = "PRJ108065";
        record.partnerId = "108065";
        record.productMasterId = 7001L;
        record.productVariantId = 8001L;
        record.productSiteOfferId = productSiteOfferId;
        record.skuParent = "PAPERSAYSB422";
        record.partnerSku = "PAPERSAYSB422";
        record.pskuCode = "ZSA_PAPERSAYSB422";
        record.noonSku = "N422";
        record.title = "Paper Says Cards";
        record.brand = "Paper Says";
        record.fbnStock = fbnStock;
        record.supermallStock = supermallStock;
        record.fbpStock = fbpStock;
        record.lastSyncedAt = "2026-06-17 09:37:08";
        return record;
    }

    private static InventorySnapshotSourceRecord inventorySnapshot(
            Long productSiteOfferId,
            int effectiveStock,
            int returnStock,
            int failedOrExceptionStock,
            int pendingConfirmationStock
    ) {
        InventorySnapshotSourceRecord record = new InventorySnapshotSourceRecord();
        record.ownerUserId = 307L;
        record.logicalStoreId = 7001L;
        record.storeCode = "STR108065-NSA";
        record.storeName = "canman";
        record.siteCode = "SA";
        record.projectCode = "PRJ108065";
        record.partnerId = "108065";
        record.productMasterId = 7001L;
        record.productVariantId = 8001L;
        record.productSiteOfferId = productSiteOfferId;
        record.skuParent = "PAPERSAYSB422";
        record.partnerSku = "PAPERSAYSB422";
        record.pskuCode = "ZSA_PAPERSAYSB422";
        record.noonSku = "N422";
        record.title = "Paper Says Cards";
        record.brand = "Paper Says";
        record.imageUrl = "https://image.test/papersays.jpg";
        record.warehouseCode = "RUH01";
        record.effectiveStock = (long) effectiveStock;
        record.returnStock = (long) returnStock;
        record.failedOrExceptionStock = (long) failedOrExceptionStock;
        record.pendingConfirmationStock = (long) pendingConfirmationStock;
        record.currentStock = (long) effectiveStock + returnStock + failedOrExceptionStock + pendingConfirmationStock;
        record.lastSyncedAt = "2026-06-18 10:11:12";
        record.inventoryConfidence = "CLASSIFIED_INVENTORY";
        return record;
    }

    private static InventoryWarehouseStockRecord warehouseStock(
            Long productSiteOfferId,
            String warehouseCode,
            int effectiveStock,
            int returnStock,
            int failedOrExceptionStock,
            int pendingConfirmationStock
    ) {
        InventoryWarehouseStockRecord record = new InventoryWarehouseStockRecord();
        record.productSiteOfferId = productSiteOfferId;
        record.partnerSku = "PAPERSAYSB422";
        record.pskuCode = "ZSA_PAPERSAYSB422";
        record.noonSku = "N422";
        record.warehouseCode = warehouseCode;
        record.effectiveStock = (long) effectiveStock;
        record.returnStock = (long) returnStock;
        record.failedOrExceptionStock = (long) failedOrExceptionStock;
        record.pendingConfirmationStock = (long) pendingConfirmationStock;
        record.currentStock = (long) effectiveStock + returnStock + failedOrExceptionStock + pendingConfirmationStock;
        return record;
    }

    private static StockCorrectionEventRecord correction(
            Long id,
            Long productSiteOfferId,
            String fromBucket,
            String toBucket,
            int quantity
    ) {
        StockCorrectionEventRecord record = new StockCorrectionEventRecord();
        record.id = id;
        record.correctionType = "CLASSIFY_STOCK";
        record.targetRefType = "PRODUCT_SITE_OFFER_FALLBACK";
        record.targetRefId = productSiteOfferId;
        record.productSiteOfferId = productSiteOfferId;
        record.warehouseCode = "NOON_FALLBACK";
        record.fromStockBucket = fromBucket;
        record.toStockBucket = toBucket;
        record.quantity = quantity;
        return record;
    }

    private static InboundStageRecord inbound(
            Long asnId,
            String localStatus,
            String noonStatus,
            String appointmentStatus,
            int totalQuantity
    ) {
        InboundStageRecord record = new InboundStageRecord();
        record.asnId = asnId;
        record.localAsnNo = "OWA-" + asnId;
        record.noonAsnNr = "A" + asnId;
        record.status = localStatus;
        record.noonAsnStatus = noonStatus;
        record.totalQuantity = totalQuantity;
        record.noonTotalQty = totalQuantity;
        record.appointmentStatus = appointmentStatus;
        return record;
    }

    private static DeliveryAccuracyRematchSummaryRecord rematchSummary(
            int totalRows,
            int matchedRows,
            int noLocalAsnRows
    ) {
        DeliveryAccuracyRematchSummaryRecord record = new DeliveryAccuracyRematchSummaryRecord();
        record.totalRows = totalRows;
        record.matchedRows = matchedRows;
        record.noLocalAsnRows = noLocalAsnRows;
        return record;
    }

    private static InboundReceiptHistoryRecord inboundHistory(
            Long productSiteOfferId,
            String noonAsnNr,
            int expected,
            int received,
            int qcFailed,
            String receiptStatus
    ) {
        InboundReceiptHistoryRecord record = new InboundReceiptHistoryRecord();
        record.importId = 623001L;
        record.reportRowId = 624001L;
        record.productSiteOfferId = productSiteOfferId;
        record.noonAsnNr = noonAsnNr;
        record.partnerSku = "PAPERSAYSB422";
        record.pskuCode = "ZSA_PAPERSAYSB422";
        record.noonSku = "N422";
        record.qtyExpected = expected;
        record.receivedQty = received;
        record.qcFailedQty = qcFailed;
        record.unidentifiedQty = 0;
        record.receiptStatus = receiptStatus;
        record.matchStatus = "MATCHED";
        record.partnerWarehouse = "ETWAREHOUSE";
        record.noonWarehouse = "RUH01";
        record.asnCreatedAt = "2026-06-01 10:00:00";
        record.asnScheduleDate = "2026-06-04";
        record.asnCompletedAt = "2026-06-05 18:00:00";
        record.importedAt = "2026-06-22 11:00:00";
        return record;
    }

    private static ProductStockSourceCandidateRecord sourceCandidate(
            String batchId,
            String batchNo,
            String orderId,
            String orderNo,
            long quantity
    ) {
        ProductStockSourceCandidateRecord record = new ProductStockSourceCandidateRecord();
        record.logisticsBatchId = Long.valueOf(batchId);
        record.logisticsBatchNo = batchNo;
        record.logisticsStatus = "READY";
        record.purchaseOrderId = Long.valueOf(orderId);
        record.purchaseOrderNo = orderNo;
        record.sourceStoreCode = "采购店铺";
        record.siteCode = "SA";
        record.partnerSku = "PAPERSAYSB422";
        record.quantity = quantity;
        record.latestAt = "2026-06-20 10:00:00";
        return record;
    }

    private static final class FakeStatisticsMapper implements OfficialWarehouseStatisticsMapper {
        private final List<StockSourceRecord> stockSources = new ArrayList<>();
        private final List<InventorySnapshotSourceRecord> inventorySnapshots = new ArrayList<>();
        private final List<InventoryWarehouseStockRecord> inventoryWarehouseStocks = new ArrayList<>();
        private final List<StockCorrectionEventRecord> corrections = new ArrayList<>();
        private final List<StockCorrectionInsertRecord> insertedCorrections = new ArrayList<>();
        private final List<InboundStageRecord> inboundRows = new ArrayList<>();
        private final List<InboundReceiptHistoryRecord> productInboundHistoryRows = new ArrayList<>();
        private final List<ProductStockSourceCandidateRecord> productStockSourceCandidates = new ArrayList<>();
        private final List<DeliveryAccuracyRematchSummaryRecord> rematchSummaries = new ArrayList<>();
        private final InboundReceiptSummaryRecord inboundReceiptSummary = new InboundReceiptSummaryRecord();
        private final ScheduledDeliveryAccuracySummaryRecord scheduledDeliveryAccuracySummary =
                new ScheduledDeliveryAccuracySummaryRecord();
        private long nextId = 990000L;
        private int listStockSourcesCallCount;
        private Long rematchImportId;
        private Long rematchOperatorUserId;
        private Long historyProductSiteOfferId;
        private int rematchedRows;

        @Override
        public int allocateId(IdSequenceCommand command) {
            command.setAllocatedId(nextId++);
            return 1;
        }

        @Override
        public Long selectMaxStockCorrectionId() {
            return nextId - 1;
        }

        @Override
        public Long selectMaxInventorySyncBatchId() {
            return nextId - 1;
        }

        @Override
        public Long selectMaxInventorySnapshotLineId() {
            return nextId - 1;
        }

        @Override
        public Long selectMaxReportImportId() {
            return nextId - 1;
        }

        @Override
        public Long selectMaxReportRowId() {
            return nextId - 1;
        }

        @Override
        public Long selectMaxInboundReceiptLineId() {
            return nextId - 1;
        }

        @Override
        public Long selectMaxDeliveryAccuracyAsnId() {
            return nextId - 1;
        }

        @Override
        public int ensureSequenceAtLeast(String sequenceName, Long minAllocatedId) {
            nextId = Math.max(nextId, minAllocatedId == null ? nextId : minAllocatedId + 1);
            return 1;
        }

        @Override
        public List<StockSourceRecord> listStockSources(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String keywordLike,
                String warehouseCode,
                String stockBucket
        ) {
            listStockSourcesCallCount += 1;
            return stockSources;
        }

        @Override
        public List<InventorySnapshotSourceRecord> listInventorySnapshotSources(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String keywordLike,
                String warehouseCode,
                String stockBucket
        ) {
            return inventorySnapshots;
        }

        @Override
        public List<InventoryWarehouseStockRecord> listInventorySnapshotWarehouseStocks(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String warehouseCode
        ) {
            return inventoryWarehouseStocks;
        }

        @Override
        public List<InventorySnapshotSourceRecord> listUnmatchedInventorySnapshotSources(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String keywordLike,
                String warehouseCode,
                String stockBucket
        ) {
            return List.of();
        }

        @Override
        public List<StockCorrectionEventRecord> listStockCorrections(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                Collection<Long> productSiteOfferIds
        ) {
            List<StockCorrectionEventRecord> result = new ArrayList<>(corrections);
            for (StockCorrectionInsertRecord inserted : insertedCorrections) {
                result.add(correction(inserted.id, inserted.productSiteOfferId, inserted.fromStockBucket, inserted.toStockBucket, inserted.quantity));
            }
            return result;
        }

        @Override
        public List<InboundStageRecord> listInboundStageRows(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String keywordLike,
                String asn,
                String warehouseCode,
                String receiptStatus
        ) {
            return inboundRows;
        }

        @Override
        public InboundReceiptSummaryRecord selectInboundReceiptSummary(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String keywordLike,
                String asn,
                String warehouseCode,
                String receiptStatus,
                String reportType
        ) {
            return inboundReceiptSummary;
        }

        @Override
        public List<InboundReceiptHistoryRecord> listProductInboundReceiptHistory(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                Long productSiteOfferId,
                int limit
        ) {
            historyProductSiteOfferId = productSiteOfferId;
            return productInboundHistoryRows.stream()
                    .filter(row -> productSiteOfferId.equals(row.productSiteOfferId))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<ProductStockSourceCandidateRecord> listProductStockSourceCandidates(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                Long productSiteOfferId,
                int limit
        ) {
            return productStockSourceCandidates;
        }

        @Override
        public ScheduledDeliveryAccuracySummaryRecord selectScheduledDeliveryAccuracySummary(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String keywordLike,
                String asn,
                String warehouseCode,
                String receiptStatus,
                String reportType
        ) {
            return scheduledDeliveryAccuracySummary;
        }

        @Override
        public Long nextStockCorrectionId() {
            return nextId++;
        }

        @Override
        public int insertStockCorrection(StockCorrectionInsertRecord record) {
            insertedCorrections.add(record);
            return 1;
        }

        @Override
        public InventorySyncScopeRecord selectInventorySyncScope(Long ownerUserId, String storeCode, String siteCode) {
            return null;
        }

        @Override
        public InventoryLineProductMatchRecord findInventoryLineProductMatch(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String noonSku,
                String partnerSku
        ) {
            return null;
        }

        @Override
        public InboundReceiptAsnMatchRecord findInboundReceiptAsnMatch(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String noonAsnNr
        ) {
            return null;
        }

        @Override
        public InboundReceiptAsnLineMatchRecord findInboundReceiptAsnLineMatch(
                Long asnId,
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String noonSku,
                String partnerSku
        ) {
            return null;
        }

        @Override
        public int insertInventorySyncBatch(InventorySyncBatchInsertRecord record) {
            return 1;
        }

        @Override
        public int deactivateCurrentInventorySnapshotLines(Long ownerUserId, String storeCode, String siteCode) {
            return 1;
        }

        @Override
        public int insertInventorySnapshotLine(InventorySnapshotLineInsertRecord record) {
            return 1;
        }

        @Override
        public int deactivatePreviousFbnReceivedReceiptLines(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String reportType,
                String sourceExportCode,
                Long operatorUserId
        ) {
            return 1;
        }

        @Override
        public int deactivatePreviousFbnReceivedReportRows(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String reportType,
                String sourceExportCode,
                Long operatorUserId
        ) {
            return 1;
        }

        @Override
        public int deactivatePreviousFbnReceivedReportImportRows(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String reportType,
                String sourceExportCode,
                Long operatorUserId
        ) {
            return 1;
        }

        @Override
        public int insertReportImport(ReportImportInsertRecord record) {
            return 1;
        }

        @Override
        public int insertReportRow(ReportRowInsertRecord record) {
            return 1;
        }

        @Override
        public int insertInboundReceiptLine(InboundReceiptLineInsertRecord record) {
            return 1;
        }

        @Override
        public int deactivatePreviousScheduledDeliveryAccuracyFacts(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String reportType,
                String sourceExportCode,
                Long operatorUserId
        ) {
            return 1;
        }

        @Override
        public int deactivatePreviousScheduledDeliveryAccuracyReportRows(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String reportType,
                String sourceExportCode,
                Long operatorUserId
        ) {
            return 1;
        }

        @Override
        public int deactivatePreviousScheduledDeliveryAccuracyReportImportRows(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String reportType,
                String sourceExportCode,
                Long operatorUserId
        ) {
            return 1;
        }

        @Override
        public int insertDeliveryAccuracyAsn(DeliveryAccuracyAsnInsertRecord record) {
            return 1;
        }

        @Override
        public DeliveryAccuracyRematchSummaryRecord selectDeliveryAccuracyRematchSummary(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                Long importId
        ) {
            return rematchSummaries.isEmpty() ? null : rematchSummaries.remove(0);
        }

        @Override
        public int rematchDeliveryAccuracyAsns(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                Long importId,
                Long operatorUserId
        ) {
            rematchImportId = importId;
            rematchOperatorUserId = operatorUserId;
            return rematchedRows;
        }

        @Override
        public List<String> listMissingDeliveryAccuracyNoonAsnNumbers(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                Long importId,
                int limit
        ) {
            return List.of();
        }
    }
}
