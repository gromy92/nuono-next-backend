package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyAsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyRematchSummaryRecord;
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
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ScheduledDeliveryAccuracySummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionEventRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockSourceRecord;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.IdSequenceCommand;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OfficialWarehouseInventorySyncServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FakeStatisticsMapper mapper = new FakeStatisticsMapper();
    private final FakeFbnInventoryProvider provider = new FakeFbnInventoryProvider(objectMapper);
    private final OfficialWarehouseInventorySyncService service =
            new OfficialWarehouseInventorySyncService(mapper, provider, objectMapper);

    @Test
    void syncReplacesCurrentInventorySnapshotLinesWithFetchedFbnInventoryRows() {
        mapper.scope.logicalStoreId = 7001L;
        mapper.scope.projectCode = "PRJ108065";
        mapper.scope.partnerId = "108065";
        mapper.match.productMasterId = 7001L;
        mapper.match.productVariantId = 8001L;
        mapper.match.productSiteOfferId = 9001L;
        mapper.match.skuParent = "PAPERSAYSB422";
        mapper.match.partnerSku = "PAPERSAYSB422";
        mapper.match.pskuCode = "ZSA_PAPERSAYSB422";
        mapper.match.noonSku = "N422";
        provider.pages.add(new InventoryPage(
                1,
                false,
                List.of(
                        InventoryItem.from(objectMapper.createObjectNode()
                                .put("warehouse_code", "RUH01")
                                .put("qty", 7)
                                .put("inventory_type", "saleable")
                                .put("sku", "N422")
                                .put("partner_sku", "PAPERSAYSB422")),
                        InventoryItem.from(objectMapper.createObjectNode()
                                .put("warehouse_code", "RUH01")
                                .put("qty", 2)
                                .put("inventory_type", "graded_returns_cir")
                                .put("reason_code", "customer_return")
                                .put("sku", "N422")
                                .put("partner_sku", "PAPERSAYSB422"))
                ),
                objectMapper.createObjectNode()
        ));
        InventorySyncCommand command = new InventorySyncCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        OfficialWarehouseStatisticsViews.InventorySyncResultView result = service.sync(access(), command);

        assertThat(provider.requests).hasSize(1);
        assertThat(provider.requests.get(0).storeCode).isEqualTo("STR108065-NSA");
        assertThat(mapper.deactivatedCurrentLines).containsExactly("307:STR108065-NSA:SA");
        assertThat(mapper.insertedBatches).hasSize(1);
        InventorySyncBatchInsertRecord batch = mapper.insertedBatches.get(0);
        assertThat(batch.sourceType).isEqualTo("FBN_INVENTORY_API");
        assertThat(batch.requestSummaryJson).doesNotContainIgnoringCase("cookie").doesNotContainIgnoringCase("authorization");
        assertThat(batch.totalRows).isEqualTo(2);
        assertThat(batch.validRows).isEqualTo(2);
        assertThat(mapper.insertedLines).hasSize(2);
        assertThat(mapper.insertedLines)
                .extracting(line -> line.stockBucket)
                .containsExactly("SELLABLE", "RETURNED");
        assertThat(mapper.markedInventoryLineIds)
                .containsExactlyElementsOf(mapper.insertedLines.stream().map(line -> line.id).collect(Collectors.toList()));
        assertThat(mapper.insertedLines)
                .allSatisfy(line -> {
                    assertThat(line.syncBatchId).isEqualTo(batch.id);
                    assertThat(line.matchStatus).isEqualTo("MATCHED");
                    assertThat(line.productSiteOfferId).isEqualTo(9001L);
                    assertThat(line.rawPayloadJson).doesNotContainIgnoringCase("cookie");
                });
        assertThat(result.syncBatchId).isEqualTo(String.valueOf(batch.id));
        assertThat(result.fetchedRows).isEqualTo(2);
        assertThat(result.insertedRows).isEqualTo(2);
    }

    @Test
    void syncDoesNotBackfillExternalPskuCodeFromPartnerSku() {
        mapper.scope.logicalStoreId = 7001L;
        mapper.scope.projectCode = "PRJ108065";
        mapper.scope.partnerId = "108065";
        mapper.match.productMasterId = 7001L;
        mapper.match.productVariantId = 8001L;
        mapper.match.productSiteOfferId = 9001L;
        mapper.match.partnerSku = "PAPERSAYSB422";
        mapper.match.pskuCode = null;
        provider.pages.add(new InventoryPage(
                1,
                false,
                List.of(InventoryItem.from(objectMapper.createObjectNode()
                        .put("warehouse_code", "RUH01")
                        .put("qty", 7)
                        .put("inventory_type", "saleable")
                        .put("partner_sku", "PAPERSAYSB422"))),
                objectMapper.createObjectNode()
        ));
        InventorySyncCommand command = new InventorySyncCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        service.sync(access(), command);

        assertThat(mapper.insertedLines).hasSize(1);
        InventorySnapshotLineInsertRecord line = mapper.insertedLines.get(0);
        assertThat(line.matchStatus).isEqualTo("MATCHED");
        assertThat(line.partnerSku).isEqualTo("PAPERSAYSB422");
        assertThat(line.pskuCode).isNull();

        mapper.insertedLines.clear();
        mapper.match.productVariantId = null;
        provider.pages.add(new InventoryPage(
                1,
                false,
                List.of(InventoryItem.from(objectMapper.createObjectNode()
                        .put("warehouse_code", "RUH01")
                        .put("qty", 3)
                        .put("inventory_type", "saleable")
                        .put("partner_sku", "PAPERSAYSB999"))),
                objectMapper.createObjectNode()
        ));

        service.sync(access(), command);

        assertThat(mapper.insertedLines).hasSize(1);
        InventorySnapshotLineInsertRecord unmatchedLine = mapper.insertedLines.get(0);
        assertThat(unmatchedLine.matchStatus).isEqualTo("PRODUCT_UNMATCHED");
        assertThat(unmatchedLine.partnerSku).isEqualTo("PAPERSAYSB999");
        assertThat(unmatchedLine.pskuCode).isNull();
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/warehouse/official-warehouse-stock"))
                .build();
    }

    private static class FakeFbnInventoryProvider extends OfficialWarehouseFbnInventoryProvider {
        private final List<InventoryPage> pages = new ArrayList<>();
        private final List<PullRequest> requests = new ArrayList<>();

        private FakeFbnInventoryProvider(ObjectMapper objectMapper) {
            super(objectMapper, null, null);
        }

        @Override
        public InventoryPage fetchPage(PullRequest request, int page) {
            requests.add(request);
            return pages.remove(0);
        }
    }

    private static final class FakeStatisticsMapper implements OfficialWarehouseStatisticsMapper {
        private final InventorySyncScopeRecord scope = new InventorySyncScopeRecord();
        private final InventoryLineProductMatchRecord match = new InventoryLineProductMatchRecord();
        private final List<InventorySyncBatchInsertRecord> insertedBatches = new ArrayList<>();
        private final List<InventorySnapshotLineInsertRecord> insertedLines = new ArrayList<>();
        private final List<String> deactivatedCurrentLines = new ArrayList<>();
        private final List<Long> markedInventoryLineIds = new ArrayList<>();
        private long nextId = 990000L;

        @Override
        public int allocateId(IdSequenceCommand command) {
            command.setAllocatedId(nextId++);
            return 1;
        }

        @Override
        public Long selectMaxStockCorrectionId() {
            return 0L;
        }

        @Override
        public int ensureSequenceAtLeast(String sequenceName, Long minAllocatedId) {
            return 1;
        }

        @Override
        public Long selectMaxInventorySyncBatchId() {
            return 0L;
        }

        @Override
        public Long selectMaxInventorySnapshotLineId() {
            return 0L;
        }

        @Override
        public Long selectMaxReportImportId() {
            return 0L;
        }

        @Override
        public Long selectMaxReportRowId() {
            return 0L;
        }

        @Override
        public Long selectMaxInboundReceiptLineId() {
            return 0L;
        }

        @Override
        public Long selectMaxDeliveryAccuracyAsnId() {
            return 0L;
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
            return List.of();
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
            return List.of();
        }

        @Override
        public List<InventoryWarehouseStockRecord> listInventorySnapshotWarehouseStocks(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                String warehouseCode
        ) {
            return List.of();
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
            return List.of();
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
            return List.of();
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
            return null;
        }

        @Override
        public List<InboundReceiptHistoryRecord> listProductInboundReceiptHistory(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                Long productSiteOfferId,
                String partnerSku,
                int limit
        ) {
            return List.of();
        }

        @Override
        public List<ProductStockSourceCandidateRecord> listProductStockSourceCandidates(
                Long ownerUserId,
                Collection<String> storeCodes,
                String storeCode,
                String siteCode,
                Long productSiteOfferId,
                String partnerSku,
                int limit
        ) {
            return List.of();
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
            return null;
        }

        @Override
        public InventorySyncScopeRecord selectInventorySyncScope(Long ownerUserId, String storeCode, String siteCode) {
            scope.ownerUserId = ownerUserId;
            scope.storeCode = storeCode;
            scope.siteCode = siteCode;
            return scope;
        }

        @Override
        public InventoryLineProductMatchRecord findInventoryLineProductMatch(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                String noonSku,
                String partnerSku
        ) {
            return match.productVariantId == null ? null : match;
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
        public Long nextInventorySyncBatchId() {
            return nextId++;
        }

        @Override
        public Long nextInventorySnapshotLineId() {
            return nextId++;
        }

        @Override
        public int insertInventorySyncBatch(InventorySyncBatchInsertRecord record) {
            insertedBatches.add(record);
            return 1;
        }

        @Override
        public int deactivateCurrentInventorySnapshotLines(Long ownerUserId, String storeCode, String siteCode) {
            deactivatedCurrentLines.add(ownerUserId + ":" + storeCode + ":" + siteCode);
            return 1;
        }

        @Override
        public int insertInventorySnapshotLine(InventorySnapshotLineInsertRecord record) {
            insertedLines.add(record);
            return 1;
        }

        @Override
        public int markProductSiteOfferLogisticsHistoryByInventorySnapshotLine(Long lineId, Long operatorUserId) {
            markedInventoryLineIds.add(lineId);
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
            return null;
        }

        @Override
        public int rematchDeliveryAccuracyAsns(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                Long importId,
                Long operatorUserId
        ) {
            return 0;
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

        @Override
        public Long nextStockCorrectionId() {
            return nextId++;
        }

        @Override
        public int insertStockCorrection(StockCorrectionInsertRecord record) {
            return 1;
        }
    }
}
