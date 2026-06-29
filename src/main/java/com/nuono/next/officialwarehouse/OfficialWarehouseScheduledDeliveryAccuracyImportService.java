package com.nuono.next.officialwarehouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseScheduledDeliveryAccuracyCsvParser.AccuracyRow;
import com.nuono.next.officialwarehouse.OfficialWarehouseScheduledDeliveryAccuracyCsvParser.ParsedFile;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyAsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyImportResultView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(OfficialWarehouseFbnExportProvider.class)
public class OfficialWarehouseScheduledDeliveryAccuracyImportService {

    public static final String REPORT_TYPE = "FBN_INBOUND_SCHEDULEDDELIVERYACCURACY";
    private static final String SOURCE_TYPE = "FBN_REPORT_EXPORT_API";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OfficialWarehouseStatisticsMapper mapper;
    private final OfficialWarehouseFbnExportProvider provider;
    private final OfficialWarehouseScheduledDeliveryAccuracyCsvParser parser;
    private final ObjectMapper objectMapper;

    public OfficialWarehouseScheduledDeliveryAccuracyImportService(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseFbnExportProvider provider,
            OfficialWarehouseScheduledDeliveryAccuracyCsvParser parser,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.provider = provider;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ScheduledDeliveryAccuracyImportResultView importByExportCode(
            BusinessAccessContext access,
            String exportCode,
            ScheduledDeliveryAccuracyImportCommand command
    ) {
        ScheduledDeliveryAccuracyImportCommand safeCommand =
                command == null ? new ScheduledDeliveryAccuracyImportCommand() : command;
        String storeCode = requireText(safeCommand.storeCode, "请选择要导入预约到货准确率报表的店铺。");
        String siteCode = requireText(safeCommand.siteCode, "请选择要导入预约到货准确率报表的站点。")
                .toUpperCase(Locale.ROOT);
        String safeExportCode = requireText(exportCode, "缺少 FBN 报表 exportCode。");
        Long ownerUserId = requireOwnerUserId(access, storeCode);
        Long operatorUserId = access == null ? ownerUserId : access.getSessionUserId();
        if (operatorUserId == null) {
            operatorUserId = ownerUserId;
        }

        InventorySyncScopeRecord scope = mapper.selectInventorySyncScope(ownerUserId, storeCode, siteCode);
        if (scope == null) {
            throw new IllegalArgumentException("当前店铺未配置官方仓统计范围。");
        }

        PullRequest request = new PullRequest(ownerUserId, storeCode, siteCode);
        ExportStatus status = provider.exportStatus(request, safeExportCode, Boolean.TRUE.equals(safeCommand.logStatus));
        if (!isComplete(status == null ? null : status.status)) {
            throw new IllegalArgumentException("FBN 报表尚未完成，当前状态：" + (status == null ? "UNKNOWN" : status.status));
        }
        String downloadUrl = requireText(status.downloadUrl, "FBN 报表已完成但没有下载地址。");
        byte[] content = provider.download(request, downloadUrl);
        ParsedFile parsedFile = parser.parse(content);

        Long importId = mapper.nextReportImportId();
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String sourceExportCode = StringUtils.hasText(status.exportCode) ? status.exportCode : safeExportCode;
        String fileName = StringUtils.hasText(status.fileName)
                ? status.fileName
                : "fbn_inbound_scheduleddeliveryaccuracy-" + sourceExportCode + ".csv";
        String fileSha256 = sha256(content);

        mapper.deactivatePreviousScheduledDeliveryAccuracyImports(
                ownerUserId,
                storeCode,
                siteCode,
                REPORT_TYPE,
                sourceExportCode,
                operatorUserId
        );

        ImportCounters counters = insertRows(
                parsedFile,
                importId,
                scope,
                ownerUserId,
                storeCode,
                siteCode,
                operatorUserId
        );

        ReportImportInsertRecord importRecord = new ReportImportInsertRecord();
        importRecord.id = importId;
        importRecord.ownerUserId = ownerUserId;
        importRecord.logicalStoreId = scope.logicalStoreId;
        importRecord.storeCode = storeCode;
        importRecord.siteCode = siteCode;
        importRecord.projectCode = scope.projectCode;
        importRecord.partnerId = scope.partnerId;
        importRecord.reportType = REPORT_TYPE;
        importRecord.sourceType = SOURCE_TYPE;
        importRecord.sourceExportCode = sourceExportCode;
        importRecord.fileName = fileName;
        importRecord.fileSha256 = fileSha256;
        importRecord.snapshotAt = now;
        importRecord.businessDateStart = counters.businessDateStart;
        importRecord.businessDateEnd = counters.businessDateEnd;
        importRecord.totalRows = parsedFile.rows.size();
        importRecord.validRows = counters.insertedRows;
        importRecord.warningRows = counters.warningRows;
        importRecord.errorRows = counters.errorRows;
        importRecord.status = counters.errorRows > 0 ? "PARTIAL_IMPORTED" : "IMPORTED";
        importRecord.summaryJson = summaryJson(sourceExportCode, status, parsedFile.rows.size(), counters);
        importRecord.rawPreviewJson = rawPreviewJson(parsedFile);
        importRecord.operatorUserId = operatorUserId;
        mapper.insertReportImport(importRecord);

        return resultView(importRecord, counters);
    }

    private ImportCounters insertRows(
            ParsedFile parsedFile,
            Long importId,
            InventorySyncScopeRecord scope,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            Long operatorUserId
    ) {
        ImportCounters counters = new ImportCounters();
        for (AccuracyRow row : parsedFile.rows) {
            RowBuildResult rowBuild = buildRow(ownerUserId, storeCode, siteCode, row);
            Long reportRowId = mapper.nextReportRowId();
            ReportRowInsertRecord reportRow = reportRowRecord(importId, reportRowId, row, rowBuild, operatorUserId);
            mapper.insertReportRow(reportRow);

            Long factId = mapper.nextDeliveryAccuracyAsnId();
            DeliveryAccuracyAsnInsertRecord fact = deliveryAccuracyRecord(
                    importId,
                    reportRowId,
                    factId,
                    scope,
                    ownerUserId,
                    storeCode,
                    siteCode,
                    row,
                    rowBuild,
                    operatorUserId
            );
            mapper.insertDeliveryAccuracyAsn(fact);

            counters.insertedRows++;
            counters.scheduledQuantity += row.scheduledQty;
            counters.grnQuantity += row.grnQty;
            counters.inboundQuantityVariance += row.inboundQtyVariance;
            counters.statusCounts.merge(rowBuild.accuracyStatus, 1, Integer::sum);
            if ("MATCHED".equals(rowBuild.matchStatus)) {
                counters.matchedAsnRows++;
            } else {
                counters.unmatchedAsnRows++;
            }
            if (!rowBuild.warningCodes.isEmpty()) {
                counters.warningRows++;
            }
            counters.businessDateStart = minDate(counters.businessDateStart, row.scheduledDate);
            counters.businessDateEnd = maxDate(counters.businessDateEnd, row.scheduledDate);
        }
        return counters;
    }

    private RowBuildResult buildRow(
            Long ownerUserId,
            String storeCode,
            String siteCode,
            AccuracyRow row
    ) {
        Set<String> warningCodes = new LinkedHashSet<>();
        if (!StringUtils.hasText(row.noonAsnNr)) {
            warningCodes.add("MISSING_ASN");
        }

        InboundReceiptAsnMatchRecord asnMatch = null;
        if (StringUtils.hasText(row.noonAsnNr)) {
            asnMatch = mapper.findInboundReceiptAsnMatch(ownerUserId, storeCode, siteCode, row.noonAsnNr);
        }
        String matchStatus = asnMatch == null ? "NO_LOCAL_ASN" : "MATCHED";
        if (!"MATCHED".equals(matchStatus)) {
            warningCodes.add(matchStatus);
        }

        String accuracyStatus = normalizeStatus(row.status);
        if (!"PUTAWAY_COMPLETED".equals(accuracyStatus)) {
            warningCodes.add("STATUS_" + accuracyStatus);
        }
        if (row.inboundQtyVariance > 0) {
            warningCodes.add("QUANTITY_VARIANCE");
        }

        RowBuildResult result = new RowBuildResult();
        result.asnMatch = asnMatch;
        result.matchStatus = matchStatus;
        result.accuracyStatus = accuracyStatus;
        result.warningCodes = new ArrayList<>(warningCodes);
        return result;
    }

    private ReportRowInsertRecord reportRowRecord(
            Long importId,
            Long reportRowId,
            AccuracyRow row,
            RowBuildResult rowBuild,
            Long operatorUserId
    ) {
        ReportRowInsertRecord record = new ReportRowInsertRecord();
        record.id = reportRowId;
        record.importId = importId;
        record.reportType = REPORT_TYPE;
        record.rowNo = row.rowNo;
        record.businessKey = row.businessKey();
        record.businessKeyHash = sha256(row.businessKey().getBytes(StandardCharsets.UTF_8));
        record.rowStatus = rowBuild.warningCodes.isEmpty() ? "VALID" : "WARNING";
        record.warningCode = rowBuild.warningCodes.isEmpty() ? null : String.join(",", rowBuild.warningCodes);
        record.errorMessage = null;
        record.rawRowJson = toJson(row.rawFields);
        record.normalizedRowJson = normalizedRowJson(row, rowBuild);
        record.operatorUserId = operatorUserId;
        return record;
    }

    private DeliveryAccuracyAsnInsertRecord deliveryAccuracyRecord(
            Long importId,
            Long reportRowId,
            Long factId,
            InventorySyncScopeRecord scope,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            AccuracyRow row,
            RowBuildResult rowBuild,
            Long operatorUserId
    ) {
        DeliveryAccuracyAsnInsertRecord record = new DeliveryAccuracyAsnInsertRecord();
        record.id = factId;
        record.importId = importId;
        record.reportRowId = reportRowId;
        record.ownerUserId = ownerUserId;
        record.logicalStoreId = scope.logicalStoreId;
        record.storeCode = storeCode;
        record.siteCode = siteCode;
        record.projectCode = scope.projectCode;
        record.partnerId = scope.partnerId;
        record.asnId = rowBuild.asnMatch == null ? null : rowBuild.asnMatch.asnId;
        record.appointmentId = rowBuild.asnMatch == null ? null : rowBuild.asnMatch.appointmentId;
        record.noonAsnNr = row.noonAsnNr;
        record.warehouseCode = row.warehouseCode;
        record.countryCode = row.countryCode;
        record.asnCreationDate = row.asnCreationDate;
        record.scheduledDate = row.scheduledDate;
        record.deliveryDate = row.deliveryDate;
        record.scheduledQty = row.scheduledQty;
        record.grnQty = row.grnQty;
        record.inboundQtyVariance = row.inboundQtyVariance;
        record.accuracyStatus = rowBuild.accuracyStatus;
        record.inboundUtilizationEfficiency = row.inboundUtilizationEfficiency;
        record.matchStatus = rowBuild.matchStatus;
        record.anomalyFlagsJson = toJson(rowBuild.warningCodes);
        record.rawPayloadJson = toJson(row.rawFields);
        record.operatorUserId = operatorUserId;
        return record;
    }

    private String normalizedRowJson(AccuracyRow row, RowBuildResult rowBuild) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("noonAsnNr", row.noonAsnNr);
        node.put("warehouseCode", row.warehouseCode);
        node.put("countryCode", row.countryCode);
        node.put("asnCreationDate", row.asnCreationDate);
        node.put("scheduledDate", row.scheduledDate);
        node.put("deliveryDate", row.deliveryDate);
        node.put("scheduledQty", row.scheduledQty);
        node.put("grnQty", row.grnQty);
        node.put("inboundQtyVariance", row.inboundQtyVariance);
        node.put("accuracyStatus", rowBuild.accuracyStatus);
        node.put("matchStatus", rowBuild.matchStatus);
        ArrayNode warnings = node.putArray("warningCodes");
        for (String warningCode : rowBuild.warningCodes) {
            warnings.add(warningCode);
        }
        return toJson(node);
    }

    private String summaryJson(String exportCode, ExportStatus status, int parsedRows, ImportCounters counters) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sourceType", SOURCE_TYPE);
        node.put("exportCode", exportCode);
        node.put("providerStatus", status.status);
        if (status.totalRows != null) {
            node.put("providerTotalRows", status.totalRows);
        }
        node.put("parsedRows", parsedRows);
        node.put("insertedAsnRows", counters.insertedRows);
        node.put("scheduledQuantity", counters.scheduledQuantity);
        node.put("grnQuantity", counters.grnQuantity);
        node.put("inboundQuantityVariance", counters.inboundQuantityVariance);
        node.put("matchedAsnRows", counters.matchedAsnRows);
        node.put("unmatchedAsnRows", counters.unmatchedAsnRows);
        node.set("statusCounts", objectMapper.valueToTree(counters.statusCounts));
        node.put("warningRows", counters.warningRows);
        node.put("errorRows", counters.errorRows);
        return toJson(node);
    }

    private String rawPreviewJson(ParsedFile parsedFile) {
        ArrayNode array = objectMapper.createArrayNode();
        int limit = Math.min(parsedFile.rows.size(), 5);
        for (int index = 0; index < limit; index++) {
            array.add(objectMapper.valueToTree(parsedFile.rows.get(index).rawFields));
        }
        return toJson(array);
    }

    private ScheduledDeliveryAccuracyImportResultView resultView(
            ReportImportInsertRecord record,
            ImportCounters counters
    ) {
        ScheduledDeliveryAccuracyImportResultView view = new ScheduledDeliveryAccuracyImportResultView();
        view.importId = String.valueOf(record.id);
        view.storeCode = record.storeCode;
        view.siteCode = record.siteCode;
        view.exportCode = record.sourceExportCode;
        view.reportType = record.reportType;
        view.status = record.status;
        view.totalRows = record.totalRows;
        view.validRows = record.validRows;
        view.warningRows = record.warningRows;
        view.errorRows = record.errorRows;
        view.insertedAsnRows = counters.insertedRows;
        view.scheduledQuantity = counters.scheduledQuantity;
        view.grnQuantity = counters.grnQuantity;
        view.inboundQuantityVariance = counters.inboundQuantityVariance;
        view.fileName = record.fileName;
        view.fileSha256 = record.fileSha256;
        view.importedAt = record.snapshotAt;
        view.sourceType = SOURCE_TYPE;
        return view;
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "UNKNOWN";
        }
        return status.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }

    private String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content == null ? new byte[0] : content);
            StringBuilder builder = new StringBuilder();
            for (byte value : hash) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前环境不支持 SHA-256。", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("官方仓预约到货准确率 JSON 序列化失败。", exception);
        }
    }

    private String minDate(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return left;
        }
        if (!StringUtils.hasText(left)) {
            return right;
        }
        return left.compareTo(right) <= 0 ? left : right;
    }

    private String maxDate(String left, String right) {
        if (!StringUtils.hasText(right)) {
            return left;
        }
        if (!StringUtils.hasText(left)) {
            return right;
        }
        return left.compareTo(right) >= 0 ? left : right;
    }

    private boolean isComplete(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        return "COMPLETE".equals(normalized)
                || "COMPLETED".equals(normalized)
                || "SUCCESS".equals(normalized)
                || "READY".equals(normalized)
                || "DONE".equals(normalized);
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
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static class RowBuildResult {
        private InboundReceiptAsnMatchRecord asnMatch;
        private String matchStatus;
        private String accuracyStatus;
        private List<String> warningCodes = List.of();
    }

    private static class ImportCounters {
        private int insertedRows;
        private int warningRows;
        private int errorRows;
        private int scheduledQuantity;
        private int grnQuantity;
        private int inboundQuantityVariance;
        private int matchedAsnRows;
        private int unmatchedAsnRows;
        private String businessDateStart;
        private String businessDateEnd;
        private final Map<String, Integer> statusCounts = new LinkedHashMap<>();
    }
}
