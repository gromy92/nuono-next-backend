package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyRematchCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptSummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.StockCorrectionCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyRematchSummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptHistoryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundStageRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotSourceRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryWarehouseStockRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ProductStockSourceCandidateRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ScheduledDeliveryAccuracySummaryRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionEventRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockCorrectionInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.StockSourceRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InboundStageRowView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InboundStatisticsView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ProductInboundHistoryView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ProductInboundReceiptRowView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ProductStockSourceCandidateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyRematchResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsQuery;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsRowView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockStatisticsView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.StockWarehouseStockView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.product.ProductImageUrlSupport;
import com.nuono.next.product.ProductListDatasetView;
import com.nuono.next.product.ProductMasterFetchCommand;
import com.nuono.next.product.ProductReadModelService;
import com.nuono.next.store.LocalDbStoreInitializationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbOfficialWarehouseStatisticsService {

    private static final String FALLBACK_WAREHOUSE_CODE = "NOON_FALLBACK";
    private static final String PENDING_CONFIRMATION = "PENDING_CONFIRMATION";
    private static final String SELLABLE = "SELLABLE";
    private static final String RETURNED = "RETURNED";
    private static final Set<String> FAILED_OR_EXCEPTION_BUCKETS = Set.of(
            "INBOUND_FAILED",
            "RECEIVING_EXCEPTION",
            "DAMAGED",
            "LOST",
            "QUALITY_HOLD"
    );

    private final OfficialWarehouseStatisticsMapper mapper;
    private final ProductReadModelService productReadModelService;

    public LocalDbOfficialWarehouseStatisticsService(OfficialWarehouseStatisticsMapper mapper) {
        this(mapper, null);
    }

    @Autowired
    public LocalDbOfficialWarehouseStatisticsService(
            OfficialWarehouseStatisticsMapper mapper,
            ProductReadModelService productReadModelService
    ) {
        this.mapper = mapper;
        this.productReadModelService = productReadModelService;
    }

    public StockStatisticsView stockStatistics(BusinessAccessContext access, StockStatisticsQuery query) {
        StockStatisticsQuery safeQuery = query == null ? new StockStatisticsQuery() : query;
        Long ownerUserId = requireOwnerUserId(access, safeQuery.storeCode);
        Collection<String> storeCodes = storeCodes(access, safeQuery.storeCode);
        ProductListDatasetView productDataset = loadProductDataset(ownerUserId, safeQuery);
        StockStatisticsQuery stockSourceQuery = productDataset == null
                ? safeQuery
                : stockSourceQueryForProductPerspective(safeQuery);
        List<InventorySnapshotSourceRecord> snapshots = mapper.listInventorySnapshotSources(
                ownerUserId,
                storeCodes,
                trimToNull(stockSourceQuery.storeCode),
                normalizeSite(stockSourceQuery.siteCode),
                keywordLike(stockSourceQuery.keyword),
                trimToNull(stockSourceQuery.warehouseCode),
                normalizeBucket(stockSourceQuery.stockBucket)
        );
        if (!snapshots.isEmpty()) {
            List<InventorySnapshotSourceRecord> rows = new ArrayList<>(snapshots);
            rows.addAll(mapper.listUnmatchedInventorySnapshotSources(
                    ownerUserId,
                    storeCodes,
                    trimToNull(stockSourceQuery.storeCode),
                    normalizeSite(stockSourceQuery.siteCode),
                    keywordLike(stockSourceQuery.keyword),
                    trimToNull(stockSourceQuery.warehouseCode),
                    normalizeBucket(stockSourceQuery.stockBucket)
            ));
            StockStatisticsView stockOnlyView =
                    stockStatisticsFromInventorySnapshots(ownerUserId, storeCodes, stockSourceQuery, rows);
            applyInventoryWarehouseStocks(stockOnlyView.rows, mapper.listInventorySnapshotWarehouseStocks(
                    ownerUserId,
                    storeCodes,
                    trimToNull(stockSourceQuery.storeCode),
                    normalizeSite(stockSourceQuery.siteCode),
                    trimToNull(stockSourceQuery.warehouseCode)
            ));
            return productDataset == null
                    ? stockOnlyView
                    : stockStatisticsFromProductDataset(productDataset, stockOnlyView.rows, safeQuery);
        }

        List<StockSourceRecord> sources = mapper.listStockSources(
                ownerUserId,
                storeCodes,
                trimToNull(stockSourceQuery.storeCode),
                normalizeSite(stockSourceQuery.siteCode),
                keywordLike(stockSourceQuery.keyword),
                trimToNull(stockSourceQuery.warehouseCode),
                normalizeBucket(stockSourceQuery.stockBucket)
        );
        List<Long> productSiteOfferIds = sources.stream()
                .map(source -> source.productSiteOfferId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<StockCorrectionEventRecord> corrections = productSiteOfferIds.isEmpty()
                ? List.of()
                : mapper.listStockCorrections(
                        ownerUserId,
                        storeCodes,
                        trimToNull(stockSourceQuery.storeCode),
                        normalizeSite(stockSourceQuery.siteCode),
                        productSiteOfferIds
                );

        StockStatisticsView view = new StockStatisticsView();
        for (StockSourceRecord source : sources) {
            StockStatisticsRowView row = toStockRow(source, correctionsForSource(source, corrections));
            if (matchesStockBucket(row, stockSourceQuery.stockBucket)) {
                view.rows.add(row);
                accumulate(view, row);
            }
        }
        return productDataset == null ? view : stockStatisticsFromProductDataset(productDataset, view.rows, safeQuery);
    }

    private StockStatisticsView stockStatisticsFromInventorySnapshots(
            Long ownerUserId,
            Collection<String> storeCodes,
            StockStatisticsQuery safeQuery,
            List<InventorySnapshotSourceRecord> snapshots
    ) {
        List<Long> productSiteOfferIds = snapshots.stream()
                .map(source -> source.productSiteOfferId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        List<StockCorrectionEventRecord> corrections = productSiteOfferIds.isEmpty()
                ? List.of()
                : mapper.listStockCorrections(
                        ownerUserId,
                        storeCodes,
                        trimToNull(safeQuery.storeCode),
                        normalizeSite(safeQuery.siteCode),
                        productSiteOfferIds
                );

        StockStatisticsView view = new StockStatisticsView();
        for (InventorySnapshotSourceRecord source : snapshots) {
            StockStatisticsRowView row = toStockRow(source, correctionsForSource(source, corrections));
            if (matchesStockBucket(row, safeQuery.stockBucket)) {
                view.rows.add(row);
                accumulate(view, row);
            }
        }
        return view;
    }

    private ProductListDatasetView loadProductDataset(Long ownerUserId, StockStatisticsQuery query) {
        if (productReadModelService == null || query == null || !StringUtils.hasText(query.storeCode)) {
            return null;
        }
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(ownerUserId);
        command.setStoreCode(query.storeCode);
        try {
            ProductListDatasetView dataset = productReadModelService.loadListDataset(command);
            if (dataset == null || dataset.getItems() == null || dataset.getItems().isEmpty()) {
                return null;
            }
            return dataset;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void applyInventoryWarehouseStocks(
            List<StockStatisticsRowView> rows,
            List<InventoryWarehouseStockRecord> warehouseStocks
    ) {
        if (rows == null || rows.isEmpty() || warehouseStocks == null || warehouseStocks.isEmpty()) {
            return;
        }
        for (StockStatisticsRowView row : rows) {
            List<StockWarehouseStockView> matches = new ArrayList<>();
            for (InventoryWarehouseStockRecord warehouseStock : warehouseStocks) {
                if (matchesWarehouseStock(row, warehouseStock)) {
                    matches.add(toWarehouseStockView(warehouseStock));
                }
            }
            if (!matches.isEmpty()) {
                row.warehouseStocks = aggregateWarehouseStocks(matches);
            }
        }
    }

    private boolean matchesWarehouseStock(StockStatisticsRowView row, InventoryWarehouseStockRecord warehouseStock) {
        Long rowProductSiteOfferId = parseLong(row.productSiteOfferId, "商品站点库存 ID");
        if (rowProductSiteOfferId != null && rowProductSiteOfferId.equals(warehouseStock.productSiteOfferId)) {
            return true;
        }
        Set<String> rowKeys = lookupKeys(row.skuParent, row.partnerSku, row.pskuCode, row.noonSku);
        Set<String> warehouseKeys = lookupKeys(warehouseStock.partnerSku, warehouseStock.pskuCode, warehouseStock.noonSku);
        warehouseKeys.retainAll(rowKeys);
        return !warehouseKeys.isEmpty();
    }

    private StockWarehouseStockView toWarehouseStockView(InventoryWarehouseStockRecord record) {
        StockWarehouseStockView view = new StockWarehouseStockView();
        view.warehouseCode = firstNonBlank(record.warehouseCode, FALLBACK_WAREHOUSE_CODE);
        view.currentStock = nonNegative(record.currentStock);
        view.effectiveStock = nonNegative(record.effectiveStock);
        view.returnStock = nonNegative(record.returnStock);
        view.failedOrExceptionStock = nonNegative(record.failedOrExceptionStock);
        view.pendingConfirmationStock = nonNegative(record.pendingConfirmationStock);
        return view;
    }

    private StockWarehouseStockView toWarehouseStockView(StockStatisticsRowView row) {
        StockWarehouseStockView view = new StockWarehouseStockView();
        view.warehouseCode = firstNonBlank(row.warehouseCode, FALLBACK_WAREHOUSE_CODE);
        view.currentStock = row.currentStock;
        view.effectiveStock = row.effectiveStock;
        view.returnStock = row.returnStock;
        view.failedOrExceptionStock = row.failedOrExceptionStock;
        view.pendingConfirmationStock = row.pendingConfirmationStock;
        return view;
    }

    private List<StockWarehouseStockView> aggregateWarehouseStocks(List<StockWarehouseStockView> warehouseStocks) {
        Map<String, StockWarehouseStockView> byWarehouse = new LinkedHashMap<>();
        for (StockWarehouseStockView source : warehouseStocks) {
            String warehouseCode = firstNonBlank(source.warehouseCode, FALLBACK_WAREHOUSE_CODE);
            StockWarehouseStockView target = byWarehouse.computeIfAbsent(warehouseCode, ignored -> {
                StockWarehouseStockView created = new StockWarehouseStockView();
                created.warehouseCode = warehouseCode;
                return created;
            });
            target.currentStock += source.currentStock;
            target.effectiveStock += source.effectiveStock;
            target.returnStock += source.returnStock;
            target.failedOrExceptionStock += source.failedOrExceptionStock;
            target.pendingConfirmationStock += source.pendingConfirmationStock;
        }
        return new ArrayList<>(byWarehouse.values());
    }

    private StockStatisticsQuery stockSourceQueryForProductPerspective(StockStatisticsQuery query) {
        StockStatisticsQuery stockSourceQuery = new StockStatisticsQuery();
        stockSourceQuery.storeCode = query.storeCode;
        stockSourceQuery.siteCode = query.siteCode;
        stockSourceQuery.warehouseCode = query.warehouseCode;
        return stockSourceQuery;
    }

    private StockStatisticsView stockStatisticsFromProductDataset(
            ProductListDatasetView productDataset,
            List<StockStatisticsRowView> stockRows,
            StockStatisticsQuery query
    ) {
        Map<String, List<StockStatisticsRowView>> stockRowsByKey = indexStockRows(stockRows);
        Set<StockStatisticsRowView> matchedStockRows = new HashSet<>();
        StockStatisticsView view = new StockStatisticsView();

        for (LocalDbStoreInitializationService.StoreInitializationProductListItemView product : productDataset.getItems()) {
            Set<StockStatisticsRowView> matches = matchingStockRows(product, stockRowsByKey);
            matchedStockRows.addAll(matches);
            StockStatisticsRowView row = toProductPerspectiveRow(productDataset, product, matches, query);
            if (matchesProductPerspectiveFilters(row, query)) {
                view.rows.add(row);
                accumulate(view, row);
            }
        }

        for (StockStatisticsRowView stockRow : stockRows) {
            if (matchedStockRows.contains(stockRow)) {
                continue;
            }
            if (matchesProductPerspectiveFilters(stockRow, query)) {
                view.rows.add(stockRow);
                accumulate(view, stockRow);
            }
        }
        return view;
    }

    private Map<String, List<StockStatisticsRowView>> indexStockRows(List<StockStatisticsRowView> stockRows) {
        Map<String, List<StockStatisticsRowView>> stockRowsByKey = new HashMap<>();
        for (StockStatisticsRowView stockRow : stockRows) {
            for (String key : lookupKeys(stockRow.skuParent, stockRow.partnerSku, stockRow.pskuCode, stockRow.noonSku)) {
                stockRowsByKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(stockRow);
            }
        }
        return stockRowsByKey;
    }

    private Set<StockStatisticsRowView> matchingStockRows(
            LocalDbStoreInitializationService.StoreInitializationProductListItemView product,
            Map<String, List<StockStatisticsRowView>> stockRowsByKey
    ) {
        Set<StockStatisticsRowView> matches = new LinkedHashSet<>();
        for (String key : lookupKeys(product.getSkuParent(), product.getPartnerSku(), product.getPskuCode(), product.getOfferCode())) {
            matches.addAll(stockRowsByKey.getOrDefault(key, List.of()));
        }
        return matches;
    }

    private StockStatisticsRowView toProductPerspectiveRow(
            ProductListDatasetView productDataset,
            LocalDbStoreInitializationService.StoreInitializationProductListItemView product,
            Set<StockStatisticsRowView> matchedRows,
            StockStatisticsQuery query
    ) {
        StockStatisticsRowView firstMatched = matchedRows.stream().findFirst().orElse(null);
        StockStatisticsRowView row = new StockStatisticsRowView();
        row.productMasterId = firstMatched == null ? null : firstMatched.productMasterId;
        row.productVariantId = firstMatched == null ? null : firstMatched.productVariantId;
        row.productSiteOfferId = firstMatched == null ? null : firstMatched.productSiteOfferId;
        row.logicalStoreId = firstMatched == null ? null : firstMatched.logicalStoreId;
        row.storeCode = firstNonBlankValue(
                product.getReferenceStoreCode(),
                productDataset.getStoreCode(),
                query.storeCode,
                firstMatched == null ? null : firstMatched.storeCode
        );
        row.storeName = firstMatched == null ? null : firstMatched.storeName;
        row.siteCode = firstNonBlankValue(query.siteCode, firstMatched == null ? null : firstMatched.siteCode);
        row.projectCode = firstNonBlankValue(productDataset.getProjectCode(), firstMatched == null ? null : firstMatched.projectCode);
        row.partnerId = firstMatched == null ? null : firstMatched.partnerId;
        row.skuParent = firstNonBlankValue(product.getSkuParent(), firstMatched == null ? null : firstMatched.skuParent);
        row.partnerSku = firstNonBlankValue(product.getPartnerSku(), firstMatched == null ? null : firstMatched.partnerSku);
        row.pskuCode = firstNonBlankValue(product.getPskuCode(), firstMatched == null ? null : firstMatched.pskuCode);
        row.noonSku = firstNonBlankValue(product.getOfferCode(), firstMatched == null ? null : firstMatched.noonSku);
        row.titleCn = trimToNull(product.getTitleCn());
        row.titleEn = firstNonBlankValue(product.getTitle(), firstMatched == null ? null : firstMatched.title);
        row.title = firstNonBlankValue(
                row.titleCn,
                row.titleEn,
                product.getTitleCn(),
                product.getTitle(),
                firstMatched == null ? null : firstMatched.title
        );
        row.brand = firstNonBlankValue(product.getBrand(), firstMatched == null ? null : firstMatched.brand);
        row.imageUrl = ProductImageUrlSupport.firstNonBlankNormalized(product.getImageUrl(), firstMatched == null ? null : firstMatched.imageUrl);
        row.warehouseCode = firstNonBlankValue(query.warehouseCode, firstMatched == null ? null : firstMatched.warehouseCode);
        if (matchedRows.isEmpty()) {
            row.sourceType = "PRODUCT_MASTER_LIST";
            row.inventoryConfidence = "NO_INVENTORY_RECORD";
            row.lastSyncedAt = product.getLastSyncedAt();
            return row;
        }

        for (StockStatisticsRowView matchedRow : matchedRows) {
            row.currentStock += matchedRow.currentStock;
            row.effectiveStock += matchedRow.effectiveStock;
            row.returnStock += matchedRow.returnStock;
            row.failedOrExceptionStock += matchedRow.failedOrExceptionStock;
            row.pendingConfirmationStock += matchedRow.pendingConfirmationStock;
            if (matchedRow.warehouseStocks == null || matchedRow.warehouseStocks.isEmpty()) {
                row.warehouseStocks.add(toWarehouseStockView(matchedRow));
            } else {
                row.warehouseStocks.addAll(matchedRow.warehouseStocks);
            }
            mergeAnomalyFlags(row, matchedRow);
            row.lastSyncedAt = latestTimestamp(row.lastSyncedAt, matchedRow.lastSyncedAt);
        }
        row.warehouseStocks = aggregateWarehouseStocks(row.warehouseStocks);
        row.sourceType = aggregateSourceType(matchedRows);
        row.inventoryConfidence = aggregateInventoryConfidence(matchedRows);
        return row;
    }

    private void mergeAnomalyFlags(StockStatisticsRowView target, StockStatisticsRowView source) {
        for (String flag : source.anomalyFlags) {
            if (!target.anomalyFlags.contains(flag)) {
                target.anomalyFlags.add(flag);
            }
        }
    }

    private String aggregateSourceType(Set<StockStatisticsRowView> matchedRows) {
        if (matchedRows.stream().anyMatch(row -> "MANUAL_CORRECTION".equals(normalize(row.sourceType)))) {
            return "MANUAL_CORRECTION";
        }
        return matchedRows.stream()
                .map(row -> trimToNull(row.sourceType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("PRODUCT_MASTER_LIST");
    }

    private String aggregateInventoryConfidence(Set<StockStatisticsRowView> matchedRows) {
        if (matchedRows.stream().anyMatch(row -> "CLASSIFIED_INVENTORY".equals(normalize(row.inventoryConfidence)))) {
            return "CLASSIFIED_INVENTORY";
        }
        if (matchedRows.stream().anyMatch(row -> "PENDING_CONFIRMATION_ONLY".equals(normalize(row.inventoryConfidence)))) {
            return "PENDING_CONFIRMATION_ONLY";
        }
        if (matchedRows.stream().anyMatch(row -> "PRODUCT_UNMATCHED".equals(normalize(row.inventoryConfidence)))) {
            return "PRODUCT_UNMATCHED";
        }
        return matchedRows.stream()
                .map(row -> trimToNull(row.inventoryConfidence))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("NO_INVENTORY_RECORD");
    }

    private boolean matchesProductPerspectiveFilters(StockStatisticsRowView row, StockStatisticsQuery query) {
        return matchesKeyword(row, query == null ? null : query.keyword)
                && matchesStockBucket(row, query == null ? null : query.stockBucket);
    }

    private boolean matchesKeyword(StockStatisticsRowView row, String keyword) {
        String safeKeyword = trimToNull(keyword);
        if (safeKeyword == null) {
            return true;
        }
        String needle = safeKeyword.toLowerCase(Locale.ROOT);
        return containsKeyword(row.skuParent, needle)
                || containsKeyword(row.partnerSku, needle)
                || containsKeyword(row.pskuCode, needle)
                || containsKeyword(row.noonSku, needle)
                || containsKeyword(row.titleCn, needle)
                || containsKeyword(row.titleEn, needle)
                || containsKeyword(row.title, needle)
                || containsKeyword(row.brand, needle);
    }

    private boolean containsKeyword(String value, String needle) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private Set<String> lookupKeys(String... values) {
        Set<String> keys = new LinkedHashSet<>();
        for (String value : values) {
            String key = normalizeLookupKey(value);
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private String normalizeLookupKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String latestTimestamp(String current, String candidate) {
        String safeCandidate = trimToNull(candidate);
        if (safeCandidate == null) {
            return current;
        }
        String safeCurrent = trimToNull(current);
        if (safeCurrent == null || safeCandidate.compareTo(safeCurrent) > 0) {
            return safeCandidate;
        }
        return safeCurrent;
    }

    public InboundStatisticsView inboundStatistics(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String keyword,
            String asn,
            String warehouseCode,
            String receiptStatus
    ) {
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Collection<String> storeCodes = storeCodes(access, storeCode);
        List<InboundStageRecord> rows = mapper.listInboundStageRows(
                ownerUserId,
                storeCodes,
                trimToNull(storeCode),
                normalizeSite(siteCode),
                keywordLike(keyword),
                trimToNull(asn),
                trimToNull(warehouseCode),
                trimToNull(receiptStatus)
        );

        InboundStatisticsView view = new InboundStatisticsView();
        view.summary.lineReceiptReportConnected = false;
        for (InboundStageRecord row : rows) {
            InboundStageRowView item = toInboundRow(row);
            view.rows.add(item);
            view.summary.asnCount += 1;
            view.summary.totalQuantity += item.totalQuantity;
            if ("GRN_COMPLETED".equals(item.inboundStage)) {
                view.summary.grnCompletedAsnCount += 1;
            }
            if ("RECEIVING".equals(item.inboundStage)) {
                view.summary.receivingAsnCount += 1;
            }
            if ("FAILED".equals(item.inboundStage)) {
                view.summary.failedAsnCount += 1;
            }
            String appointmentStatus = normalize(row.appointmentStatus);
            if ("SCHEDULED".equals(appointmentStatus)) {
                view.summary.appointmentScheduledCount += 1;
            } else if ("PENDING".equals(appointmentStatus) || "RUNNING".equals(appointmentStatus)) {
                view.summary.appointmentPendingCount += 1;
            } else if ("FAILED".equals(appointmentStatus)) {
                view.summary.appointmentFailedCount += 1;
            }
        }
        InboundReceiptSummaryRecord receiptSummary = mapper.selectInboundReceiptSummary(
                ownerUserId,
                storeCodes,
                trimToNull(storeCode),
                normalizeSite(siteCode),
                keywordLike(keyword),
                trimToNull(asn),
                trimToNull(warehouseCode),
                trimToNull(receiptStatus),
                OfficialWarehouseFbnReceivedReportImportService.REPORT_TYPE
        );
        applyInboundReceiptSummary(view, receiptSummary);
        ScheduledDeliveryAccuracySummaryRecord scheduledDeliveryAccuracySummary =
                mapper.selectScheduledDeliveryAccuracySummary(
                        ownerUserId,
                        storeCodes,
                        trimToNull(storeCode),
                        normalizeSite(siteCode),
                        keywordLike(keyword),
                        trimToNull(asn),
                        trimToNull(warehouseCode),
                        trimToNull(receiptStatus),
                        OfficialWarehouseScheduledDeliveryAccuracyImportService.REPORT_TYPE
                );
        applyScheduledDeliveryAccuracySummary(view, scheduledDeliveryAccuracySummary);
        return view;
    }

    public ProductInboundHistoryView productInboundHistory(
            BusinessAccessContext access,
            String storeCode,
            String siteCode,
            String productSiteOfferId
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。");
        String normalizedSiteCode = normalizeSite(requireText(siteCode, "请选择站点。"));
        Long parsedProductSiteOfferId = parseLong(productSiteOfferId, "商品站点 Offer ID");
        if (parsedProductSiteOfferId == null) {
            throw new IllegalArgumentException("请选择商品。");
        }
        Long ownerUserId = requireOwnerUserId(access, normalizedStoreCode);
        Collection<String> storeCodes = storeCodes(access, normalizedStoreCode);
        List<InboundReceiptHistoryRecord> records = mapper.listProductInboundReceiptHistory(
                ownerUserId,
                storeCodes,
                normalizedStoreCode,
                normalizedSiteCode,
                parsedProductSiteOfferId,
                200
        );
        ProductInboundHistoryView view = new ProductInboundHistoryView();
        for (InboundReceiptHistoryRecord record : records) {
            ProductInboundReceiptRowView row = toProductInboundReceiptRow(record);
            view.rows.add(row);
            view.summary.receiptLineCount += 1;
            view.summary.expectedQuantity += row.qtyExpected;
            view.summary.receivedQuantity += row.receivedQty;
            view.summary.qcFailedQuantity += row.qcFailedQty;
            view.summary.unidentifiedQuantity += row.unidentifiedQty;
            if (!"NORMAL".equals(normalize(row.receiptStatus)) || !"MATCHED".equals(normalize(row.matchStatus))) {
                view.summary.exceptionLineCount += 1;
            }
        }
        List<ProductStockSourceCandidateRecord> sourceCandidates = mapper.listProductStockSourceCandidates(
                ownerUserId,
                storeCodes,
                normalizedStoreCode,
                normalizedSiteCode,
                parsedProductSiteOfferId,
                50
        );
        for (ProductStockSourceCandidateRecord sourceCandidate : sourceCandidates) {
            view.sourceCandidates.add(toProductStockSourceCandidate(sourceCandidate));
        }
        return view;
    }

    public StockStatisticsRowView correctStock(BusinessAccessContext access, StockCorrectionCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少库存订正参数。");
        }
        Long ownerUserId = requireOwnerUserId(access, command.storeCode);
        StockStatisticsQuery query = new StockStatisticsQuery();
        query.storeCode = command.storeCode;
        query.siteCode = command.siteCode;
        query.warehouseCode = command.warehouseCode;
        StockStatisticsView before = stockStatistics(access, query);
        Long productSiteOfferId = parseLong(command.productSiteOfferId, "商品站点库存 ID");
        StockStatisticsRowView target = before.rows.stream()
                .filter(row -> productSiteOfferId.equals(parseLong(row.productSiteOfferId, "商品站点库存 ID")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("库存订正目标不存在或当前账号不可访问。"));
        int quantity = requirePositiveQuantity(command.quantity);
        String toBucket = normalizeRequiredBucket(command.toStockBucket);
        if (SELLABLE.equals(toBucket) && !StringUtils.hasText(command.reasonText) && !StringUtils.hasText(command.reasonCode)) {
            throw new IllegalArgumentException("订正为有效在仓必须填写原因。");
        }
        if (quantity > target.pendingConfirmationStock && !isReverseCorrection(command)) {
            throw new IllegalArgumentException("订正数量不能超过待确认库存。");
        }

        StockCorrectionInsertRecord insert = new StockCorrectionInsertRecord();
        insert.id = mapper.nextStockCorrectionId();
        insert.ownerUserId = ownerUserId;
        insert.logicalStoreId = parseLong(target.logicalStoreId, "逻辑店铺 ID");
        insert.storeCode = trimToNull(command.storeCode);
        insert.siteCode = normalizeSite(command.siteCode);
        insert.projectCode = target.projectCode;
        insert.partnerId = target.partnerId;
        insert.correctionType = isReverseCorrection(command) ? "REVERSE_CORRECTION" : "CLASSIFY_STOCK";
        insert.targetRefType = firstNonBlank(command.targetRefType, "PRODUCT_SITE_OFFER_FALLBACK");
        insert.targetRefId = parseLong(command.targetRefId, "订正目标 ID");
        insert.productMasterId = parseLong(target.productMasterId, "商品主档 ID");
        insert.productVariantId = parseLong(target.productVariantId, "商品变体 ID");
        insert.productSiteOfferId = productSiteOfferId;
        insert.partnerSku = target.partnerSku;
        insert.pskuCode = target.pskuCode;
        insert.noonSku = target.noonSku;
        insert.warehouseCode = firstNonBlank(command.warehouseCode, FALLBACK_WAREHOUSE_CODE);
        insert.fromStockBucket = firstNonBlank(command.fromStockBucket, PENDING_CONFIRMATION);
        insert.toStockBucket = toBucket;
        insert.quantity = quantity;
        insert.reasonCode = trimToNull(command.reasonCode);
        insert.reasonText = trimToNull(command.reasonText);
        insert.operatorUserId = access.getSessionUserId();
        mapper.insertStockCorrection(insert);

        StockStatisticsView after = stockStatistics(access, query);
        return after.rows.stream()
                .filter(row -> productSiteOfferId.equals(parseLong(row.productSiteOfferId, "商品站点库存 ID")))
                .findFirst()
                .orElse(target);
    }

    @Transactional
    public ScheduledDeliveryAccuracyRematchResultView rematchScheduledDeliveryAccuracy(
            BusinessAccessContext access,
            String importId,
            ScheduledDeliveryAccuracyRematchCommand command
    ) {
        if (command == null) {
            throw new IllegalArgumentException("缺少预约到货准确率重匹配参数。");
        }
        String storeCode = requireText(command.storeCode, "请选择要重匹配的店铺。");
        String siteCode = normalizeSite(requireText(command.siteCode, "请选择要重匹配的站点。"));
        Long parsedImportId = parseLong(requireText(importId, "缺少报表导入批次 ID。"), "报表导入批次 ID");
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Long operatorUserId = access.getSessionUserId() == null ? ownerUserId : access.getSessionUserId();

        DeliveryAccuracyRematchSummaryRecord before = mapper.selectDeliveryAccuracyRematchSummary(
                ownerUserId,
                storeCode,
                siteCode,
                parsedImportId
        );
        if (before == null || nonNegative(before.totalRows) <= 0) {
            throw new IllegalArgumentException("预约到货准确率导入批次不存在或当前账号不可访问。");
        }

        int rematchedRows = mapper.rematchDeliveryAccuracyAsns(
                ownerUserId,
                storeCode,
                siteCode,
                parsedImportId,
                operatorUserId
        );
        DeliveryAccuracyRematchSummaryRecord after = mapper.selectDeliveryAccuracyRematchSummary(
                ownerUserId,
                storeCode,
                siteCode,
                parsedImportId
        );
        if (after == null) {
            after = before;
        }

        ScheduledDeliveryAccuracyRematchResultView view = new ScheduledDeliveryAccuracyRematchResultView();
        view.importId = String.valueOf(parsedImportId);
        view.storeCode = storeCode;
        view.siteCode = siteCode;
        view.totalRows = nonNegative(after.totalRows);
        view.matchedRowsBefore = nonNegative(before.matchedRows);
        view.noLocalAsnRowsBefore = nonNegative(before.noLocalAsnRows);
        view.rematchedRows = Math.max(0, rematchedRows);
        view.matchedRowsAfter = nonNegative(after.matchedRows);
        view.noLocalAsnRowsAfter = nonNegative(after.noLocalAsnRows);
        return view;
    }

    private StockStatisticsRowView toStockRow(
            StockSourceRecord source,
            List<StockCorrectionEventRecord> corrections
    ) {
        StockStatisticsRowView row = new StockStatisticsRowView();
        row.productMasterId = stringValue(source.productMasterId);
        row.productVariantId = stringValue(source.productVariantId);
        row.productSiteOfferId = stringValue(source.productSiteOfferId);
        row.logicalStoreId = stringValue(source.logicalStoreId);
        row.storeCode = source.storeCode;
        row.storeName = source.storeName;
        row.siteCode = source.siteCode;
        row.projectCode = source.projectCode;
        row.partnerId = source.partnerId;
        row.skuParent = source.skuParent;
        row.partnerSku = source.partnerSku;
        row.pskuCode = source.pskuCode;
        row.noonSku = source.noonSku;
        row.title = source.title;
        row.titleEn = source.title;
        row.brand = source.brand;
        row.imageUrl = ProductImageUrlSupport.normalize(source.imageUrl);
        row.warehouseCode = firstNonBlank(source.warehouseCode, FALLBACK_WAREHOUSE_CODE);
        row.currentStock = nonNegative(source.fbnStock) + nonNegative(source.supermallStock);
        row.inventoryConfidence = "PENDING_CONFIRMATION_ONLY";
        row.lastSyncedAt = source.lastSyncedAt;
        applyBuckets(
                row,
                0,
                0,
                0,
                row.currentStock,
                corrections,
                "PRODUCT_SITE_OFFER_FALLBACK"
        );
        row.warehouseStocks.add(toWarehouseStockView(row));
        return row;
    }

    private StockStatisticsRowView toStockRow(
            InventorySnapshotSourceRecord source,
            List<StockCorrectionEventRecord> corrections
    ) {
        StockStatisticsRowView row = new StockStatisticsRowView();
        row.productMasterId = stringValue(source.productMasterId);
        row.productVariantId = stringValue(source.productVariantId);
        row.productSiteOfferId = stringValue(source.productSiteOfferId);
        row.logicalStoreId = stringValue(source.logicalStoreId);
        row.storeCode = source.storeCode;
        row.storeName = source.storeName;
        row.siteCode = source.siteCode;
        row.projectCode = source.projectCode;
        row.partnerId = source.partnerId;
        row.skuParent = source.skuParent;
        row.partnerSku = source.partnerSku;
        row.pskuCode = source.pskuCode;
        row.noonSku = source.noonSku;
        row.title = source.title;
        row.titleEn = source.title;
        row.brand = source.brand;
        row.imageUrl = ProductImageUrlSupport.normalize(source.imageUrl);
        row.warehouseCode = trimToNull(source.warehouseCode);
        long baseEffective = nonNegative(source.effectiveStock);
        long baseReturn = nonNegative(source.returnStock);
        long baseFailed = nonNegative(source.failedOrExceptionStock);
        long basePending = nonNegative(source.pendingConfirmationStock);
        row.currentStock = source.currentStock == null
                ? baseEffective + baseReturn + baseFailed + basePending
                : Math.max(0, source.currentStock);
        row.inventoryConfidence = firstNonBlank(source.inventoryConfidence, "CLASSIFIED_INVENTORY");
        row.lastSyncedAt = source.lastSyncedAt;
        applyBuckets(row, baseEffective, baseReturn, baseFailed, basePending, corrections, "FBN_INVENTORY_API");
        if ("PRODUCT_UNMATCHED".equals(normalize(row.inventoryConfidence))) {
            row.anomalyFlags.add("PRODUCT_UNMATCHED");
        }
        row.warehouseStocks.add(toWarehouseStockView(row));
        return row;
    }

    private void applyBuckets(
            StockStatisticsRowView row,
            long baseEffective,
            long baseReturn,
            long baseFailed,
            long basePending,
            List<StockCorrectionEventRecord> corrections,
            String baseSourceType
    ) {
        Map<String, Long> rawBuckets = aggregateCorrections(corrections);
        long rawEffective = rawBuckets.getOrDefault(SELLABLE, 0L);
        long rawReturn = rawBuckets.getOrDefault(RETURNED, 0L);
        long rawFailed = FAILED_OR_EXCEPTION_BUCKETS.stream()
                .mapToLong(bucket -> rawBuckets.getOrDefault(bucket, 0L))
                .sum();
        long rawKnown = rawEffective + rawReturn + rawFailed;
        long baseKnown = baseEffective + baseReturn + baseFailed + basePending;
        if (baseKnown > row.currentStock || rawKnown > basePending) {
            row.anomalyFlags.add("BUCKET_QUANTITY_EXCEEDS_CURRENT_STOCK");
        }

        long remainingPending = Math.max(0, Math.min(basePending, row.currentStock - baseEffective - baseReturn - baseFailed));
        long correctedEffective = Math.min(rawEffective, remainingPending);
        remainingPending -= correctedEffective;
        long correctedReturn = Math.min(rawReturn, remainingPending);
        remainingPending -= correctedReturn;
        long correctedFailed = Math.min(rawFailed, remainingPending);
        remainingPending -= correctedFailed;

        row.effectiveStock = baseEffective + correctedEffective;
        row.returnStock = baseReturn + correctedReturn;
        row.failedOrExceptionStock = baseFailed + correctedFailed;
        row.pendingConfirmationStock = Math.max(0, remainingPending);
        row.sourceType = rawKnown > 0 ? "MANUAL_CORRECTION" : baseSourceType;
    }

    private Map<String, Long> aggregateCorrections(List<StockCorrectionEventRecord> corrections) {
        Set<Long> reversed = corrections.stream()
                .filter(record -> "REVERSE_CORRECTION".equals(normalize(record.correctionType)))
                .map(record -> record.targetRefId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, Long> buckets = new HashMap<>();
        for (StockCorrectionEventRecord record : corrections) {
            if (!"CLASSIFY_STOCK".equals(normalize(record.correctionType)) || reversed.contains(record.id)) {
                continue;
            }
            String bucket = normalize(record.toStockBucket);
            if (!StringUtils.hasText(bucket)) {
                continue;
            }
            buckets.merge(bucket, (long) nonNegative(record.quantity), Long::sum);
        }
        return buckets;
    }

    private List<StockCorrectionEventRecord> correctionsForSource(
            StockSourceRecord source,
            List<StockCorrectionEventRecord> corrections
    ) {
        Long productSiteOfferId = source.productSiteOfferId;
        if (productSiteOfferId == null) {
            return List.of();
        }
        return corrections.stream()
                .filter(record -> productSiteOfferId.equals(record.productSiteOfferId))
                .collect(Collectors.toList());
    }

    private List<StockCorrectionEventRecord> correctionsForSource(
            InventorySnapshotSourceRecord source,
            List<StockCorrectionEventRecord> corrections
    ) {
        Long productSiteOfferId = source.productSiteOfferId;
        if (productSiteOfferId == null) {
            return List.of();
        }
        return corrections.stream()
                .filter(record -> productSiteOfferId.equals(record.productSiteOfferId))
                .collect(Collectors.toList());
    }

    private InboundStageRowView toInboundRow(InboundStageRecord row) {
        InboundStageRowView view = new InboundStageRowView();
        view.asnId = stringValue(row.asnId);
        view.localAsnNo = row.localAsnNo;
        view.noonAsnNr = row.noonAsnNr;
        view.storeCode = row.storeCode;
        view.siteCode = row.siteCode;
        view.localStatus = row.status;
        view.noonAsnStatus = row.noonAsnStatus;
        view.inboundStage = inboundStage(row.status, row.noonAsnStatus);
        view.appointmentStatus = row.appointmentStatus;
        view.totalQuantity = nonNegative(row.noonTotalQty == null ? row.totalQuantity : row.noonTotalQty);
        view.selectedWarehouseCode = row.selectedWarehouseCode;
        view.selectedWarehousePartnerCode = row.selectedWarehousePartnerCode;
        return view;
    }

    private ProductInboundReceiptRowView toProductInboundReceiptRow(InboundReceiptHistoryRecord record) {
        ProductInboundReceiptRowView view = new ProductInboundReceiptRowView();
        view.importId = stringValue(record.importId);
        view.reportRowId = stringValue(record.reportRowId);
        view.noonAsnNr = record.noonAsnNr;
        view.partnerSku = record.partnerSku;
        view.pskuCode = record.pskuCode;
        view.noonSku = record.noonSku;
        view.pbarcodeCanonical = record.pbarcodeCanonical;
        view.partnerWarehouse = record.partnerWarehouse;
        view.noonWarehouse = record.noonWarehouse;
        view.qtyExpected = nonNegative(record.qtyExpected);
        view.receivedQty = nonNegative(record.receivedQty);
        view.qcFailedQty = nonNegative(record.qcFailedQty);
        view.unidentifiedQty = nonNegative(record.unidentifiedQty);
        view.qcFailedReason = record.qcFailedReason;
        view.receiptStatus = firstNonBlank(record.receiptStatus, "UNKNOWN");
        view.matchStatus = firstNonBlank(record.matchStatus, "UNKNOWN");
        view.asnCreatedAt = record.asnCreatedAt;
        view.asnScheduleDate = record.asnScheduleDate;
        view.asnCompletedAt = record.asnCompletedAt;
        view.importedAt = record.importedAt;
        return view;
    }

    private ProductStockSourceCandidateView toProductStockSourceCandidate(ProductStockSourceCandidateRecord record) {
        ProductStockSourceCandidateView view = new ProductStockSourceCandidateView();
        view.logisticsBatchId = stringValue(record.logisticsBatchId);
        view.logisticsBatchNo = record.logisticsBatchNo;
        view.logisticsStatus = record.logisticsStatus;
        view.purchaseOrderId = stringValue(record.purchaseOrderId);
        view.purchaseOrderNo = record.purchaseOrderNo;
        view.sourceStoreCode = record.sourceStoreCode;
        view.siteCode = record.siteCode;
        view.partnerSku = record.partnerSku;
        view.skuParent = record.skuParent;
        view.quantity = nonNegative(record.quantity);
        view.latestAt = record.latestAt;
        view.relationBasis = "同商品 / 同站点 / 物流批次来源";
        return view;
    }

    private void applyInboundReceiptSummary(InboundStatisticsView view, InboundReceiptSummaryRecord record) {
        if (record == null || nonNegative(record.receiptLineCount) <= 0) {
            view.summary.lineReceiptReportConnected = false;
            return;
        }
        view.summary.lineReceiptReportConnected = true;
        view.summary.latestReceiptImportId = stringValue(record.latestImportId);
        view.summary.latestReceiptImportedAt = record.latestImportedAt;
        view.summary.asnCount = Math.max(view.summary.asnCount, nonNegative(record.asnCount));
        view.summary.totalQuantity = nonNegative(record.expectedQuantity);
        view.summary.receiptLineCount = nonNegative(record.receiptLineCount);
        view.summary.expectedQuantity = nonNegative(record.expectedQuantity);
        view.summary.receivedQuantity = nonNegative(record.receivedQuantity);
        view.summary.qcFailedQuantity = nonNegative(record.qcFailedQuantity);
        view.summary.unidentifiedQuantity = nonNegative(record.unidentifiedQuantity);
        view.summary.normalLineCount = nonNegative(record.normalLineCount);
        view.summary.qcFailedLineCount = nonNegative(record.qcFailedLineCount);
        view.summary.shortReceivedLineCount = nonNegative(record.shortReceivedLineCount);
        view.summary.overReceivedLineCount = nonNegative(record.overReceivedLineCount);
        view.summary.unidentifiedLineCount = nonNegative(record.unidentifiedLineCount);
        view.summary.matchedLineCount = nonNegative(record.matchedLineCount);
        view.summary.noLocalAsnLineCount = nonNegative(record.noLocalAsnLineCount);
        view.summary.lineUnmatchedLineCount = nonNegative(record.lineUnmatchedLineCount);
        view.summary.productUnmatchedLineCount = nonNegative(record.productUnmatchedLineCount);
        view.summary.receiptExceptionLineCount = nonNegative(record.receiptExceptionLineCount);
    }

    private void applyScheduledDeliveryAccuracySummary(
            InboundStatisticsView view,
            ScheduledDeliveryAccuracySummaryRecord record
    ) {
        if (record == null || nonNegative(record.asnCount) <= 0) {
            view.summary.scheduledDeliveryAccuracyConnected = false;
            return;
        }
        view.summary.scheduledDeliveryAccuracyConnected = true;
        view.summary.latestScheduledDeliveryAccuracyImportId = stringValue(record.latestImportId);
        view.summary.latestScheduledDeliveryAccuracyImportedAt = record.latestImportedAt;
        view.summary.scheduledDeliveryAccuracyAsnCount = nonNegative(record.asnCount);
        view.summary.scheduledQuantity = nonNegative(record.scheduledQuantity);
        view.summary.grnQuantity = nonNegative(record.grnQuantity);
        view.summary.inboundQuantityVariance = nonNegative(record.inboundQuantityVariance);
        view.summary.putawayCompletedAsnCount = nonNegative(record.putawayCompletedAsnCount);
        view.summary.cancelledAsnCount = nonNegative(record.cancelledAsnCount);
        view.summary.expiredAsnCount = nonNegative(record.expiredAsnCount);
        view.summary.matchedScheduledDeliveryAccuracyAsnCount = nonNegative(record.matchedAsnCount);
        view.summary.noLocalScheduledDeliveryAccuracyAsnCount = nonNegative(record.noLocalAsnCount);
        view.summary.scheduledDeliveryAccuracyExceptionAsnCount = nonNegative(record.exceptionAsnCount);
    }

    private String inboundStage(String localStatus, String noonStatus) {
        String normalizedNoon = normalize(noonStatus);
        if ("GRN_COMPLETED".equals(normalizedNoon)) {
            return "GRN_COMPLETED";
        }
        if ("RECEIVING".equals(normalizedNoon)) {
            return "RECEIVING";
        }
        if ("EXPIRED".equals(normalizedNoon) || "CANCELED".equals(normalizedNoon) || "CANCELLED".equals(normalizedNoon)) {
            return "FAILED";
        }
        if ("FAILED".equals(normalize(localStatus))) {
            return "FAILED";
        }
        return StringUtils.hasText(normalizedNoon) ? normalizedNoon : normalize(localStatus);
    }

    private void accumulate(StockStatisticsView view, StockStatisticsRowView row) {
        view.summary.currentStock += row.currentStock;
        view.summary.effectiveStock += row.effectiveStock;
        view.summary.returnStock += row.returnStock;
        view.summary.failedOrExceptionStock += row.failedOrExceptionStock;
        view.summary.pendingConfirmationStock += row.pendingConfirmationStock;
        view.summary.skuCount += 1;
        if (!row.anomalyFlags.isEmpty()) {
            view.summary.exceptionSkuCount += 1;
        }
    }

    private boolean matchesStockBucket(StockStatisticsRowView row, String stockBucket) {
        String normalized = normalize(stockBucket);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        if (SELLABLE.equals(normalized)) {
            return row.effectiveStock > 0;
        }
        if (RETURNED.equals(normalized)) {
            return row.returnStock > 0;
        }
        if (PENDING_CONFIRMATION.equals(normalized)) {
            return row.pendingConfirmationStock > 0;
        }
        if (FAILED_OR_EXCEPTION_BUCKETS.contains(normalized)) {
            return row.failedOrExceptionStock > 0;
        }
        return true;
    }

    private Collection<String> storeCodes(BusinessAccessContext access, String storeCode) {
        String safeStoreCode = trimToNull(storeCode);
        if (safeStoreCode != null) {
            return List.of(safeStoreCode);
        }
        return access.getStoreCodes();
    }

    private Long requireOwnerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        String safeStoreCode = trimToNull(storeCode);
        Long ownerUserId = safeStoreCode == null ? null : access.resolveOwnerUserIdForStore(safeStoreCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    private boolean isReverseCorrection(StockCorrectionCommand command) {
        return "REVERSE_CORRECTION".equals(normalize(command.targetRefType));
    }

    private int requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("订正数量必须大于 0。");
        }
        return quantity;
    }

    private String normalizeRequiredBucket(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("请选择订正后的库存分类。");
        }
        return normalized;
    }

    private String normalizeBucket(String value) {
        return trimToNull(normalize(value));
    }

    private String normalizeSite(String value) {
        return trimToNull(value == null ? null : value.toUpperCase(Locale.ROOT));
    }

    private String keywordLike(String keyword) {
        String value = trimToNull(keyword);
        return value == null ? null : "%" + value + "%";
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }

    private String firstNonBlankValue(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private long nonNegative(Long value) {
        return value == null ? 0L : Math.max(0L, value);
    }

    private Long parseLong(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(fieldName + "格式不正确。");
        }
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String stringValue(Long value) {
        return value == null ? null : String.valueOf(value);
    }
}
