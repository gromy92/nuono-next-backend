package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
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

    private static String receivedCsv() {
        return "partner_sku,sku,po_nr,pbarcode_canonical,storage_type_code,volume,brand,product_title,asn,"
                + "partner_warehouse,noon_warehouse,country_code,qty_expected,received_qty,qc_failed_qty,"
                + "unidentified_qty,qc_failed_reason,asn_created_at,asn_schedule_date,asn_completed_at\n"
                + "PAPERSAYSB105N1,Z0B8C025C4C884FD10BE6Z-1,,6287053004607,standard,0.01,Papersay,"
                + "\"A4 file bag\",A05508658PN,-,RUH01S,sa,1,1,0,0,-,2026-06-11,2026-06-11,2026-06-13\n"
                + "PAPERSAYSB042,Z9DDECF61092EFCE742E9Z-1,,6287053004508,standard,0.02,Papersay,"
                + "Tape,A05508658PN,-,RUH01S,sa,3,2,0,1,missing,2026-06-11,2026-06-11,2026-06-13\n";
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

    private static class FakeFbnExportProvider extends OfficialWarehouseFbnExportProvider {
        private final java.util.List<String> statusRequests = new java.util.ArrayList<>();
        private final java.util.List<String> downloadRequests = new java.util.ArrayList<>();
        private ExportStatus status;
        private byte[] downloadedBytes;

        private FakeFbnExportProvider(ObjectMapper objectMapper) {
            super(objectMapper, null, null);
        }

        @Override
        public ExportStatus exportStatus(PullRequest request, String exportCode, boolean log) {
            statusRequests.add(request.ownerUserId + ":" + request.storeCode + ":" + request.siteCode + ":" + exportCode + ":" + log);
            return status;
        }

        @Override
        public byte[] download(PullRequest request, String downloadUrl) {
            downloadRequests.add(request.ownerUserId + ":" + request.storeCode + ":" + request.siteCode);
            return downloadedBytes;
        }
    }
}
