package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.InventoryPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnInventoryProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.InventorySyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.InventorySyncResultView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(OfficialWarehouseFbnInventoryProvider.class)
public class OfficialWarehouseInventorySyncService {

    private static final String SOURCE_TYPE = "FBN_INVENTORY_API";
    private static final DateTimeFormatter RESULT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OfficialWarehouseStatisticsMapper mapper;
    private final OfficialWarehouseFbnInventoryProvider provider;
    private final ObjectMapper objectMapper;

    public OfficialWarehouseInventorySyncService(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseFbnInventoryProvider provider,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.provider = provider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public InventorySyncResultView sync(BusinessAccessContext access, InventorySyncCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("缺少官方仓库存同步参数。");
        }
        String storeCode = requireText(command.storeCode, "请选择要同步的店铺。");
        String siteCode = requireText(command.siteCode, "请选择要同步的站点。").toUpperCase(Locale.ROOT);
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        InventorySyncScopeRecord scope = mapper.selectInventorySyncScope(ownerUserId, storeCode, siteCode);
        if (scope == null) {
            throw new IllegalArgumentException("无法识别官方仓库存同步店铺范围。");
        }

        int maxPages = maxPages(command.maxPages);
        List<InventoryPage> pages = fetchPages(ownerUserId, storeCode, siteCode, maxPages);
        List<InventoryItem> items = new ArrayList<>();
        for (InventoryPage page : pages) {
            items.addAll(page.items);
        }

        InventorySyncBatchInsertRecord batch = new InventorySyncBatchInsertRecord();
        batch.id = mapper.nextInventorySyncBatchId();
        batch.ownerUserId = ownerUserId;
        batch.logicalStoreId = scope.logicalStoreId;
        batch.storeCode = storeCode;
        batch.siteCode = siteCode;
        batch.projectCode = firstNonBlank(scope.projectCode, deriveProjectCode(scope.partnerId));
        batch.partnerId = firstNonBlank(scope.partnerId, derivePartnerId(batch.projectCode));
        batch.sourceType = SOURCE_TYPE;
        batch.requestSummaryJson = requestSummary(storeCode, siteCode, maxPages);
        batch.responseSummaryJson = responseSummary(pages, items.size());
        batch.status = "IMPORTED";
        batch.totalPages = pages.size();
        batch.totalRows = items.size();
        batch.validRows = items.size();
        batch.errorRows = 0;
        batch.operatorUserId = access.getSessionUserId();
        mapper.insertInventorySyncBatch(batch);
        mapper.deactivateCurrentInventorySnapshotLines(ownerUserId, storeCode, siteCode);

        int insertedRows = 0;
        for (InventoryItem item : items) {
            InventorySnapshotLineInsertRecord line = toLine(scope, batch, item);
            mapper.insertInventorySnapshotLine(line);
            insertedRows += 1;
        }

        InventorySyncResultView result = new InventorySyncResultView();
        result.syncBatchId = String.valueOf(batch.id);
        result.storeCode = storeCode;
        result.siteCode = siteCode;
        result.pageCount = pages.size();
        result.fetchedRows = items.size();
        result.insertedRows = insertedRows;
        result.sourceType = SOURCE_TYPE;
        result.syncedAt = LocalDateTime.now().format(RESULT_TIME_FORMAT);
        return result;
    }

    private List<InventoryPage> fetchPages(Long ownerUserId, String storeCode, String siteCode, int maxPages) {
        List<InventoryPage> pages = new ArrayList<>();
        for (int pageNo = 1; pageNo <= maxPages; pageNo += 1) {
            InventoryPage page = provider.fetchPage(new PullRequest(ownerUserId, storeCode, siteCode), pageNo);
            pages.add(page);
            if (!page.hasNextPage) {
                break;
            }
        }
        return pages;
    }

    private InventorySnapshotLineInsertRecord toLine(
            InventorySyncScopeRecord scope,
            InventorySyncBatchInsertRecord batch,
            InventoryItem item
    ) {
        InventoryLineProductMatchRecord match = matchProduct(batch.ownerUserId, batch.storeCode, batch.siteCode, item);
        InventorySnapshotLineInsertRecord line = new InventorySnapshotLineInsertRecord();
        line.id = mapper.nextInventorySnapshotLineId();
        line.syncBatchId = batch.id;
        line.ownerUserId = batch.ownerUserId;
        line.logicalStoreId = scope.logicalStoreId;
        line.storeCode = batch.storeCode;
        line.siteCode = batch.siteCode;
        line.projectCode = batch.projectCode;
        line.partnerId = batch.partnerId;
        if (match != null) {
            line.productMasterId = match.productMasterId;
            line.productVariantId = match.productVariantId;
            line.productSiteOfferId = match.productSiteOfferId;
            line.partnerSku = firstNonBlank(item.partnerSku, match.partnerSku);
            line.pskuCode = firstNonBlank(match.pskuCode, item.partnerSku);
            line.noonSku = firstNonBlank(item.noonSku, match.noonSku);
            line.titleCache = firstNonBlank(item.title, match.title);
            line.brandCache = firstNonBlank(item.brand, match.brand);
            line.matchStatus = "MATCHED";
        } else {
            line.partnerSku = item.partnerSku;
            line.pskuCode = item.partnerSku;
            line.noonSku = item.noonSku;
            line.titleCache = item.title;
            line.brandCache = item.brand;
            line.matchStatus = "PRODUCT_UNMATCHED";
            line.matchMessage = "No local product matched by Noon SKU or partner SKU.";
        }
        line.pbarcode = item.pbarcode;
        line.barcode = item.barcode;
        line.warehouseCode = item.warehouseCode;
        line.countryCode = item.countryCode;
        line.inventoryType = item.inventoryType;
        line.reasonCode = item.reasonCode;
        line.classificationCode = item.classificationCode;
        line.stockBucket = item.stockBucket;
        line.quantity = item.quantity == null ? 0 : Math.max(0, item.quantity);
        line.inventorySnapshotAt = item.inventorySnapshotAt;
        line.rawPayloadJson = writeJson(item.rawPayload);
        line.operatorUserId = batch.operatorUserId;
        return line;
    }

    private InventoryLineProductMatchRecord matchProduct(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            InventoryItem item
    ) {
        if (!StringUtils.hasText(item.noonSku) && !StringUtils.hasText(item.partnerSku)) {
            return null;
        }
        return mapper.findInventoryLineProductMatch(
                ownerUserId,
                storeCode,
                siteCode,
                trimToNull(item.noonSku),
                trimToNull(item.partnerSku)
        );
    }

    private String requestSummary(String storeCode, String siteCode, int maxPages) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("source_type", SOURCE_TYPE);
        root.put("store_code", storeCode);
        root.put("site_code", siteCode);
        root.put("max_pages", maxPages);
        root.put("endpoint", OfficialWarehouseFbnInventoryProvider.FBN_INVENTORY_URL);
        ObjectNode body = root.putObject("body");
        body.put("inventory_tab_name", "export");
        body.set("filters", objectMapper.createObjectNode());
        return writeJson(root);
    }

    private String responseSummary(List<InventoryPage> pages, int totalRows) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("page_count", pages.size());
        root.put("total_rows", totalRows);
        root.put("source_type", SOURCE_TYPE);
        return writeJson(root);
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception exception) {
            return null;
        }
    }

    private int maxPages(Integer value) {
        if (value == null || value <= 0) {
            return 20;
        }
        return Math.min(value, 100);
    }

    private Long requireOwnerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null) {
            throw new IllegalArgumentException("缺少业务访问上下文。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null) {
            throw new IllegalArgumentException("无法识别当前业务老板账号。");
        }
        return ownerUserId;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String deriveProjectCode(String partnerId) {
        String safePartnerId = trimToNull(partnerId);
        return safePartnerId == null ? null : "PRJ" + safePartnerId;
    }

    private String derivePartnerId(String projectCode) {
        String safeProjectCode = trimToNull(projectCode);
        if (safeProjectCode == null) {
            return null;
        }
        return safeProjectCode.toUpperCase(Locale.ROOT).startsWith("PRJ") ? safeProjectCode.substring(3) : safeProjectCode;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
