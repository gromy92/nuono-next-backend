package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.InTransitGoodsMapper;
import com.nuono.next.intransit.InTransitBatchCommands.ConfirmImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.PreviewImportCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.intransit.InTransitBatchRecords.BatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.BatchView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportBatchRow;
import com.nuono.next.intransit.InTransitBatchRecords.ImportConfirmView;
import com.nuono.next.intransit.InTransitBatchRecords.ImportPreviewView;
import com.nuono.next.intransit.InTransitBatchRecords.LineRow;
import com.nuono.next.intransit.InTransitForwarderCommands.ResolveForwarderCommand;
import com.nuono.next.intransit.InTransitForwarderRecords.ForwarderResolveView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitImportServiceTest {

    @Mock
    private InTransitGoodsMapper mapper;

    @Mock
    private InTransitForwarderService forwarderService;

    @Mock
    private InTransitBatchService batchService;

    @Mock
    private InTransitOperationAuditService auditService;

    @Mock
    private InTransitGoodsAccessScopeService accessScopeService;

    private InTransitImportService service;

    @BeforeEach
    void setUp() {
        service = new InTransitImportService(mapper, forwarderService, batchService, auditService, accessScopeService, new ObjectMapper());
    }

    @Test
    void shouldPreviewCsvWithoutWritingBatchesOrLines() {
        when(mapper.nextImportBatchId()).thenReturn(56001L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(
                matchedForwarder()
        );

        ImportPreviewView result = service.preview(command("历史在途.csv", validCsv()));

        assertEquals(56001L, result.getImportBatchId());
        assertEquals("ready", result.getStatus());
        assertEquals(2, result.getTotalRowCount());
        assertEquals(2, result.getValidRowCount());
        assertEquals(1, result.getWillCreateBatchCount());
        assertEquals(2, result.getWillUpsertLineCount());
        assertEquals(1, result.getBatches().size());
        assertEquals("BATCH-001", result.getBatches().get(0).getBatchReferenceNo());
        assertEquals("AIR", result.getBatches().get(0).getTransportMode());
        assertEquals("DB", result.getBatches().get(0).getTargetStoreCode());
        assertEquals(2, result.getBatches().get(0).getLines().size());
        assertEquals("STR245027-NAE", result.getBatches().get(0).getLines().get(0).getStoreCode());
        assertEquals("AE", result.getBatches().get(0).getLines().get(0).getSiteCode());
        assertEquals("SKU-AE-001", result.getBatches().get(0).getLines().get(0).getSku());
        assertFalse(result.getFieldNames().contains("purchaseOrderNo"));
        assertFalse(result.getFieldNames().contains("feeStatus"));
        verify(batchService, never()).saveBatch(any(SaveBatchCommand.class));
        verify(batchService, never()).saveLine(any(SaveLineCommand.class));
        verify(mapper).insertImportBatch(any(ImportBatchRow.class));
    }

    @Test
    void shouldMarkUnmatchedForwarderAsWarning() {
        when(mapper.nextImportBatchId()).thenReturn(56002L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(
                ForwarderResolveView.unmatched("历史货代X", "历史货代x")
        );

        ImportPreviewView result = service.preview(command("历史在途.csv", validCsv().replace("义特物流", "历史货代X")));

        assertEquals("ready", result.getStatus());
        assertEquals(0, result.getErrorCount());
        assertEquals(1, result.getWarningCount());
        assertEquals("forwarder_unmatched", result.getIssues().get(0).getCode());
        assertEquals("warning", result.getIssues().get(0).getLevel());
    }

    @Test
    void shouldMarkInvalidTransportAndRequiredFieldErrors() {
        when(mapper.nextImportBatchId()).thenReturn(56003L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());
        String csv = "批次号,原始货代,运输方式,目的地,店铺编码,站点,目的仓,箱号,PSKU,SKU,发货数量,已入仓数量\n"
                + "BATCH-ERR,义特物流,铁路,JED,STR245027-NAE,,FBN-DXB,BATCH-ERR-1,PSKU-AE-001,SKU-AE-001,,0\n"
                + "BATCH-ERR,义特物流,AIR,DB,STR245027-NAE,AE,,BATCH-ERR-2,PSKU-AE-002,SKU-AE-002,3,5\n";

        ImportPreviewView result = service.preview(command("错误在途.csv", csv));

        assertEquals("has_errors", result.getStatus());
        assertEquals(6, result.getErrorCount());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "transport_mode_invalid".equals(issue.getCode())));
        assertTrue(result.getIssues().stream().anyMatch(issue -> "destination_invalid".equals(issue.getCode())));
        assertTrue(result.getIssues().stream().anyMatch(issue -> "target_site_missing".equals(issue.getCode())));
        assertTrue(result.getIssues().stream().anyMatch(issue -> "shipped_quantity_missing".equals(issue.getCode())));
        assertTrue(result.getIssues().stream().anyMatch(issue -> "target_warehouse_missing".equals(issue.getCode())));
        assertTrue(result.getIssues().stream().anyMatch(issue -> "received_quantity_exceeds_shipped".equals(issue.getCode())));
    }

    @Test
    void shouldPreviewXlsxTemplate() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56004L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());

        ImportPreviewView result = service.preview(command("历史在途.xlsx", xlsxBytes()));

        assertEquals("ready", result.getStatus());
        assertEquals(1, result.getTotalRowCount());
        assertEquals(1, result.getWillCreateBatchCount());
        assertEquals("SKU-AE-003", result.getBatches().get(0).getLines().get(0).getSku());
    }

    @Test
    void shouldGenerateDownloadableXlsxTemplateWithTwoImportableSampleRows() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56009L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());

        byte[] template = service.buildTemplate();
        ImportPreviewView result = service.preview(command("在途商品导入模板.xlsx", template));

        assertTrue(template.length > 0);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(template))) {
            Row header = workbook.getSheetAt(0).getRow(0);
            assertEquals("目的地", header.getCell(4).getStringCellValue());
            assertEquals("店铺编码", header.getCell(5).getStringCellValue());
            assertEquals("站点", header.getCell(6).getStringCellValue());
            assertEquals("目的仓", header.getCell(7).getStringCellValue());
            assertEquals("预计到仓", header.getCell(9).getStringCellValue());
        }
        assertEquals("ready", result.getStatus());
        assertEquals(2, result.getTotalRowCount());
        assertEquals(2, result.getValidRowCount());
        assertEquals(2, result.getWillCreateBatchCount());
        assertEquals(2, result.getWillUpsertLineCount());
        assertEquals("TMP-INTRANSIT-SEA-001", result.getBatches().get(0).getBatchReferenceNo());
        assertEquals("SEA", result.getBatches().get(0).getTransportMode());
        assertEquals("DB", result.getBatches().get(0).getTargetStoreCode());
        assertEquals("PSKU-TEMPLATE-SEA-001", result.getBatches().get(0).getLines().get(0).getPsku());
        assertEquals("STR245027-NAE", result.getBatches().get(0).getLines().get(0).getStoreCode());
        assertEquals("TMP-INTRANSIT-AIR-001", result.getBatches().get(1).getBatchReferenceNo());
        assertEquals("AIR", result.getBatches().get(1).getTransportMode());
        assertEquals("RUH", result.getBatches().get(1).getTargetStoreCode());
        assertEquals("PSKU-TEMPLATE-AIR-001", result.getBatches().get(1).getLines().get(0).getPsku());
        assertEquals("STR245027-NSA", result.getBatches().get(1).getLines().get(0).getStoreCode());
        assertEquals("TMP-INTRANSIT-AIR-001-BOX-1", getProperty(result.getBatches().get(1).getLines().get(0), "boxNo"));
        assertFalse(result.getFieldNames().contains("purchaseOrderNo"));
        assertFalse(result.getFieldNames().contains("feeStatus"));
    }

    @Test
    void shouldPreviewQikeAirRowsAsOneBatchWithMultipleBoxes() {
        when(mapper.nextImportBatchId()).thenReturn(56010L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(
                ForwarderResolveView.unmatched("启客", "启客")
        );

        ImportPreviewView result = service.preview(command("启客空运在途.csv", qikeBoxCsv()));

        assertEquals("ready", result.getStatus());
        assertEquals(3, result.getTotalRowCount());
        assertEquals(1, result.getWillCreateBatchCount());
        assertEquals(3, result.getWillUpsertLineCount());
        assertEquals("XGGEUAE04029", result.getBatches().get(0).getBatchReferenceNo());
        assertEquals("DB", result.getBatches().get(0).getTargetStoreCode());
        assertEquals("XGGEUAE04029-1", getProperty(result.getBatches().get(0).getLines().get(0), "boxNo"));
        assertEquals("STR245027-NAE", result.getBatches().get(0).getLines().get(0).getStoreCode());
        assertEquals("SKU-QIKE-001", result.getBatches().get(0).getLines().get(0).getSku());
        assertEquals("XGGEUAE04029-1", getProperty(result.getBatches().get(0).getLines().get(1), "boxNo"));
        assertEquals("SKU-QIKE-002", result.getBatches().get(0).getLines().get(1).getSku());
        assertEquals("XGGEUAE04029-2", getProperty(result.getBatches().get(0).getLines().get(2), "boxNo"));
        assertEquals("SKU-QIKE-003", result.getBatches().get(0).getLines().get(2).getSku());
    }

    @Test
    void shouldPreviewAndConfirmBatchStatusFromImportFile() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56012L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());
        ImportPreviewView preview = service.preview(command("启客历史完成批次.csv", completedBatchCsv()));

        assertEquals("ready", preview.getStatus());
        assertEquals("completed", preview.getBatches().get(0).getBatchStatus());

        ImportBatchRow row = importBatchRow(56012L, preview);
        when(mapper.selectImportBatchById(10002L, 56012L)).thenReturn(row);
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53100L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56012L);
        service.confirm(command);

        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("completed", batchCaptor.getValue().getBatchStatus());
    }

    @Test
    void shouldRejectConfirmWhenPreviewHasErrors() throws Exception {
        ImportPreviewView preview = new ImportPreviewView();
        preview.setImportBatchId(56005L);
        preview.setStatus("has_errors");
        preview.setErrorCount(1);
        ImportBatchRow row = importBatchRow(56005L, preview);
        when(mapper.selectImportBatchById(10002L, 56005L)).thenReturn(row);

        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56005L);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.confirm(command));

        assertEquals("导入预览仍存在错误，不能确认导入。", exception.getMessage());
        verify(batchService, never()).saveBatch(any(SaveBatchCommand.class));
    }

    @Test
    void shouldConfirmImportByReplayingPreviewThroughBatchService() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56006L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());
        ImportPreviewView preview = service.preview(command("历史在途.csv", validCsv()));
        ImportBatchRow row = importBatchRow(56006L, preview);
        when(mapper.selectImportBatchById(10002L, 56006L)).thenReturn(row);
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53088L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56006L);
        ImportConfirmView result = service.confirm(command);

        assertEquals(56006L, result.getImportBatchId());
        assertEquals(1, result.getImportedBatchCount());
        assertEquals(2, result.getImportedLineCount());
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals(10002L, batchCaptor.getValue().getOwnerUserId());
        assertEquals("BATCH-001", batchCaptor.getValue().getBatchReferenceNo());
        assertEquals("DB", batchCaptor.getValue().getTargetStoreCode());
        ArgumentCaptor<SaveLineCommand> lineCaptor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService, org.mockito.Mockito.times(2)).saveLine(lineCaptor.capture());
        assertEquals(53088L, lineCaptor.getAllValues().get(0).getBatchId());
        assertEquals("SKU-AE-001", lineCaptor.getAllValues().get(0).getSku());
        assertEquals("STR245027-NAE", lineCaptor.getAllValues().get(0).getStoreCode());
        assertEquals("AE", lineCaptor.getAllValues().get(0).getSiteCode());
        verify(mapper).markImportBatchImported(any(Long.class), any(Long.class), any(Long.class), any(String.class));
        assertAudit("import_confirmed", "import_batch", 56006L);
    }

    @Test
    void shouldReplayBoxNoWhenConfirmingQikeImport() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56011L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(
                ForwarderResolveView.unmatched("启客", "启客")
        );
        ImportPreviewView preview = service.preview(command("启客空运在途.csv", qikeBoxCsv()));
        ImportBatchRow row = importBatchRow(56011L, preview);
        when(mapper.selectImportBatchById(10002L, 56011L)).thenReturn(row);
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53099L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56011L);
        ImportConfirmView result = service.confirm(command);

        assertEquals(1, result.getImportedBatchCount());
        assertEquals(3, result.getImportedLineCount());
        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals("XGGEUAE04029", batchCaptor.getValue().getBatchReferenceNo());
        assertEquals("DB", batchCaptor.getValue().getTargetStoreCode());
        ArgumentCaptor<SaveLineCommand> lineCaptor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService, org.mockito.Mockito.times(3)).saveLine(lineCaptor.capture());
        assertEquals("XGGEUAE04029-1", getProperty(lineCaptor.getAllValues().get(0), "boxNo"));
        assertEquals("STR245027-NAE", lineCaptor.getAllValues().get(0).getStoreCode());
        assertEquals("XGGEUAE04029-2", getProperty(lineCaptor.getAllValues().get(2), "boxNo"));
    }

    @Test
    void shouldReuseExistingBatchAndLineWhenConfirmingSameNaturalKeys() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56013L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(
                ForwarderResolveView.unmatched("启客", "启客")
        );
        ImportPreviewView preview = service.preview(command("启客空运在途.csv", qikeBoxCsv()));
        ImportBatchRow row = importBatchRow(56013L, preview);
        when(mapper.selectImportBatchById(10002L, 56013L)).thenReturn(row);
        when(mapper.selectBatchByReferenceNo(10002L, "XGGEUAE04029")).thenReturn(existingBatch(53101L, "XGGEUAE04029"));
        when(mapper.selectLineByBoxNoAndPsku(10002L, 53101L, "XGGEUAE04029-1", "PSKU-QIKE-001"))
                .thenReturn(existingLine(54101L, 53101L, "XGGEUAE04029-1", "SKU-QIKE-001", "PSKU-QIKE-001"));
        BatchView savedBatch = new BatchView();
        savedBatch.setBatchId(53101L);
        when(batchService.saveBatch(any(SaveBatchCommand.class))).thenReturn(savedBatch);

        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56013L);
        service.confirm(command);

        ArgumentCaptor<SaveBatchCommand> batchCaptor = ArgumentCaptor.forClass(SaveBatchCommand.class);
        verify(batchService).saveBatch(batchCaptor.capture());
        assertEquals(53101L, batchCaptor.getValue().getBatchId());
        verify(batchService).reconcileSyncedDetails(
                eq(10002L),
                eq(90001L),
                eq(53101L),
                eq(List.of("XGGEUAE04029-1", "XGGEUAE04029-2")),
                eq(List.of(
                        "XGGEUAE04029-1\nPSKU-QIKE-001",
                        "XGGEUAE04029-1\nPSKU-QIKE-002",
                        "XGGEUAE04029-2\nPSKU-QIKE-003"
                ))
        );
        ArgumentCaptor<SaveLineCommand> lineCaptor = ArgumentCaptor.forClass(SaveLineCommand.class);
        verify(batchService, org.mockito.Mockito.times(3)).saveLine(lineCaptor.capture());
        assertEquals(54101L, lineCaptor.getAllValues().get(0).getLineId());
        assertEquals("SKU-QIKE-001", lineCaptor.getAllValues().get(0).getSku());
        assertEquals("PSKU-QIKE-001", lineCaptor.getAllValues().get(0).getPsku());
    }

    @Test
    void shouldRejectImportRowsWithoutPskuBecausePskuIsProductKey() {
        when(mapper.nextImportBatchId()).thenReturn(56014L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());
        String csv = "批次号,原始货代,运输方式,目的地,店铺编码,站点,目的仓,箱号,SKU,发货数量,已入仓数量\n"
                + "BATCH-NO-PSKU,义特物流,AIR,DB,STR245027-NAE,AE,FBN-DXB,BATCH-NO-PSKU-1,SOURCE-SKU-001,3,0\n";

        ImportPreviewView result = service.preview(command("缺PSKU.csv", csv));

        assertEquals("has_errors", result.getStatus());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "psku_missing".equals(issue.getCode())));
    }

    @Test
    void shouldRejectImportRowsWithoutBoxNoBecauseLineNaturalKeyIncludesBox() {
        when(mapper.nextImportBatchId()).thenReturn(56015L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());
        String csv = "批次号,原始货代,运输方式,目的地,店铺编码,站点,目的仓,PSKU,SKU,发货数量,已入仓数量\n"
                + "BATCH-NO-BOX,义特物流,AIR,DB,STR245027-NAE,AE,FBN-DXB,PSKU-AE-001,SOURCE-SKU-001,3,0\n";

        ImportPreviewView result = service.preview(command("缺箱号.csv", csv));

        assertEquals("has_errors", result.getStatus());
        assertTrue(result.getIssues().stream().anyMatch(issue -> "box_no_missing".equals(issue.getCode())));
    }

    @Test
    void shouldRejectConfirmWhenPreviewBatchIsOutsideCurrentAccessScope() throws Exception {
        when(mapper.nextImportBatchId()).thenReturn(56008L);
        when(forwarderService.resolveForwarder(any(ResolveForwarderCommand.class))).thenReturn(matchedForwarder());
        ImportPreviewView preview = service.preview(command("历史在途.csv", validCsv()));
        ImportBatchRow row = importBatchRow(56008L, preview);
        when(mapper.selectImportBatchById(10002L, 56008L)).thenReturn(row);
        BusinessAccessContext context = context();
        doThrow(new BusinessAccessDeniedException("当前账号不能操作该店铺。"))
                .when(accessScopeService)
                .requireWritableStoreSite(eq(context), eq("STR245027-NAE"), eq("AE"));

        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56008L);
        command.setAccessContext(context);

        BusinessAccessDeniedException exception = assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.confirm(command)
        );

        assertEquals("当前账号不能操作该店铺。", exception.getMessage());
        verify(batchService, never()).saveBatch(any(SaveBatchCommand.class));
    }

    @Test
    void shouldRejectConfirmFromAnotherOwnerScope() {
        when(mapper.selectImportBatchById(10002L, 56007L)).thenReturn(null);
        ConfirmImportCommand command = new ConfirmImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setImportBatchId(56007L);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> service.confirm(command));

        assertEquals("导入批次不存在。", exception.getMessage());
    }

    private PreviewImportCommand command(String fileName, String content) {
        return command(fileName, content.getBytes(StandardCharsets.UTF_8));
    }

    private PreviewImportCommand command(String fileName, byte[] content) {
        PreviewImportCommand command = new PreviewImportCommand();
        command.setOwnerUserId(10002L);
        command.setOperatorUserId(90001L);
        command.setFileName(fileName);
        command.setContent(content);
        return command;
    }

    private String validCsv() {
        return "批次号,原始货代,运输方式,目的地,店铺编码,站点,目的仓,发货日期,预计到仓,物流单号,箱号,柜号,PSKU,SKU,MSKU,商品名称,发货数量,已入仓数量,箱数,单箱数量,单箱重量,单箱体积\n"
                + "BATCH-001,义特物流,空运,DB,STR245027-NAE,AE,FBN-DXB,2026-05-20,2026-06-08,TRK-001,BATCH-001-BOX-1,CONT-001,PSKU-001,SKU-AE-001,MSKU-001,折叠手机壳,10,4,2,5,12.5,0.25\n"
                + "BATCH-001,义特物流,AIR,DB,STR245027-NAE,AE,FBN-DXB,2026-05-20,2026-06-08,TRK-001,BATCH-001-BOX-1,CONT-001,PSKU-002,SKU-AE-002,MSKU-002,折叠手机膜,8,0,1,8,3.2,0.08\n";
    }

    private String qikeBoxCsv() {
        return "批次号,箱号,原始货代,运输方式,目标店铺,目标站点,目标仓,物流单号,PSKU,SKU,商品名称,发货数量,已入仓数量\n"
                + "XGGEUAE04029,XGGEUAE04029-1,启客,空运,STR245027-NAE,AE,FBN-DXB,XGGEUAE04029-1,PSKU-QIKE-001,SKU-QIKE-001,启客商品1,20,0\n"
                + "XGGEUAE04029,XGGEUAE04029-1,启客,空运,STR245027-NAE,AE,FBN-DXB,XGGEUAE04029-1,PSKU-QIKE-002,SKU-QIKE-002,启客商品2,5,0\n"
                + "XGGEUAE04029,XGGEUAE04029-2,启客,空运,STR245027-NAE,AE,FBN-DXB,XGGEUAE04029-2,PSKU-QIKE-003,SKU-QIKE-003,启客商品3,10,0\n";
    }

    private String completedBatchCsv() {
        return "批次号,批次状态,箱号,原始货代,运输方式,目标店铺,目标站点,目标仓,物流单号,PSKU,SKU,商品名称,发货数量,已入仓数量\n"
                + "XGGEKSA04074,已完成,XGGEKSA04074-1,义特物流,空运,STR245027-NAE,AE,FBN-DXB,XGGEKSA04074-1,PSKU-DONE-001,SKU-DONE-001,历史已完成商品,10,10\n";
    }

    private byte[] xlsxBytes() throws Exception {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("在途");
            Row header = sheet.createRow(0);
            String[] headers = {"批次号", "原始货代", "运输方式", "目的地", "店铺编码", "站点", "目的仓", "箱号", "PSKU", "SKU", "发货数量", "已入仓数量"};
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            Row row = sheet.createRow(1);
            String[] values = {"BATCH-XLSX", "义特物流", "SEA", "DB", "STR245027-NAE", "AE", "FBN-DXB", "BATCH-XLSX-BOX-1", "PSKU-AE-003", "SKU-AE-003", "5", "0"};
            for (int index = 0; index < values.length; index++) {
                row.createCell(index).setCellValue(values[index]);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private ForwarderResolveView matchedForwarder() {
        ForwarderResolveView view = new ForwarderResolveView();
        view.setStandardForwarderId(51001L);
        view.setStandardForwarderCode("YITE");
        view.setStandardForwarderName("义特");
        view.setRawForwarderName("义特");
        view.setNormalizedRawForwarderName("义特物流");
        view.setQualityStatus("forwarder_matched");
        return view;
    }

    private ImportBatchRow importBatchRow(Long id, ImportPreviewView preview) throws Exception {
        ImportBatchRow row = new ImportBatchRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setFileName("历史在途.csv");
        row.setStatus("previewed");
        row.setRawPreviewJson(new ObjectMapper().findAndRegisterModules().writeValueAsString(preview));
        return row;
    }

    private BatchRow existingBatch(Long id, String batchReferenceNo) {
        BatchRow row = new BatchRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchReferenceNo(batchReferenceNo);
        row.setBatchStatus("in_transit");
        row.setRawForwarderName("启客");
        return row;
    }

    private LineRow existingLine(Long id, Long batchId, String boxNo, String sku, String psku) {
        LineRow row = new LineRow();
        row.setId(id);
        row.setOwnerUserId(10002L);
        row.setBatchId(batchId);
        row.setBoxNo(boxNo);
        row.setSku(sku);
        row.setPsku(psku);
        return row;
    }

    private BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR245027-NAE"))
                .build();
    }

    private void assertAudit(String operationType, String targetType, Long targetId) {
        ArgumentCaptor<InTransitOperationAuditService.AuditCommand> auditCaptor =
                ArgumentCaptor.forClass(InTransitOperationAuditService.AuditCommand.class);
        verify(auditService).record(auditCaptor.capture());
        assertEquals(operationType, auditCaptor.getValue().getOperationType());
        assertEquals(targetType, auditCaptor.getValue().getTargetType());
        assertEquals(targetId, auditCaptor.getValue().getTargetId());
        assertEquals(10002L, auditCaptor.getValue().getOwnerUserId());
        assertEquals(90001L, auditCaptor.getValue().getOperatorUserId());
    }

    private Object getProperty(Object target, String name) {
        try {
            Method method = target.getClass().getMethod("get" + Character.toUpperCase(name.charAt(0)) + name.substring(1));
            return method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Missing readable property: " + name, exception);
        }
    }
}
