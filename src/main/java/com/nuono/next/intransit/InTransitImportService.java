package com.nuono.next.intransit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveNodeCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportBatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewBatchView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewIssueView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewLineView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitBatchRecords.NodeRow;
import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class InTransitImportService {

    private static final Map<String, String> HEADER_ALIASES = buildHeaderAliases();
    private static final List<String> TEMPLATE_HEADERS = List.of(
            "批次号",
            "批次状态",
            "原始货代",
            "运输方式",
            "目的地",
            "店铺编码",
            "站点",
            "目的仓",
            "发货日期",
            "预计到仓",
            "国内收货日期",
            "发往海外日期",
            "完成清关日期",
            "ET海外仓入库日期",
            "物流单号",
            "箱号",
            "柜号",
            "PSKU",
            "SKU",
            "MSKU",
            "商品名称",
            "发货数量",
            "已入仓数量",
            "箱数",
            "单箱数量",
            "单箱重量",
            "单箱体积"
    );
    private static final List<List<String>> TEMPLATE_SAMPLE_ROWS = List.of(
            List.of(
                    "TMP-INTRANSIT-SEA-001",
                    "运输中",
                    "启客",
                    "海运",
                    "DB",
                    "STR245027-NAE",
                    "AE",
                    "FBN-DXB",
                    "2026-06-01",
                    "2026-07-10",
                    "2026-06-01",
                    "2026-06-05",
                    "2026-07-01",
                    "2026-07-10",
                    "SEA-TEMPLATE-001",
                    "TMP-INTRANSIT-SEA-001-BOX-1",
                    "CONT-TEMPLATE-001",
                    "PSKU-TEMPLATE-SEA-001",
                    "SKU-TEMPLATE-SEA-001",
                    "MSKU-TEMPLATE-SEA-001",
                    "海运模板商品",
                    "120",
                    "0",
                    "12",
                    "10",
                    "18.5",
                    "0.42"
            ),
            List.of(
                    "TMP-INTRANSIT-AIR-001",
                    "运输中",
                    "义特",
                    "空运",
                    "RUH",
                    "STR245027-NSA",
                    "SA",
                    "FBN-RUH",
                    "2026-06-05",
                    "2026-06-15",
                    "2026-06-05",
                    "2026-06-06",
                    "2026-06-12",
                    "2026-06-15",
                    "AIR-TEMPLATE-001",
                    "TMP-INTRANSIT-AIR-001-BOX-1",
                    "",
                    "PSKU-TEMPLATE-AIR-001",
                    "SKU-TEMPLATE-AIR-001",
                    "MSKU-TEMPLATE-AIR-001",
                    "空运模板商品",
                    "40",
                    "0",
                    "4",
                    "10",
                    "6.2",
                    "0.12"
            )
    );

    private final InTransitGoodsMapper mapper;
    private final InTransitForwarderService forwarderService;
    private final InTransitBatchService batchService;
    private final InTransitOperationAuditService auditService;
    private final InTransitGoodsAccessScopeService accessScopeService;
    private final ObjectMapper objectMapper;

    public InTransitImportService(
            InTransitGoodsMapper mapper,
            InTransitForwarderService forwarderService,
            InTransitBatchService batchService,
            InTransitOperationAuditService auditService,
            InTransitGoodsAccessScopeService accessScopeService,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.forwarderService = forwarderService;
        this.batchService = batchService;
        this.auditService = auditService;
        this.accessScopeService = accessScopeService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public byte[] buildTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("在途商品导入模板");
            Row header = sheet.createRow(0);
            for (int index = 0; index < TEMPLATE_HEADERS.size(); index++) {
                header.createCell(index).setCellValue(TEMPLATE_HEADERS.get(index));
            }
            for (int rowIndex = 0; rowIndex < TEMPLATE_SAMPLE_ROWS.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<String> values = TEMPLATE_SAMPLE_ROWS.get(rowIndex);
                for (int columnIndex = 0; columnIndex < values.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(values.get(columnIndex));
                }
            }
            for (int index = 0; index < TEMPLATE_HEADERS.size(); index++) {
                sheet.autoSizeColumn(index);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("导入模板生成失败。", exception);
        }
    }

    @Transactional
    public ImportPreviewView preview(PreviewImportCommand command) {
        PreviewImportCommand resolved = command == null ? new PreviewImportCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long operatorUserId = resolved.getOperatorUserId();
        byte[] content = resolved.getContent();
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("导入文件不能为空。");
        }
        String fileName = clean(resolved.getFileName());
        String extension = extension(fileName);
        List<ImportRowDraft> rows = parseRows(extension, content);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入文件没有可预览的数据行。");
        }

        ImportPreviewView preview = new ImportPreviewView();
        preview.setImportBatchId(mapper.nextImportBatchId());
        preview.setFileName(fileName);
        preview.setTotalRowCount(rows.size());

        LinkedHashMap<String, ImportPreviewBatchView> batches = new LinkedHashMap<>();
        List<ImportPreviewIssueView> issues = new ArrayList<>();
        int validRowCount = 0;
        for (ImportRowDraft row : rows) {
            List<ImportPreviewIssueView> rowIssues = new ArrayList<>();
            String batchKey = firstText(row.value("batchReferenceNo"), row.value("trackingNo"), row.value("containerNo"));
            if (!StringUtils.hasText(batchKey)) {
                rowIssues.add(issue("error", "batch_key_missing", "缺少批次号、物流单号或柜号，无法归组。", row.rowNumber, "batchReferenceNo"));
                batchKey = "row-" + row.rowNumber;
            }
            ImportPreviewBatchView batch = batches.computeIfAbsent(batchKey, key -> buildBatch(ownerUserId, row, key, issues));
            addRowBatchIssues(resolved.getAccessContext(), row, rowIssues);
            ImportPreviewLineView line = buildLine(row, rowIssues);
            if (rowIssues.stream().noneMatch(item -> "error".equals(item.getLevel()))) {
                validRowCount++;
            }
            line.setIssues(rowIssues);
            List<ImportPreviewLineView> nextLines = new ArrayList<>(batch.getLines());
            nextLines.add(line);
            batch.setLines(nextLines);
            issues.addAll(rowIssues);
        }

        int errorCount = (int) issues.stream().filter(issue -> "error".equals(issue.getLevel())).count();
        int warningCount = (int) issues.stream().filter(issue -> "warning".equals(issue.getLevel())).count();
        preview.setStatus(errorCount > 0 ? "has_errors" : "ready");
        preview.setValidRowCount(validRowCount);
        preview.setErrorCount(errorCount);
        preview.setWarningCount(warningCount);
        preview.setWillCreateBatchCount(batches.size());
        preview.setWillUpsertLineCount(rows.size());
        preview.setBatches(new ArrayList<>(batches.values()));
        preview.setIssues(issues);
        persistPreview(ownerUserId, operatorUserId, extension, preview);
        return preview;
    }

    @Transactional
    public ImportConfirmView confirm(ConfirmImportCommand command) {
        ConfirmImportCommand resolved = command == null ? new ConfirmImportCommand() : command;
        Long ownerUserId = requireOwnerUserId(resolved.getOwnerUserId());
        Long importBatchId = requirePositiveId(resolved.getImportBatchId(), "导入批次不存在。");
        ImportBatchRow row = mapper.selectImportBatchById(ownerUserId, importBatchId);
        if (row == null) {
            throw new IllegalArgumentException("导入批次不存在。");
        }
        ImportPreviewView preview = readPreview(row.getRawPreviewJson());
        if (preview.getErrorCount() != null && preview.getErrorCount() > 0) {
            throw new IllegalStateException("导入预览仍存在错误，不能确认导入。");
        }
        int importedBatchCount = 0;
        int importedLineCount = 0;
        int importedNodeCount = 0;
        for (ImportPreviewBatchView batch : preview.getBatches()) {
            requireImportBatchScope(resolved.getAccessContext(), batch);
            SaveBatchCommand batchCommand = new SaveBatchCommand();
            batchCommand.setOwnerUserId(ownerUserId);
            batchCommand.setOperatorUserId(resolved.getOperatorUserId());
            batchCommand.setStandardForwarderId(batch.getStandardForwarderId());
            batchCommand.setRawForwarderName(batch.getRawForwarderName());
            batchCommand.setTransportMode(batch.getTransportMode());
            batchCommand.setTargetStoreCode(batch.getTargetStoreCode());
            batchCommand.setTargetSiteCode(batch.getTargetSiteCode());
            batchCommand.setTargetWarehouseName(batch.getTargetWarehouseName());
            batchCommand.setDepartureDate(batch.getDepartureDate());
            batchCommand.setEtaDate(batch.getEtaDate());
            batchCommand.setTrackingNo(batch.getTrackingNo());
            batchCommand.setContainerNo(batch.getContainerNo());
            batchCommand.setBatchReferenceNo(batch.getBatchReferenceNo());
            batchCommand.setBatchStatus(defaultText(batch.getBatchStatus(), InTransitBatchStatus.IN_TRANSIT.code()));
            BatchRow existingBatch = findExistingBatch(ownerUserId, batch);
            if (existingBatch != null) {
                batchCommand.setBatchId(existingBatch.getId());
            }
            BatchView savedBatch = batchService.saveBatch(batchCommand);
            List<String> syncedBoxNos = syncedBoxNos(batch);
            List<String> syncedLineKeys = syncedLineKeys(batch);
            if (!syncedBoxNos.isEmpty() && !syncedLineKeys.isEmpty()) {
                batchService.reconcileSyncedDetails(
                        ownerUserId,
                        resolved.getOperatorUserId(),
                        savedBatch.getBatchId(),
                        syncedBoxNos,
                        syncedLineKeys
                );
            }
            importedBatchCount++;
            importedNodeCount += saveImportedMilestoneNodes(ownerUserId, resolved.getOperatorUserId(), savedBatch.getBatchId(), batch);
            for (ImportPreviewLineView line : batch.getLines()) {
                SaveLineCommand lineCommand = new SaveLineCommand();
                lineCommand.setOwnerUserId(ownerUserId);
                lineCommand.setOperatorUserId(resolved.getOperatorUserId());
                lineCommand.setBatchId(savedBatch.getBatchId());
                LineRow existingLine = findExistingLine(ownerUserId, savedBatch.getBatchId(), line);
                if (existingLine != null) {
                    lineCommand.setLineId(existingLine.getId());
                }
                lineCommand.setBoxNo(line.getBoxNo());
                lineCommand.setSku(line.getSku());
                lineCommand.setMsku(line.getMsku());
                lineCommand.setPsku(line.getPsku());
                lineCommand.setProductName(line.getProductName());
                lineCommand.setStoreCode(line.getStoreCode());
                lineCommand.setSiteCode(line.getSiteCode());
                lineCommand.setShippedQuantity(line.getShippedQuantity());
                lineCommand.setReceivedQuantity(line.getReceivedQuantity());
                lineCommand.setCartonCount(line.getCartonCount());
                lineCommand.setUnitsPerCarton(line.getUnitsPerCarton());
                lineCommand.setCartonWeightKg(line.getCartonWeightKg());
                lineCommand.setCartonVolumeCbm(line.getCartonVolumeCbm());
                batchService.saveLine(lineCommand);
                importedLineCount++;
            }
        }

        ImportConfirmView view = new ImportConfirmView();
        view.setImportBatchId(importBatchId);
        view.setStatus("imported");
        view.setImportedBatchCount(importedBatchCount);
        view.setImportedLineCount(importedLineCount);
        view.setImportedNodeCount(importedNodeCount);
        mapper.markImportBatchImported(ownerUserId, importBatchId, resolved.getOperatorUserId(), writeJson(Map.of(
                "importedBatchCount", importedBatchCount,
                "importedLineCount", importedLineCount,
                "importedNodeCount", importedNodeCount
        )));
        audit(
                ownerUserId,
                resolved.getOperatorUserId(),
                "import_confirmed",
                "import_batch",
                importBatchId,
                null,
                null,
                "历史在途导入已确认。",
                detail("importedBatchCount", importedBatchCount, "importedLineCount", importedLineCount, "importedNodeCount", importedNodeCount)
        );
        return view;
    }

    private BatchRow findExistingBatch(Long ownerUserId, ImportPreviewBatchView batch) {
        if (batch == null || !StringUtils.hasText(batch.getBatchReferenceNo())) {
            return null;
        }
        return mapper.selectBatchByReferenceNo(ownerUserId, batch.getBatchReferenceNo());
    }

    private LineRow findExistingLine(Long ownerUserId, Long batchId, ImportPreviewLineView line) {
        if (batchId == null || line == null || !StringUtils.hasText(line.getBoxNo()) || !StringUtils.hasText(line.getPsku())) {
            return null;
        }
        return mapper.selectLineByBoxNoAndPsku(ownerUserId, batchId, line.getBoxNo(), line.getPsku());
    }

    private List<String> syncedBoxNos(ImportPreviewBatchView batch) {
        LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
        for (ImportPreviewLineView line : batch.getLines()) {
            String boxNo = clean(line.getBoxNo());
            if (StringUtils.hasText(boxNo)) {
                values.put(boxNo, Boolean.TRUE);
            }
        }
        return new ArrayList<>(values.keySet());
    }

    private List<String> syncedLineKeys(ImportPreviewBatchView batch) {
        LinkedHashMap<String, Boolean> values = new LinkedHashMap<>();
        for (ImportPreviewLineView line : batch.getLines()) {
            String boxNo = clean(line.getBoxNo());
            String psku = clean(line.getPsku());
            if (StringUtils.hasText(boxNo) && StringUtils.hasText(psku)) {
                values.put(boxNo + "\n" + psku, Boolean.TRUE);
            }
        }
        return new ArrayList<>(values.keySet());
    }

    private ImportPreviewBatchView buildBatch(
            Long ownerUserId,
            ImportRowDraft row,
            String batchKey,
            List<ImportPreviewIssueView> issues
    ) {
        ImportPreviewBatchView batch = new ImportPreviewBatchView();
        batch.setBatchKey(batchKey);
        batch.setBatchReferenceNo(clean(row.value("batchReferenceNo")));
        batch.setBatchStatus(parseBatchStatus(row.value("batchStatus")));
        batch.setRawForwarderName(clean(row.value("rawForwarderName")));
        InTransitDestination destination = InTransitDestination.infer(
                row.value("targetStoreCode"),
                row.value("targetWarehouseName"),
                row.value("batchReferenceNo"),
                row.value("trackingNo"),
                row.value("containerNo")
        );
        batch.setTargetStoreCode(destination == null ? null : destination.code());
        batch.setTargetSiteCode(null);
        batch.setTargetWarehouseName(clean(row.value("targetWarehouseName")));
        LocalDate outboundAt = parseDate(row.value("outboundAt"));
        batch.setDepartureDate(firstDate(parseDate(row.value("departureDate")), outboundAt));
        batch.setEtaDate(parseDate(row.value("etaDate")));
        batch.setDomesticReceivedAt(parseDate(row.value("domesticReceivedAt")));
        batch.setOutboundAt(outboundAt);
        batch.setCustomsReleasedAt(parseDate(row.value("customsReleasedAt")));
        batch.setEtWarehouseReceivedAt(parseDate(row.value("etWarehouseReceivedAt")));
        batch.setTrackingNo(clean(row.value("trackingNo")));
        batch.setContainerNo(clean(row.value("containerNo")));

        String transportMode = parseTransportMode(row.value("transportMode"));
        batch.setTransportMode(transportMode);
        applyForwarder(ownerUserId, batch, row, issues);
        return batch;
    }

    private int saveImportedMilestoneNodes(
            Long ownerUserId,
            Long operatorUserId,
            Long batchId,
            ImportPreviewBatchView batch
    ) {
        int imported = 0;
        imported += saveImportedMilestoneNode(ownerUserId, operatorUserId, batchId, batch.getDomesticReceivedAt(),
                InTransitNodeStatus.HANDED_TO_FORWARDER.code(), "国内收货");
        imported += saveImportedMilestoneNode(ownerUserId, operatorUserId, batchId, batch.getOutboundAt(),
                InTransitNodeStatus.DEPARTED_ORIGIN.code(), "发往海外");
        imported += saveImportedMilestoneNode(ownerUserId, operatorUserId, batchId, batch.getCustomsReleasedAt(),
                InTransitNodeStatus.CUSTOMS_RELEASED.code(), "完成清关");
        imported += saveImportedMilestoneNode(ownerUserId, operatorUserId, batchId, batch.getEtWarehouseReceivedAt(),
                InTransitNodeStatus.IN_TRANSIT.code(), "ET海外仓入库");
        return imported;
    }

    private int saveImportedMilestoneNode(
            Long ownerUserId,
            Long operatorUserId,
            Long batchId,
            LocalDate date,
            String nodeStatus,
            String description
    ) {
        if (date == null) {
            return 0;
        }
        NodeRow existing = mapper.selectNodeByStatusDescriptionAndHappenedAt(
                ownerUserId,
                batchId,
                nodeStatus,
                date.atStartOfDay(),
                description
        );
        if (existing != null) {
            if (InTransitNodeStatus.isTerminalForBatchProjection(existing.getNodeStatus(), existing.getDescription())) {
                batchService.refreshBatchLatestNodeProjection(ownerUserId, batchId);
            }
            return 0;
        }
        SaveNodeCommand command = new SaveNodeCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setBatchId(batchId);
        command.setNodeStatus(nodeStatus);
        command.setNodeHappenedAt(date.atStartOfDay());
        command.setDescription(description);
        command.setOperatorName("导入");
        batchService.saveNode(command);
        return 1;
    }

    private void addRowBatchIssues(BusinessAccessContext context, ImportRowDraft row, List<ImportPreviewIssueView> rowIssues) {
        if (parseTransportMode(row.value("transportMode")) == null) {
            rowIssues.add(issue("error", "transport_mode_invalid", "运输方式只支持海运或空运。", row.rowNumber, "transportMode"));
        }
        if (StringUtils.hasText(row.value("targetStoreCode"))
                && InTransitDestination.infer(row.value("targetStoreCode")) == null) {
            rowIssues.add(issue("error", "destination_invalid", "目的地只支持 RUH 利雅得或 DB 迪拜。", row.rowNumber, "targetStoreCode"));
        }
        if (StringUtils.hasText(row.value("batchStatus")) && parseBatchStatus(row.value("batchStatus")) == null) {
            rowIssues.add(issue("error", "batch_status_invalid", "批次状态不支持自由文本。", row.rowNumber, "batchStatus"));
        }
        String lineStoreCode = resolveLineStoreCode(row);
        String lineSiteCode = firstText(row.value("siteCode"), row.value("targetSiteCode"));
        if (StringUtils.hasText(lineStoreCode) && !StringUtils.hasText(lineSiteCode)) {
            rowIssues.add(issue("error", "target_site_missing", "缺少目的站点。", row.rowNumber, "targetSiteCode"));
        }
        if (!StringUtils.hasText(row.value("targetWarehouseName"))) {
            rowIssues.add(issue("error", "target_warehouse_missing", "缺少目的仓。", row.rowNumber, "targetWarehouseName"));
        }
        if (context != null && StringUtils.hasText(lineStoreCode) && StringUtils.hasText(lineSiteCode)) {
            try {
                accessScopeService.requireWritableStoreSite(context, lineStoreCode, lineSiteCode);
            } catch (BusinessAccessDeniedException exception) {
                rowIssues.add(issue("error", "store_site_forbidden", exception.getMessage(), row.rowNumber, "storeCode"));
            }
        }
    }

    private ImportPreviewLineView buildLine(ImportRowDraft row, List<ImportPreviewIssueView> rowIssues) {
        ImportPreviewLineView line = new ImportPreviewLineView();
        line.setRowNumber(row.rowNumber);
        line.setBoxNo(clean(row.value("boxNo")));
        line.setSku(clean(row.value("sku")));
        line.setMsku(clean(row.value("msku")));
        line.setPsku(clean(row.value("psku")));
        line.setProductName(clean(row.value("productName")));
        line.setStoreCode(resolveLineStoreCode(row));
        line.setSiteCode(firstText(row.value("siteCode"), row.value("targetSiteCode")));
        line.setShippedQuantity(parseInteger(row.value("shippedQuantity")));
        line.setReceivedQuantity(parseInteger(row.value("receivedQuantity")));
        line.setCartonCount(parseInteger(row.value("cartonCount")));
        line.setUnitsPerCarton(parseInteger(row.value("unitsPerCarton")));
        line.setCartonWeightKg(parseDecimal(row.value("cartonWeightKg")));
        line.setCartonVolumeCbm(parseDecimal(row.value("cartonVolumeCbm")));
        if (!StringUtils.hasText(line.getBoxNo())) {
            rowIssues.add(issue("error", "box_no_missing", "缺少箱号。", row.rowNumber, "boxNo"));
        }
        if (!StringUtils.hasText(line.getPsku())) {
            rowIssues.add(issue("error", "psku_missing", "缺少 PSKU。", row.rowNumber, "psku"));
        }
        if (!StringUtils.hasText(row.value("shippedQuantity"))) {
            rowIssues.add(issue("error", "shipped_quantity_missing", "缺少发货数量。", row.rowNumber, "shippedQuantity"));
        }
        if (line.getShippedQuantity() != null && line.getShippedQuantity() < 0) {
            rowIssues.add(issue("error", "shipped_quantity_invalid", "发货数量不能为负数。", row.rowNumber, "shippedQuantity"));
        }
        if (line.getReceivedQuantity() != null && line.getReceivedQuantity() < 0) {
            rowIssues.add(issue("error", "received_quantity_invalid", "已入仓数量不能为负数。", row.rowNumber, "receivedQuantity"));
        }
        if (line.getShippedQuantity() != null
                && line.getReceivedQuantity() != null
                && line.getReceivedQuantity() > line.getShippedQuantity()) {
            rowIssues.add(issue("error", "received_quantity_exceeds_shipped", "已入仓数量不能大于发货数量。", row.rowNumber, "receivedQuantity"));
        }
        return line;
    }

    private void applyForwarder(
            Long ownerUserId,
            ImportPreviewBatchView batch,
            ImportRowDraft row,
            List<ImportPreviewIssueView> issues
    ) {
        if (!StringUtils.hasText(batch.getRawForwarderName())) {
            batch.setForwarderQualityStatus(InTransitQualityStatus.FORWARDER_UNMATCHED.code());
            issues.add(issue("warning", "forwarder_unmatched", "缺少原始货代，确认导入后会保留为待归一。", row.rowNumber, "rawForwarderName"));
            return;
        }
        ResolveForwarderCommand command = new ResolveForwarderCommand();
        command.setOwnerUserId(ownerUserId);
        command.setRawForwarderName(batch.getRawForwarderName());
        ForwarderResolveView forwarder = forwarderService.resolveForwarder(command);
        if (forwarder == null) {
            batch.setForwarderQualityStatus(InTransitQualityStatus.FORWARDER_UNMATCHED.code());
            issues.add(issue("warning", "forwarder_unmatched", "货代未归一，确认导入后会保留原始货代名称。", row.rowNumber, "rawForwarderName"));
            return;
        }
        batch.setStandardForwarderId(forwarder.getStandardForwarderId());
        batch.setStandardForwarderName(forwarder.getStandardForwarderName());
        batch.setForwarderQualityStatus(defaultText(forwarder.getQualityStatus(), InTransitQualityStatus.FORWARDER_UNMATCHED.code()));
        if (InTransitQualityStatus.FORWARDER_UNMATCHED.code().equals(batch.getForwarderQualityStatus())) {
            issues.add(issue("warning", "forwarder_unmatched", "货代未归一，确认导入后会保留原始货代名称。", row.rowNumber, "rawForwarderName"));
        }
    }

    private void persistPreview(Long ownerUserId, Long operatorUserId, String extension, ImportPreviewView preview) {
        ImportBatchRow row = new ImportBatchRow();
        row.setId(preview.getImportBatchId());
        row.setOwnerUserId(ownerUserId);
        row.setFileName(preview.getFileName());
        row.setSourceType(defaultText(extension, "unknown"));
        row.setStatus("previewed");
        row.setTotalRowCount(preview.getTotalRowCount());
        row.setValidRowCount(preview.getValidRowCount());
        row.setErrorCount(preview.getErrorCount());
        row.setWarningCount(preview.getWarningCount());
        row.setSummaryJson(writeJson(Map.of(
                "willCreateBatchCount", preview.getWillCreateBatchCount(),
                "willUpsertLineCount", preview.getWillUpsertLineCount()
        )));
        row.setRawPreviewJson(writeJson(preview));
        row.setCreatedBy(operatorUserId);
        row.setUpdatedBy(operatorUserId);
        mapper.insertImportBatch(row);
    }

    private List<ImportRowDraft> parseRows(String extension, byte[] content) {
        if ("xlsx".equals(extension) || "xls".equals(extension)) {
            return parseWorkbook(content);
        }
        if (!"csv".equals(extension)) {
            throw new IllegalArgumentException("导入文件只支持 CSV、XLS 或 XLSX。");
        }
        return parseCsv(content);
    }

    private List<ImportRowDraft> parseCsv(byte[] content) {
        String csv = new String(content, StandardCharsets.UTF_8);
        List<List<String>> records = csv.split("\\r?\\n", -1).length == 0
                ? Collections.emptyList()
                : Arrays.stream(csv.split("\\r?\\n", -1))
                .map(this::splitCsvLine)
                .collect(Collectors.toList());
        return toRows(records);
    }

    private List<ImportRowDraft> parseWorkbook(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<List<String>> records = new ArrayList<>();
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (sheet == null) {
                    continue;
                }
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    short lastCellNum = row.getLastCellNum();
                    for (int index = 0; index < lastCellNum; index++) {
                        Cell cell = row.getCell(index);
                        cells.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
                    }
                    if (cells.stream().anyMatch(StringUtils::hasText)) {
                        records.add(cells);
                    }
                }
                if (!records.isEmpty()) {
                    break;
                }
            }
            return toRows(records);
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Excel 文件读取失败。", exception);
        }
    }

    private List<ImportRowDraft> toRows(List<List<String>> records) {
        if (records == null || records.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> headers = records.get(0);
        Map<Integer, String> fieldByIndex = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            String field = HEADER_ALIASES.get(normalizeHeader(headers.get(index)));
            if (field != null) {
                fieldByIndex.put(index, field);
            }
        }
        if (fieldByIndex.isEmpty()) {
            throw new IllegalArgumentException("导入模板缺少可识别表头。");
        }
        List<ImportRowDraft> result = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> values = records.get(rowIndex);
            if (values.stream().noneMatch(StringUtils::hasText)) {
                continue;
            }
            Map<String, String> fields = new LinkedHashMap<>();
            for (Map.Entry<Integer, String> entry : fieldByIndex.entrySet()) {
                fields.put(entry.getValue(), entry.getKey() < values.size() ? clean(values.get(entry.getKey())) : null);
            }
            result.add(new ImportRowDraft(rowIndex + 1, fields));
        }
        return result;
    }

    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char ch = line.charAt(index);
            if (ch == '"') {
                boolean escaped = quoted && index + 1 < line.length() && line.charAt(index + 1) == '"';
                if (escaped) {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                cells.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private ImportPreviewView readPreview(String json) {
        try {
            return objectMapper.readValue(json, ImportPreviewView.class);
        } catch (IOException exception) {
            throw new IllegalStateException("导入预览快照读取失败。", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("导入预览快照写入失败。", exception);
        }
    }

    private void requireImportBatchScope(BusinessAccessContext context, ImportPreviewBatchView batch) {
        if (context == null || batch == null) {
            return;
        }
        for (ImportPreviewLineView line : batch.getLines()) {
            accessScopeService.requireWritableStoreSite(context, line.getStoreCode(), line.getSiteCode());
        }
    }

    private void audit(
            Long ownerUserId,
            Long operatorUserId,
            String operationType,
            String targetType,
            Long targetId,
            String storeCode,
            String siteCode,
            String summary,
            Map<String, Object> detail
    ) {
        InTransitOperationAuditService.AuditCommand command = new InTransitOperationAuditService.AuditCommand();
        command.setOwnerUserId(ownerUserId);
        command.setOperatorUserId(operatorUserId);
        command.setOperationType(operationType);
        command.setTargetType(targetType);
        command.setTargetId(targetId);
        command.setStoreCode(storeCode);
        command.setSiteCode(siteCode);
        command.setSummary(summary);
        command.setDetail(detail);
        auditService.record(command);
    }

    private static Map<String, Object> detail(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (pairs == null) {
            return result;
        }
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            result.put(String.valueOf(pairs[index]), pairs[index + 1]);
        }
        return result;
    }

    private static ImportPreviewIssueView issue(String level, String code, String message, Integer rowNumber, String field) {
        return new ImportPreviewIssueView(level, code, message, rowNumber, field);
    }

    private static String parseTransportMode(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if ("海运".equals(trimmed)) {
            return InTransitTransportMode.SEA.code();
        }
        if ("空运".equals(trimmed)) {
            return InTransitTransportMode.AIR.code();
        }
        try {
            return InTransitTransportMode.require(trimmed).code();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String parseBatchStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        Map<String, String> labels = Map.ofEntries(
                Map.entry("草稿", InTransitBatchStatus.DRAFT.code()),
                Map.entry("待发货", InTransitBatchStatus.PENDING_SHIPMENT.code()),
                Map.entry("已发货", InTransitBatchStatus.SHIPPED.code()),
                Map.entry("运输中", InTransitBatchStatus.IN_TRANSIT.code()),
                Map.entry("在途", InTransitBatchStatus.IN_TRANSIT.code()),
                Map.entry("未到货", InTransitBatchStatus.IN_TRANSIT.code()),
                Map.entry("国内仓未到货", InTransitBatchStatus.IN_TRANSIT.code()),
                Map.entry("未到货—待出库", InTransitBatchStatus.IN_TRANSIT.code()),
                Map.entry("未到货-待出库", InTransitBatchStatus.IN_TRANSIT.code()),
                Map.entry("清关中", InTransitBatchStatus.CUSTOMS_CLEARANCE.code()),
                Map.entry("派送中", InTransitBatchStatus.DELIVERING.code()),
                Map.entry("已入仓", InTransitBatchStatus.WAREHOUSE_RECEIVED.code()),
                Map.entry("海外仓签收", InTransitBatchStatus.WAREHOUSE_RECEIVED.code()),
                Map.entry("海外仓部分理货完成", InTransitBatchStatus.WAREHOUSE_RECEIVED.code()),
                Map.entry("已完成", InTransitBatchStatus.COMPLETED.code()),
                Map.entry("已到货", InTransitBatchStatus.COMPLETED.code()),
                Map.entry("已到货—全部出库", InTransitBatchStatus.COMPLETED.code()),
                Map.entry("已到货-全部出库", InTransitBatchStatus.COMPLETED.code()),
                Map.entry("海外仓理货完成", InTransitBatchStatus.COMPLETED.code()),
                Map.entry("异常", InTransitBatchStatus.EXCEPTION.code()),
                Map.entry("已取消", InTransitBatchStatus.CANCELLED.code()),
                Map.entry("取消", InTransitBatchStatus.CANCELLED.code())
        );
        String mapped = labels.get(trimmed);
        if (mapped != null) {
            return mapped;
        }
        try {
            return InTransitBatchStatus.require(trimmed).code();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().replace("/", "-");
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0) {
            normalized = normalized.substring(0, spaceIndex);
        }
        int dateTimeIndex = normalized.indexOf('T');
        if (dateTimeIndex > 0) {
            normalized = normalized.substring(0, dateTimeIndex);
        }
        String[] parts = normalized.split("-");
        if (parts.length == 3) {
            try {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                return LocalDate.of(year < 100 ? 2000 + year : year, month, day);
            } catch (RuntimeException ignored) {
                return LocalDate.parse(normalized);
            }
        }
        return LocalDate.parse(normalized);
    }

    private static Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new BigDecimal(value.trim()).intValue();
    }

    private static BigDecimal parseDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new BigDecimal(value.trim());
    }

    private static Long requireOwnerUserId(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("缺少老板账号范围。");
        }
        return ownerUserId;
    }

    private static Long requirePositiveId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static LocalDate firstDate(LocalDate... values) {
        for (LocalDate value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String extension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "csv";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private static String resolveLineStoreCode(ImportRowDraft row) {
        String explicit = firstText(row.value("storeCode"));
        if (StringUtils.hasText(explicit)) {
            return explicit;
        }
        String legacyTarget = row.value("targetStoreCode");
        if (!StringUtils.hasText(legacyTarget)) {
            return null;
        }
        String normalized = legacyTarget.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("STR") || normalized.startsWith("STORE")) {
            return legacyTarget.trim();
        }
        return null;
    }

    private static String normalizeHeader(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "")
                : "";
    }

    private static Map<String, String> buildHeaderAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "batchReferenceNo", "批次号", "batchReferenceNo", "batchNo", "batch");
        alias(aliases, "batchStatus", "批次状态", "当前状态", "batchStatus");
        alias(aliases, "rawForwarderName", "原始货代", "货代", "rawForwarderName", "forwarder");
        alias(aliases, "transportMode", "运输方式", "transportMode", "mode");
        alias(aliases, "targetStoreCode", "目的地编码", "目的地", "目标店铺", "targetStoreCode", "destination");
        alias(aliases, "storeCode", "店铺编码", "店铺", "storeCode", "shopCode");
        alias(aliases, "targetSiteCode", "目的站点", "目标站点", "targetSiteCode");
        alias(aliases, "siteCode", "站点", "店铺站点", "siteCode");
        alias(aliases, "targetWarehouseName", "目的仓", "目标仓", "仓库", "targetWarehouseName", "warehouse");
        alias(aliases, "departureDate", "发货日期", "departureDate");
        alias(aliases, "etaDate", "预计到仓", "到仓日期", "ETA", "etaDate");
        alias(aliases, "domesticReceivedAt", "国内收货日期", "国内收货", "domesticReceivedAt");
        alias(aliases, "outboundAt", "发往海外日期", "发往海外", "outboundAt");
        alias(aliases, "customsReleasedAt", "完成清关日期", "完成清关", "customsReleasedAt");
        alias(aliases, "etWarehouseReceivedAt", "ET海外仓入库日期", "ET海外仓入库", "warehouseReceivedAt", "etWarehouseReceivedAt");
        alias(aliases, "trackingNo", "物流单号", "trackingNo");
        alias(aliases, "boxNo", "箱号", "boxNo", "packageNo", "包裹号");
        alias(aliases, "containerNo", "柜号", "containerNo");
        alias(aliases, "sku", "SKU", "sku");
        alias(aliases, "msku", "MSKU", "msku");
        alias(aliases, "psku", "PSKU", "psku");
        alias(aliases, "productName", "商品名称", "productName");
        alias(aliases, "shippedQuantity", "发货数量", "shippedQuantity");
        alias(aliases, "receivedQuantity", "已入仓数量", "receivedQuantity");
        alias(aliases, "cartonCount", "箱数", "cartonCount");
        alias(aliases, "unitsPerCarton", "单箱数量", "unitsPerCarton");
        alias(aliases, "cartonWeightKg", "单箱重量", "cartonWeightKg");
        alias(aliases, "cartonVolumeCbm", "单箱体积", "cartonVolumeCbm");
        return aliases;
    }

    private static void alias(Map<String, String> aliases, String field, String... names) {
        for (String name : names) {
            aliases.put(normalizeHeader(name), field);
        }
    }

    private static final class ImportRowDraft {
        private final int rowNumber;
        private final Map<String, String> fields;

        private ImportRowDraft(int rowNumber, Map<String, String> fields) {
            this.rowNumber = rowNumber;
            this.fields = fields;
        }

        private String value(String field) {
            return fields.get(field);
        }
    }
}
