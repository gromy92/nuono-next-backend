package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.firstLong;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.firstText;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.matchStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.maxDate;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.minDate;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.receiptStatus;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.requireOwnerUserId;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.requireText;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnImportSupport.sha256;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportCsvParser.ParsedFile;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportCsvParser.ReceivedRow;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnDownloadedReportPreparer.Prepared;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnLineMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
@ConditionalOnBean(OfficialWarehouseFbnExportProvider.class)
public class OfficialWarehouseFbnReceivedReportImportService {

    public static final String REPORT_TYPE = "FBN_INBOUND_FBNRECEIVEDREPORT";
    private static final String SOURCE_TYPE = "FBN_REPORT_EXPORT_API";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OfficialWarehouseStatisticsMapper mapper;
    private final OfficialWarehouseFbnExportProvider provider;
    private final OfficialWarehouseFbnReceivedReportCsvParser parser;
    private final ObjectMapper objectMapper;
    private final OfficialWarehouseFbnDownloadedReportPreparer downloadedReportPreparer;

    public OfficialWarehouseFbnReceivedReportImportService(
            OfficialWarehouseStatisticsMapper mapper,
            OfficialWarehouseFbnExportProvider provider,
            OfficialWarehouseFbnReceivedReportCsvParser parser,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.provider = provider;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.downloadedReportPreparer = new OfficialWarehouseFbnDownloadedReportPreparer(mapper, parser);
    }

    @Transactional
    public FbnReceivedImportResultView importByExportCode(
            BusinessAccessContext access,
            String exportCode,
            FbnReceivedImportCommand command
    ) {
        FbnReceivedImportCommand safeCommand = command == null ? new FbnReceivedImportCommand() : command;
        String storeCode = requireText(safeCommand.storeCode, "请选择要导入 FBN 入仓报表的店铺。");
        String siteCode = requireText(safeCommand.siteCode, "请选择要导入 FBN 入仓报表的站点。").toUpperCase(Locale.ROOT);
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
        String downloadUrl = OfficialWarehouseFbnReceivedReportIntegrity.validatedDownloadUrl(
                status,
                safeExportCode
        );
        byte[] content = provider.download(request, downloadUrl);
        ParsedFile parsedFile = parser.parse(content);
        OfficialWarehouseFbnReceivedReportIntegrity.validateSourceRows(status.totalRows, parsedFile.sourceDataRowCount);

        String sourceExportCode = safeExportCode;
        String fileName = StringUtils.hasText(status.fileName)
                ? status.fileName
                : "fbn_inbound_fbnreceivedreport-" + sourceExportCode + ".csv";
        return persistDownloaded(
                parsedFile,
                content,
                scope,
                ownerUserId,
                operatorUserId,
                storeCode,
                siteCode,
                sourceExportCode,
                fileName,
                status.status,
                status.totalRows
        );
    }

    /** Runtime entry after DP-07-B has already downloaded and verified the complete artifact. */
    @Transactional
    public FbnReceivedImportResultView importDownloaded(
            BusinessAccessContext access,
            String exportCode,
            FbnReceivedImportCommand command,
            byte[] content,
            String fileName,
            String expectedSha256
    ) {
        return persistDownloaded(downloadedReportPreparer.prepare(
                access,
                exportCode,
                command,
                content,
                fileName,
                expectedSha256
        ));
    }

    private FbnReceivedImportResultView persistDownloaded(Prepared prepared) {
        return persistDownloaded(
                prepared.parsedFile,
                prepared.content,
                prepared.scope,
                prepared.ownerUserId,
                prepared.operatorUserId,
                prepared.storeCode,
                prepared.siteCode,
                prepared.exportCode,
                prepared.fileName,
                "COMPLETE",
                prepared.parsedFile.rows.size()
        );
    }

    private FbnReceivedImportResultView persistDownloaded(
            ParsedFile parsedFile,
            byte[] content,
            InventorySyncScopeRecord scope,
            Long ownerUserId,
            Long operatorUserId,
            String storeCode,
            String siteCode,
            String sourceExportCode,
            String fileName,
            String providerStatus,
            Integer providerTotalRows
    ) {

        Long importId = mapper.nextReportImportId();
        String now = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String fileSha256 = sha256(content);

        mapper.deactivatePreviousFbnReceivedReportImports(
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
        importRecord.summaryJson = summaryJson(
                sourceExportCode,
                providerStatus,
                providerTotalRows,
                parsedFile.rows.size(),
                counters
        );
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
        Set<String> acceptedBusinessKeys = new LinkedHashSet<>();
        for (ReceivedRow row : parsedFile.rows) {
            if (!acceptedBusinessKeys.add(row.businessKey())) {
                continue;
            }
            RowBuildResult rowBuild = buildRow(scope, ownerUserId, storeCode, siteCode, row);
            Long reportRowId = mapper.nextReportRowId();
            ReportRowInsertRecord reportRow = reportRowRecord(
                    importId,
                    reportRowId,
                    row,
                    rowBuild.warningCodes,
                    rowBuild.receiptStatus,
                    rowBuild.matchStatus,
                    operatorUserId
            );
            mapper.insertReportRow(reportRow);

            Long lineId = mapper.nextInboundReceiptLineId();
            InboundReceiptLineInsertRecord receiptLine = receiptLineRecord(
                    importId,
                    reportRowId,
                    lineId,
                    scope,
                    ownerUserId,
                    storeCode,
                    siteCode,
                    row,
                    rowBuild,
                    operatorUserId
            );
            mapper.insertInboundReceiptLine(receiptLine);

            counters.insertedRows++;
            if (!rowBuild.warningCodes.isEmpty()) {
                counters.warningRows++;
            }
            counters.businessDateStart = minDate(counters.businessDateStart, row.asnScheduleDate);
            counters.businessDateEnd = maxDate(counters.businessDateEnd, row.asnScheduleDate);
        }
        return counters;
    }

    private RowBuildResult buildRow(
            InventorySyncScopeRecord scope,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            ReceivedRow row
    ) {
        Set<String> warningCodes = new LinkedHashSet<>();
        if (!StringUtils.hasText(row.noonAsnNr)) {
            warningCodes.add("MISSING_ASN");
        }
        if (!StringUtils.hasText(row.noonSku)
                && !StringUtils.hasText(row.partnerSku)
                && !StringUtils.hasText(row.pbarcodeCanonical)) {
            warningCodes.add("MISSING_PRODUCT_KEY");
        }

        InboundReceiptAsnMatchRecord asnMatch = null;
        if (StringUtils.hasText(row.noonAsnNr)) {
            asnMatch = mapper.findInboundReceiptAsnMatch(ownerUserId, storeCode, siteCode, row.noonAsnNr);
        }

        InventoryLineProductMatchRecord productMatch = null;
        if (StringUtils.hasText(row.noonSku) || StringUtils.hasText(row.partnerSku)) {
            productMatch = mapper.findInventoryLineProductMatch(
                    ownerUserId,
                    storeCode,
                    siteCode,
                    row.noonSku,
                    row.partnerSku
            );
        }

        InboundReceiptAsnLineMatchRecord lineMatch = null;
        if (asnMatch != null && (StringUtils.hasText(row.noonSku) || StringUtils.hasText(row.partnerSku))) {
            lineMatch = mapper.findInboundReceiptAsnLineMatch(
                    asnMatch.asnId,
                    ownerUserId,
                    storeCode,
                    siteCode,
                    row.noonSku,
                    row.partnerSku
            );
        }

        String matchStatus = matchStatus(asnMatch, lineMatch, productMatch);
        if (!"MATCHED".equals(matchStatus)) {
            warningCodes.add(matchStatus);
        }
        String receiptStatus = receiptStatus(row);
        if (!"NORMAL".equals(receiptStatus)) {
            warningCodes.add(receiptStatus);
        }

        RowBuildResult result = new RowBuildResult();
        result.asnMatch = asnMatch;
        result.lineMatch = lineMatch;
        result.productMatch = productMatch;
        result.matchStatus = matchStatus;
        result.receiptStatus = receiptStatus;
        result.warningCodes = new ArrayList<>(warningCodes);
        result.logicalStoreId = scope.logicalStoreId;
        return result;
    }

    private ReportRowInsertRecord reportRowRecord(
            Long importId,
            Long reportRowId,
            ReceivedRow row,
            List<String> warningCodes,
            String receiptStatus,
            String matchStatus,
            Long operatorUserId
    ) {
        ReportRowInsertRecord record = new ReportRowInsertRecord();
        record.id = reportRowId;
        record.importId = importId;
        record.reportType = REPORT_TYPE;
        record.rowNo = row.rowNo;
        record.businessKey = row.businessKey();
        record.businessKeyHash = sha256(row.businessKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        record.rowStatus = warningCodes.isEmpty() ? "VALID" : "WARNING";
        record.warningCode = warningCodes.isEmpty() ? null : String.join(",", warningCodes);
        record.errorMessage = null;
        record.rawRowJson = toJson(row.rawFields);
        record.normalizedRowJson = normalizedRowJson(row, receiptStatus, matchStatus, warningCodes);
        record.operatorUserId = operatorUserId;
        return record;
    }

    private InboundReceiptLineInsertRecord receiptLineRecord(
            Long importId,
            Long reportRowId,
            Long lineId,
            InventorySyncScopeRecord scope,
            Long ownerUserId,
            String storeCode,
            String siteCode,
            ReceivedRow row,
            RowBuildResult rowBuild,
            Long operatorUserId
    ) {
        InboundReceiptLineInsertRecord record = new InboundReceiptLineInsertRecord();
        record.id = lineId;
        record.importId = importId;
        record.reportRowId = reportRowId;
        record.ownerUserId = ownerUserId;
        record.logicalStoreId = scope.logicalStoreId;
        record.storeCode = storeCode;
        record.siteCode = siteCode;
        record.projectCode = scope.projectCode;
        record.partnerId = scope.partnerId;
        record.asnId = rowBuild.asnMatch == null ? null : rowBuild.asnMatch.asnId;
        record.asnLineId = rowBuild.lineMatch == null ? null : rowBuild.lineMatch.asnLineId;
        record.noonAsnNr = row.noonAsnNr;
        record.productMasterId = firstLong(
                rowBuild.lineMatch == null ? null : rowBuild.lineMatch.productMasterId,
                rowBuild.productMatch == null ? null : rowBuild.productMatch.productMasterId
        );
        record.productVariantId = firstLong(
                rowBuild.lineMatch == null ? null : rowBuild.lineMatch.productVariantId,
                rowBuild.productMatch == null ? null : rowBuild.productMatch.productVariantId
        );
        record.productSiteOfferId = firstLong(
                rowBuild.lineMatch == null ? null : rowBuild.lineMatch.productSiteOfferId,
                rowBuild.productMatch == null ? null : rowBuild.productMatch.productSiteOfferId
        );
        record.partnerSku = firstText(
                row.partnerSku,
                rowBuild.lineMatch == null ? null : rowBuild.lineMatch.partnerSku,
                rowBuild.productMatch == null ? null : rowBuild.productMatch.partnerSku
        );
        record.pskuCode = firstText(
                rowBuild.lineMatch == null ? null : rowBuild.lineMatch.pskuCode,
                rowBuild.productMatch == null ? null : rowBuild.productMatch.pskuCode
        );
        record.noonSku = firstText(
                row.noonSku,
                rowBuild.lineMatch == null ? null : rowBuild.lineMatch.noonSku,
                rowBuild.productMatch == null ? null : rowBuild.productMatch.noonSku
        );
        record.pbarcodeCanonical = row.pbarcodeCanonical;
        record.partnerWarehouse = row.partnerWarehouse;
        record.noonWarehouse = row.noonWarehouse;
        record.countryCode = row.countryCode;
        record.qtyExpected = row.qtyExpected;
        record.receivedQty = row.receivedQty;
        record.qcFailedQty = row.qcFailedQty;
        record.unidentifiedQty = row.unidentifiedQty;
        record.qcFailedReason = row.qcFailedReason;
        record.receiptStatus = rowBuild.receiptStatus;
        record.matchStatus = rowBuild.matchStatus;
        record.anomalyFlagsJson = toJson(rowBuild.warningCodes);
        record.asnCreatedAt = row.asnCreatedAt;
        record.asnScheduleDate = row.asnScheduleDate;
        record.asnCompletedAt = row.asnCompletedAt;
        record.rawPayloadJson = toJson(row.rawFields);
        record.operatorUserId = operatorUserId;
        return record;
    }

    private String normalizedRowJson(
            ReceivedRow row,
            String receiptStatus,
            String matchStatus,
            List<String> warningCodes
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("partnerSku", row.partnerSku);
        node.put("noonSku", row.noonSku);
        node.put("noonAsnNr", row.noonAsnNr);
        node.put("pbarcodeCanonical", row.pbarcodeCanonical);
        node.put("partnerWarehouse", row.partnerWarehouse);
        node.put("noonWarehouse", row.noonWarehouse);
        node.put("countryCode", row.countryCode);
        node.put("qtyExpected", row.qtyExpected);
        node.put("receivedQty", row.receivedQty);
        node.put("qcFailedQty", row.qcFailedQty);
        node.put("unidentifiedQty", row.unidentifiedQty);
        node.put("receiptStatus", receiptStatus);
        node.put("matchStatus", matchStatus);
        ArrayNode warnings = node.putArray("warningCodes");
        for (String warningCode : warningCodes) {
            warnings.add(warningCode);
        }
        return toJson(node);
    }

    private String summaryJson(
            String exportCode,
            String providerStatus,
            Integer providerTotalRows,
            int parsedRows,
            ImportCounters counters
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sourceType", SOURCE_TYPE);
        node.put("exportCode", exportCode);
        node.put("providerStatus", providerStatus);
        if (providerTotalRows != null) {
            node.put("providerTotalRows", providerTotalRows);
        }
        node.put("parsedRows", parsedRows);
        node.put("insertedReceiptLines", counters.insertedRows);
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

    private FbnReceivedImportResultView resultView(ReportImportInsertRecord record, ImportCounters counters) {
        FbnReceivedImportResultView view = new FbnReceivedImportResultView();
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
        view.insertedReceiptLines = counters.insertedRows;
        view.fileName = record.fileName;
        view.fileSha256 = record.fileSha256;
        view.importedAt = record.snapshotAt;
        view.sourceType = SOURCE_TYPE;
        return view;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("官方仓报表 JSON 序列化失败。", exception);
        }
    }

    private static class RowBuildResult {
        private InboundReceiptAsnMatchRecord asnMatch;
        private InboundReceiptAsnLineMatchRecord lineMatch;
        private InventoryLineProductMatchRecord productMatch;
        private String receiptStatus;
        private String matchStatus;
        private List<String> warningCodes = List.of();
        private Long logicalStoreId;
    }

    private static class ImportCounters {
        private int insertedRows;
        private int warningRows;
        private int errorRows;
        private String businessDateStart;
        private String businessDateEnd;
    }
}
