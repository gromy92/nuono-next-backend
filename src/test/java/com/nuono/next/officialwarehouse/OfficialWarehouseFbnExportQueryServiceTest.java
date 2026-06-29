package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportItem;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportListPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OfficialWarehouseFbnExportQueryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FakeFbnExportProvider provider = new FakeFbnExportProvider(objectMapper);
    private final OfficialWarehouseFbnExportQueryService service = new OfficialWarehouseFbnExportQueryService(provider);

    @Test
    void listExportsUsesBusinessOwnerScopeAndReturnsSanitizedRows() {
        provider.listPage = new ExportListPage(
                1,
                20,
                false,
                List.of(ExportItem.from(objectMapper.createObjectNode()
                        .put("exportCode", "EXP4URWS7NYN")
                        .put("status_code", "COMPLETE")
                        .put("exportCategoryCode", "fbn_inbound_fbnreceivedreport")
                        .put("fileName", "fbn-received.csv")
                        .put("download_url", "https://download.test/fbn-received.csv"))),
                objectMapper.createObjectNode().put("cookie", "must-not-leak")
        );

        OfficialWarehouseStatisticsViews.FbnExportListView result = service.listExports(
                access(),
                "STR108065-NSA",
                "SA",
                1,
                20
        );

        assertThat(provider.listRequests).hasSize(1);
        assertThat(provider.listRequests.get(0).ownerUserId).isEqualTo(307L);
        assertThat(provider.listRequests.get(0).storeCode).isEqualTo("STR108065-NSA");
        assertThat(provider.listRequests.get(0).siteCode).isEqualTo("SA");
        assertThat(result.items).hasSize(1);
        assertThat(result.items.get(0).exportCode).isEqualTo("EXP4URWS7NYN");
        assertThat(result.items.get(0).reportType).isEqualTo("fbn_inbound_fbnreceivedreport");
        assertThat(result.items.get(0).downloadUrl).isEqualTo("https://download.test/fbn-received.csv");
    }

    @Test
    void exportStatusUsesRequestedExportCodeAndReturnsDownloadMetadataOnly() {
        provider.status = ExportStatus.from(
                objectMapper,
                objectMapper.createObjectNode()
                        .put("export_code", "EXP4URWS7NYN")
                        .put("status_code", "COMPLETE")
                        .put("download_url", "https://download.test/fbn-received.csv")
                        .put("file_name", "fbn-received.csv")
                        .set("result", objectMapper.createObjectNode().put("total_rows", 177)),
                "EXP4URWS7NYN",
                objectMapper.createObjectNode().put("authorization", "must-not-leak")
        );

        OfficialWarehouseStatisticsViews.FbnExportStatusView result = service.exportStatus(
                access(),
                "STR108065-NSA",
                "SA",
                "EXP4URWS7NYN",
                true
        );

        assertThat(provider.statusRequests).hasSize(1);
        assertThat(provider.statusRequests.get(0).ownerUserId).isEqualTo(307L);
        assertThat(provider.statusExportCodes).containsExactly("EXP4URWS7NYN");
        assertThat(provider.statusLogs).containsExactly(true);
        assertThat(result.exportCode).isEqualTo("EXP4URWS7NYN");
        assertThat(result.status).isEqualTo("COMPLETE");
        assertThat(result.fileName).isEqualTo("fbn-received.csv");
        assertThat(result.downloadUrl).isEqualTo("https://download.test/fbn-received.csv");
        assertThat(result.totalRows).isEqualTo(177);
    }

    @Test
    void createExportUsesBusinessOwnerScopeAndAllowlistedReportType() {
        provider.createResult = new OfficialWarehouseFbnExportProvider.CreateExportResult(
                "EXP-SDA",
                "PENDING",
                "fbn_inbound_scheduleddeliveryaccuracy",
                objectMapper.createObjectNode().put("cookie", "must-not-leak")
        );
        FbnExportCreateCommand command = new FbnExportCreateCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        command.exportCategoryCode = "fbn_inbound_scheduleddeliveryaccuracy";
        command.fromDate = "2026-06-20";
        command.toDate = "2026-06-20";

        OfficialWarehouseStatisticsViews.FbnExportCreateView result = service.createExport(access(), command);

        assertThat(provider.createRequests).hasSize(1);
        assertThat(provider.createRequests.get(0).ownerUserId).isEqualTo(307L);
        assertThat(provider.createRequests.get(0).storeCode).isEqualTo("STR108065-NSA");
        assertThat(provider.createRequests.get(0).siteCode).isEqualTo("SA");
        assertThat(provider.createExportRequests.get(0).exportCategoryCode)
                .isEqualTo("fbn_inbound_scheduleddeliveryaccuracy");
        assertThat(provider.createExportRequests.get(0).fromDate).isEqualTo("2026-06-20");
        assertThat(provider.createExportRequests.get(0).toDate).isEqualTo("2026-06-20");
        assertThat(result.exportCode).isEqualTo("EXP-SDA");
        assertThat(result.status).isEqualTo("PENDING");
        assertThat(result.reportType).isEqualTo("fbn_inbound_scheduleddeliveryaccuracy");
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
        private ExportListPage listPage;
        private ExportStatus status;
        private CreateExportResult createResult;
        private final List<PullRequest> listRequests = new ArrayList<>();
        private final List<PullRequest> statusRequests = new ArrayList<>();
        private final List<PullRequest> createRequests = new ArrayList<>();
        private final List<CreateExportRequest> createExportRequests = new ArrayList<>();
        private final List<String> statusExportCodes = new ArrayList<>();
        private final List<Boolean> statusLogs = new ArrayList<>();

        private FakeFbnExportProvider(ObjectMapper objectMapper) {
            super(objectMapper, null, null);
        }

        @Override
        public ExportListPage listExports(PullRequest request, int page, int perPage) {
            listRequests.add(request);
            return listPage;
        }

        @Override
        public ExportStatus exportStatus(PullRequest request, String exportCode, boolean log) {
            statusRequests.add(request);
            statusExportCodes.add(exportCode);
            statusLogs.add(log);
            return status;
        }

        @Override
        public CreateExportResult createExport(PullRequest request, CreateExportRequest createRequest) {
            createRequests.add(request);
            createExportRequests.add(createRequest);
            return createResult;
        }
    }
}
