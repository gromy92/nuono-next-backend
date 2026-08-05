package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.access;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.command;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.minimalReceivedHeader;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.minimalReceivedRow;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.receivedCsv;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.receivedHeader;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.scope;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.sha256;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.FakeFbnExportProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnLineMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventoryLineProductMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseFbnReceivedReportImportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OfficialWarehouseStatisticsMapper mapper;

    private FakeFbnExportProvider provider;
    private OfficialWarehouseFbnReceivedReportImportService service;

    @BeforeEach
    void setUp() {
        provider = new FakeFbnExportProvider(objectMapper);
        service = new OfficialWarehouseFbnReceivedReportImportService(
                mapper,
                provider,
                new OfficialWarehouseFbnReceivedReportCsvParser(),
                objectMapper
        );
    }

    @Test
    void importsCompletedFbnReceivedExportWithoutPersistingSignedDownloadUrl() throws Exception {
        provider.status = ExportStatus.from(
                objectMapper,
                objectMapper.readTree("{\"exportCode\":\"EXP4URWS7NYN\",\"status\":\"COMPLETE\","
                        + "\"file_name\":\"fbn_inbound_fbnreceivedreport.csv\","
                        + "\"download_url\":\"https://storage.googleapis.com/private?Signature=secret\","
                        + "\"result\":\"{\\\"total_rows\\\":2}\"}"),
                "EXP4URWS7NYN",
                objectMapper.createObjectNode()
        );
        provider.downloadedBytes = receivedCsv().getBytes(StandardCharsets.UTF_8);

        InventorySyncScopeRecord scope = new InventorySyncScopeRecord();
        scope.ownerUserId = 307L;
        scope.logicalStoreId = 7001L;
        scope.storeCode = "STR108065-NSA";
        scope.siteCode = "SA";
        scope.projectCode = "PRJ108065";
        scope.partnerId = "108065";
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA")).thenReturn(scope);
        when(mapper.nextReportImportId()).thenReturn(623001L);
        when(mapper.nextReportRowId()).thenReturn(624001L, 624002L);
        when(mapper.nextInboundReceiptLineId()).thenReturn(625001L, 625002L);

        InboundReceiptAsnMatchRecord asnMatch = new InboundReceiptAsnMatchRecord();
        asnMatch.asnId = 500001L;
        asnMatch.localAsnNo = "ASN-LOCAL";
        asnMatch.noonAsnNr = "A05508658PN";
        when(mapper.findInboundReceiptAsnMatch(307L, "STR108065-NSA", "SA", "A05508658PN"))
                .thenReturn(asnMatch);

        InboundReceiptAsnLineMatchRecord lineMatch = new InboundReceiptAsnLineMatchRecord();
        lineMatch.asnLineId = 510001L;
        lineMatch.productMasterId = 7002L;
        lineMatch.productVariantId = 8002L;
        lineMatch.productSiteOfferId = 9002L;
        lineMatch.partnerSku = "PAPERSAYSB105N1";
        lineMatch.pskuCode = "Z0B8C025C4C884FD10BE6Z-1";
        lineMatch.noonSku = "Z0B8C025C4C884FD10BE6Z-1";
        when(mapper.findInboundReceiptAsnLineMatch(
                500001L,
                307L,
                "STR108065-NSA",
                "SA",
                "Z0B8C025C4C884FD10BE6Z-1",
                "PAPERSAYSB105N1"
        )).thenReturn(lineMatch);

        InventoryLineProductMatchRecord productMatch = new InventoryLineProductMatchRecord();
        productMatch.productMasterId = 7002L;
        productMatch.productVariantId = 8002L;
        productMatch.productSiteOfferId = 9002L;
        productMatch.partnerSku = "PAPERSAYSB105N1";
        productMatch.pskuCode = "Z0B8C025C4C884FD10BE6Z-1";
        productMatch.noonSku = "Z0B8C025C4C884FD10BE6Z-1";
        when(mapper.findInventoryLineProductMatch(
                eq(307L),
                eq("STR108065-NSA"),
                eq("SA"),
                any(),
                any()
        )).thenReturn(productMatch);

        FbnReceivedImportCommand command = new FbnReceivedImportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        FbnReceivedImportResultView result = service.importByExportCode(access(), "EXP4URWS7NYN", command);

        assertThat(provider.statusRequests).containsExactly("307:STR108065-NSA:SA:EXP4URWS7NYN:false");
        assertThat(provider.downloadRequests).containsExactly("307:STR108065-NSA:SA");
        verify(mapper).deactivatePreviousFbnReceivedReportImports(
                307L,
                "STR108065-NSA",
                "SA",
                "FBN_INBOUND_FBNRECEIVEDREPORT",
                "EXP4URWS7NYN",
                307L
        );

        ArgumentCaptor<ReportImportInsertRecord> importCaptor =
                ArgumentCaptor.forClass(ReportImportInsertRecord.class);
        verify(mapper).insertReportImport(importCaptor.capture());
        ReportImportInsertRecord importRecord = importCaptor.getValue();
        assertThat(importRecord.id).isEqualTo(623001L);
        assertThat(importRecord.reportType).isEqualTo("FBN_INBOUND_FBNRECEIVEDREPORT");
        assertThat(importRecord.sourceExportCode).isEqualTo("EXP4URWS7NYN");
        assertThat(importRecord.totalRows).isEqualTo(2);
        assertThat(importRecord.validRows).isEqualTo(2);
        assertThat(importRecord.warningRows).isEqualTo(1);
        assertThat(importRecord.summaryJson).contains("\"providerTotalRows\":2");
        assertThat(importRecord.summaryJson).doesNotContain("Signature").doesNotContain("storage.googleapis.com");

        ArgumentCaptor<ReportRowInsertRecord> rowCaptor = ArgumentCaptor.forClass(ReportRowInsertRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertReportRow(rowCaptor.capture());
        assertThat(rowCaptor.getAllValues())
                .extracting(row -> row.rowStatus)
                .containsExactly("VALID", "WARNING");
        assertThat(rowCaptor.getAllValues().get(0).rawRowJson).contains("PAPERSAYSB105N1");

        ArgumentCaptor<InboundReceiptLineInsertRecord> lineCaptor =
                ArgumentCaptor.forClass(InboundReceiptLineInsertRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertInboundReceiptLine(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues())
                .extracting(line -> line.receiptStatus)
                .containsExactly("NORMAL", "UNIDENTIFIED");
        assertThat(lineCaptor.getAllValues())
                .extracting(line -> line.matchStatus)
                .containsExactly("MATCHED", "LINE_UNMATCHED");
        assertThat(lineCaptor.getAllValues().get(0).asnLineId).isEqualTo(510001L);
        assertThat(lineCaptor.getAllValues().get(0).rawPayloadJson).doesNotContain("Signature");

        assertThat(result.importId).isEqualTo("623001");
        assertThat(result.exportCode).isEqualTo("EXP4URWS7NYN");
        assertThat(result.insertedReceiptLines).isEqualTo(2);
        assertThat(result.warningRows).isEqualTo(1);
        assertThat(result.fileSha256).hasSize(64);
    }

    @Test
    void importsVerifiedDownloadedBytesWithoutCallingTheProvider() throws Exception {
        byte[] content = receivedHeader().getBytes(StandardCharsets.UTF_8);
        InventorySyncScopeRecord scope = new InventorySyncScopeRecord();
        scope.ownerUserId = 307L;
        scope.logicalStoreId = 7001L;
        scope.storeCode = "STR108065-NSA";
        scope.siteCode = "SA";
        scope.projectCode = "PRJ108065";
        scope.partnerId = "108065";
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA")).thenReturn(scope);
        when(mapper.nextReportImportId()).thenReturn(623101L);
        FbnReceivedImportCommand command = new FbnReceivedImportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        FbnReceivedImportResultView result = service.importDownloaded(
                access(),
                "EXP-DURABLE-1",
                command,
                content,
                "durable.csv",
                sha256(content)
        );

        assertThat(provider.statusRequests).isEmpty();
        assertThat(provider.downloadRequests).isEmpty();
        assertThat(result.insertedReceiptLines).isZero();
        verify(mapper).insertReportImport(any(ReportImportInsertRecord.class));
    }

    @Test
    void keepsTheFirstValidatedBusinessKeyAndSkipsBadAndLaterConflictingRows() throws Exception {
        completedStatus("EXP-ROW-SEMANTICS", 4);
        provider.downloadedBytes = (minimalReceivedHeader()
                + minimalReceivedRow("P1", "Z1", "A1", "-1", "1", "0", "0", "2026-08-01")
                + minimalReceivedRow(" p1 ", " z1 ", " a1 ", "1", "1", "0", "0", "2026-08-01")
                + minimalReceivedRow("P1", "Z1", "A1", "2", "2", "0", "0", "2026-08-01")
                + minimalReceivedRow("P2", "Z2", "A2", "3", "3", "0", "0", "2026-08-01"))
                .getBytes(StandardCharsets.UTF_8);
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA")).thenReturn(scope());
        when(mapper.nextReportImportId()).thenReturn(623201L);
        when(mapper.nextReportRowId()).thenReturn(624201L, 624202L);
        when(mapper.nextInboundReceiptLineId()).thenReturn(625201L, 625202L);

        FbnReceivedImportResultView result = service.importByExportCode(
                access(),
                "EXP-ROW-SEMANTICS",
                command()
        );

        assertThat(result.totalRows).isEqualTo(3);
        assertThat(result.validRows).isEqualTo(2);
        assertThat(result.insertedReceiptLines).isEqualTo(2);

        ArgumentCaptor<ReportRowInsertRecord> rowCaptor = ArgumentCaptor.forClass(ReportRowInsertRecord.class);
        verify(mapper, times(2)).insertReportRow(rowCaptor.capture());
        assertThat(rowCaptor.getAllValues()).extracting(row -> row.rowNo).containsExactly(3, 5);
        assertThat(rowCaptor.getAllValues().get(0).businessKey).contains("A1", "P1", "Z1");

        ArgumentCaptor<InboundReceiptLineInsertRecord> lineCaptor =
                ArgumentCaptor.forClass(InboundReceiptLineInsertRecord.class);
        verify(mapper, times(2)).insertInboundReceiptLine(lineCaptor.capture());
        assertThat(lineCaptor.getAllValues()).extracting(line -> line.qtyExpected).containsExactly(1, 3);
    }

    @Test
    void rejectsAWholeFileBeforeAnyFactWriteWhenALaterRowHasAnInvalidDate() throws Exception {
        completedStatus("EXP-INVALID-DATE", 2);
        provider.downloadedBytes = (minimalReceivedHeader()
                + minimalReceivedRow("P1", "Z1", "A1", "1", "1", "0", "0", "2026-08-01")
                + minimalReceivedRow("P2", "Z2", "A2", "1", "1", "0", "0", "2026-02-30"))
                .getBytes(StandardCharsets.UTF_8);
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA")).thenReturn(scope());

        assertThatThrownBy(() -> service.importByExportCode(
                access(),
                "EXP-INVALID-DATE",
                command()
        )).isInstanceOf(IllegalArgumentException.class);

        verify(mapper, never()).nextReportImportId();
        verify(mapper, never()).deactivatePreviousFbnReceivedReportImports(
                307L,
                "STR108065-NSA",
                "SA",
                "FBN_INBOUND_FBNRECEIVEDREPORT",
                "EXP-INVALID-DATE",
                307L
        );
        verify(mapper, never()).insertReportRow(any(ReportRowInsertRecord.class));
        verify(mapper, never()).insertInboundReceiptLine(any(InboundReceiptLineInsertRecord.class));
        verify(mapper, never()).insertReportImport(any(ReportImportInsertRecord.class));
    }

    private void completedStatus(String exportCode, int totalRows) throws Exception {
        provider.status = ExportStatus.from(
                objectMapper,
                objectMapper.readTree("{\"exportCode\":\"" + exportCode + "\",\"status\":\"COMPLETE\","
                        + "\"download_url\":\"https://storage.googleapis.com/private\","
                        + "\"result\":\"{\\\"total_rows\\\":" + totalRows + "}\"}"),
                exportCode,
                objectMapper.createObjectNode()
        );
    }

}
