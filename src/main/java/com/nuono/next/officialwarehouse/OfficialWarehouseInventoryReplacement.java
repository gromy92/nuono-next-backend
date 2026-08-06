package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySnapshotLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncBatchInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.datapull.Dp07InventorySnapshotItem;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Short local transaction that replaces one proven-complete FBN inventory fact scope.
 * It deliberately does not update product/listing/logistics projections.
 */
@Component
@Profile("local-db")
public class OfficialWarehouseInventoryReplacement {

    static final String SOURCE_TYPE = "FBN_INVENTORY_API";
    private static final DateTimeFormatter RESULT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OfficialWarehouseStatisticsMapper mapper;
    private final ObjectMapper objectMapper;

    public OfficialWarehouseInventoryReplacement(
            OfficialWarehouseStatisticsMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Transactional
    public OfficialWarehouseInventoryReplacementResult replace(
            OfficialWarehouseInventoryReplacementCommand command
    ) {
        OfficialWarehouseInventoryReplacementCommand value =
                Objects.requireNonNull(command, "command").validate();
        InventorySyncScopeRecord scope = mapper.selectInventorySyncScope(
                value.ownerUserId,
                value.storeCode,
                value.siteCode
        );
        requireScope(scope, value);

        InventorySyncBatchInsertRecord batch = new InventorySyncBatchInsertRecord();
        batch.id = mapper.nextInventorySyncBatchId();
        batch.ownerUserId = value.ownerUserId;
        batch.logicalStoreId = scope.logicalStoreId;
        batch.storeCode = value.storeCode;
        batch.siteCode = value.siteCode;
        batch.projectCode = firstNonBlank(scope.projectCode, value.projectCode);
        batch.partnerId = firstNonBlank(scope.partnerId, derivePartnerId(batch.projectCode));
        batch.sourceType = SOURCE_TYPE;
        batch.requestSummaryJson = requestSummary(value);
        batch.responseSummaryJson = responseSummary(value);
        batch.status = "IMPORTED";
        batch.totalPages = value.totalPages;
        batch.totalRows = value.items.size();
        batch.validRows = value.items.size();
        batch.errorRows = value.skippedItemCount;
        batch.operatorUserId = value.operatorUserId;
        requireSingleRow(mapper.insertInventorySyncBatch(batch), "inventory sync batch insert");
        requireNonNegative(
                mapper.deactivateCurrentInventorySnapshotLines(
                        value.ownerUserId,
                        value.storeCode,
                        value.siteCode
                ),
                "inventory current-line deactivation"
        );

        int insertedRows = 0;
        for (Dp07InventorySnapshotItem item : value.items) {
            InventorySnapshotLineInsertRecord line = toLine(scope, batch, item);
            requireSingleRow(mapper.insertInventorySnapshotLine(line), "inventory line insert");
            insertedRows += 1;
        }
        return new OfficialWarehouseInventoryReplacementResult(
                batch.id,
                value.storeCode,
                value.siteCode,
                value.totalPages,
                value.items.size(),
                insertedRows,
                LocalDateTime.now().format(RESULT_TIME_FORMAT)
        );
    }

    private InventorySnapshotLineInsertRecord toLine(
            InventorySyncScopeRecord scope,
            InventorySyncBatchInsertRecord batch,
            Dp07InventorySnapshotItem item
    ) {
        InventoryLineProductMatchRecord match = matchProduct(
                batch.ownerUserId,
                batch.storeCode,
                batch.siteCode,
                item
        );
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
            line.partnerSku = firstNonBlank(item.getPartnerSku(), match.partnerSku);
            line.pskuCode = match.pskuCode;
            line.noonSku = firstNonBlank(item.getNoonSku(), match.noonSku);
            line.titleCache = firstNonBlank(item.getTitle(), match.title);
            line.brandCache = firstNonBlank(item.getBrand(), match.brand);
            line.matchStatus = "MATCHED";
        } else {
            line.partnerSku = item.getPartnerSku();
            line.noonSku = item.getNoonSku();
            line.titleCache = item.getTitle();
            line.brandCache = item.getBrand();
            line.matchStatus = "PRODUCT_UNMATCHED";
            line.matchMessage = "No local product matched by Noon SKU or partner SKU.";
        }
        line.pbarcode = item.getPbarcode();
        line.barcode = item.getBarcode();
        line.warehouseCode = item.getWarehouseCode();
        line.countryCode = item.getCountryCode();
        line.inventoryType = item.getInventoryType();
        line.reasonCode = item.getReasonCode();
        line.classificationCode = item.getClassificationCode();
        line.stockBucket = item.getStockBucket();
        line.quantity = item.getQuantity();
        line.inventorySnapshotAt = item.getInventorySnapshotAt();
        line.rawPayloadJson = item.getRawPayloadJson();
        line.operatorUserId = batch.operatorUserId;
        return line;
    }

    private InventoryLineProductMatchRecord matchProduct(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Dp07InventorySnapshotItem item
    ) {
        if (!StringUtils.hasText(item.getNoonSku())
                && !StringUtils.hasText(item.getPartnerSku())) {
            return null;
        }
        return mapper.findInventoryLineProductMatch(
                ownerUserId,
                storeCode,
                siteCode,
                trimToNull(item.getNoonSku()),
                trimToNull(item.getPartnerSku())
        );
    }

    private void requireScope(
            InventorySyncScopeRecord scope,
            OfficialWarehouseInventoryReplacementCommand command
    ) {
        if (scope == null
                || !Objects.equals(scope.ownerUserId, command.ownerUserId)
                || !Objects.equals(scope.logicalStoreId, command.logicalStoreId)
                || !same(scope.storeCode, command.storeCode)
                || !sameIgnoreCase(scope.siteCode, command.siteCode)
                || !same(scope.projectCode, command.projectCode)) {
            throw new IllegalStateException("official-warehouse inventory scope changed before apply");
        }
    }

    private String requestSummary(OfficialWarehouseInventoryReplacementCommand command) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("source_type", SOURCE_TYPE);
        root.put("store_code", command.storeCode);
        root.put("site_code", command.siteCode);
        root.put("source_batch_ref", command.sourceBatchReference);
        root.put("completion_evidence", "pages-1-through-last");
        root.put("endpoint", OfficialWarehouseFbnInventoryProvider.FBN_INVENTORY_URL);
        ObjectNode body = root.putObject("body");
        body.put("inventory_tab_name", "export");
        body.set("filters", objectMapper.createObjectNode());
        return writeJson(root);
    }

    private String responseSummary(OfficialWarehouseInventoryReplacementCommand command) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("page_count", command.totalPages);
        root.put("total_rows", command.items.size());
        root.put("skipped_rows", command.skippedItemCount);
        root.put("source_type", SOURCE_TYPE);
        return writeJson(root);
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception failure) {
            throw new IllegalStateException("inventory summary JSON cannot be encoded", failure);
        }
    }

    private String derivePartnerId(String projectCode) {
        String value = trimToNull(projectCode);
        if (value == null) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT).startsWith("PRJ") ? value.substring(3) : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean same(String left, String right) {
        return Objects.equals(trimToNull(left), trimToNull(right));
    }

    private boolean sameIgnoreCase(String left, String right) {
        String first = trimToNull(left);
        String second = trimToNull(right);
        return first != null && second != null && first.equalsIgnoreCase(second);
    }

    private void requireSingleRow(int changed, String action) {
        if (changed != 1) {
            throw new IllegalStateException(action + " must affect exactly one row");
        }
    }

    private void requireNonNegative(int changed, String action) {
        if (changed < 0) {
            throw new IllegalStateException(action + " returned an invalid row count");
        }
    }

}
