package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.DeliveryAccuracyAsnInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptAsnMatchRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InventorySyncScopeRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyImportResultView;
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
class OfficialWarehouseScheduledDeliveryAccuracyImportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private OfficialWarehouseStatisticsMapper mapper;

    private FakeFbnExportProvider provider;
    private OfficialWarehouseScheduledDeliveryAccuracyImportService service;

    @BeforeEach
    void setUp() {
        provider = new FakeFbnExportProvider(objectMapper);
        service = new OfficialWarehouseScheduledDeliveryAccuracyImportService(
                mapper,
                provider,
                new OfficialWarehouseScheduledDeliveryAccuracyCsvParser(),
                objectMapper
        );
    }

    @Test
    void importsCompletedScheduledDeliveryAccuracyExportAsAsnFacts() throws Exception {
        provider.status = ExportStatus.from(
                objectMapper,
                objectMapper.readTree("{\"exportCode\":\"EXP-SDA\",\"status\":\"COMPLETE\","
                        + "\"file_name\":\"fbn_inbound_scheduleddeliveryaccuracy.csv\","
                        + "\"download_url\":\"https://example.test/download?X-Goog-Signature=secret\","
                        + "\"result\":\"{\\\"total_rows\\\":2}\"}"),
                "EXP-SDA",
                objectMapper.createObjectNode()
        );
        provider.downloadedBytes = scheduledCsv().getBytes(StandardCharsets.UTF_8);

        InventorySyncScopeRecord scope = new InventorySyncScopeRecord();
        scope.ownerUserId = 307L;
        scope.logicalStoreId = 7001L;
        scope.storeCode = "STR108065-NSA";
        scope.siteCode = "SA";
        scope.projectCode = "PRJ108065";
        scope.partnerId = "108065";
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA")).thenReturn(scope);
        when(mapper.nextReportImportId()).thenReturn(623101L);
        when(mapper.nextReportRowId()).thenReturn(624101L, 624102L);
        when(mapper.nextDeliveryAccuracyAsnId()).thenReturn(626001L, 626002L);

        InboundReceiptAsnMatchRecord asnMatch = new InboundReceiptAsnMatchRecord();
        asnMatch.asnId = 500001L;
        asnMatch.noonAsnNr = "A04540991PN";
        when(mapper.findInboundReceiptAsnMatch(307L, "STR108065-NSA", "SA", "A04540991PN"))
                .thenReturn(asnMatch);

        ScheduledDeliveryAccuracyImportCommand command = new ScheduledDeliveryAccuracyImportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        ScheduledDeliveryAccuracyImportResultView result = service.importByExportCode(access(), "EXP-SDA", command);

        verify(mapper).deactivatePreviousScheduledDeliveryAccuracyImports(
                307L,
                "STR108065-NSA",
                "SA",
                "FBN_INBOUND_SCHEDULEDDELIVERYACCURACY",
                "EXP-SDA",
                307L
        );

        ArgumentCaptor<ReportImportInsertRecord> importCaptor =
                ArgumentCaptor.forClass(ReportImportInsertRecord.class);
        verify(mapper).insertReportImport(importCaptor.capture());
        ReportImportInsertRecord importRecord = importCaptor.getValue();
        assertThat(importRecord.summaryJson).contains("\"scheduledQuantity\":112");
        assertThat(importRecord.summaryJson).doesNotContain("X-Goog-Signature").doesNotContain("download");
        assertThat(importRecord.validRows).isEqualTo(2);
        assertThat(importRecord.warningRows).isEqualTo(2);

        ArgumentCaptor<ReportRowInsertRecord> rowCaptor = ArgumentCaptor.forClass(ReportRowInsertRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertReportRow(rowCaptor.capture());
        assertThat(rowCaptor.getAllValues()).extracting(row -> row.rowStatus).containsExactly("WARNING", "WARNING");

        ArgumentCaptor<DeliveryAccuracyAsnInsertRecord> factCaptor =
                ArgumentCaptor.forClass(DeliveryAccuracyAsnInsertRecord.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertDeliveryAccuracyAsn(factCaptor.capture());
        assertThat(factCaptor.getAllValues()).extracting(row -> row.matchStatus).containsExactly("MATCHED", "NO_LOCAL_ASN");
        assertThat(factCaptor.getAllValues()).extracting(row -> row.accuracyStatus).containsExactly("PUTAWAY_COMPLETED", "CANCELLED");
        assertThat(factCaptor.getAllValues()).extracting(row -> row.inboundQtyVariance).containsExactly(1, 2);

        assertThat(result.importId).isEqualTo("623101");
        assertThat(result.insertedAsnRows).isEqualTo(2);
        assertThat(result.scheduledQuantity).isEqualTo(112);
        assertThat(result.grnQuantity).isEqualTo(109);
        assertThat(result.inboundQuantityVariance).isEqualTo(3);
    }

    private static String scheduledCsv() {
        return "ASN Number,Warehouse,country,ASN Creation Date,Scheduled Date,Delivery Date,"
                + "Scheduled Quantity,GRN Quantity,Inbound Quantity Variance,Status,Inbound Utilization Efficiency %\n"
                + "A04540991PN,RUH01S,SA,2026-01-07 02:34:06 UTC,2026-01-08,2026-01-08,110,109,1,putaway_completed,99.49\n"
                + "A04544806PN,RUH07,SA,2026-01-07 10:28:36 UTC,2026-01-08,,2,0,2,cancelled,99.63\n";
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
        private ExportStatus status;
        private byte[] downloadedBytes;

        private FakeFbnExportProvider(ObjectMapper objectMapper) {
            super(objectMapper, null, null);
        }

        @Override
        public ExportStatus exportStatus(PullRequest request, String exportCode, boolean log) {
            return status;
        }

        @Override
        public byte[] download(PullRequest request, String downloadUrl) {
            return downloadedBytes;
        }
    }
}
