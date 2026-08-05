package com.nuono.next.officialwarehouse;

import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.access;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.command;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.minimalReceivedHeader;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.minimalReceivedRow;
import static com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.scope;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.noonpull.NoonPullFailurePolicy;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnReceivedReportImportTestSupport.FakeFbnExportProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.InboundReceiptLineInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportImportInsertRecord;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsRecords.ReportRowInsertRecord;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseFbnReceivedReportIntegrityTest {
    private static final String EXPORT_CODE = "EXP-INTEGRITY";
    private static final String DOWNLOAD_URL = "https://storage.googleapis.com/private";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NoonPullFailurePolicy failurePolicy = new NoonPullFailurePolicy();

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
        when(mapper.selectInventorySyncScope(307L, "STR108065-NSA", "SA")).thenReturn(scope());
    }

    @ParameterizedTest
    @MethodSource("invalidStatuses")
    void rejectsUnprovenProviderStatusBeforeDownloadOrFactWrite(
            String providerExportCode,
            String downloadUrl,
            Integer providerTotalRows,
            String retryablePrefix
    ) {
        provider.status = status(providerExportCode, downloadUrl, providerTotalRows);

        Throwable failure = catchThrowable(() -> service.importByExportCode(
                access(),
                EXPORT_CODE,
                command()
        ));

        assertRetryable(failure, retryablePrefix);
        assertThat(provider.downloadRequests).isEmpty();
        assertNoFactWrites();
    }

    @Test
    void rejectsHeaderOnlyDownloadBeforeAnyFactWrite() {
        provider.status = status(EXPORT_CODE, DOWNLOAD_URL, 1);
        provider.downloadedBytes = minimalReceivedHeader().getBytes(StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> service.importByExportCode(
                access(),
                EXPORT_CODE,
                command()
        ));

        assertRetryable(failure, "report not ready");
        assertThat(provider.downloadRequests).hasSize(1);
        assertNoFactWrites();
    }

    @Test
    void rejectsProviderAndSourceRowCountMismatchBeforeAnyFactWrite() {
        provider.status = status(EXPORT_CODE, DOWNLOAD_URL, 2);
        provider.downloadedBytes = (minimalReceivedHeader()
                + minimalReceivedRow("P1", "Z1", "A1", "1", "1", "0", "0", "2026-08-01"))
                .getBytes(StandardCharsets.UTF_8);

        Throwable failure = catchThrowable(() -> service.importByExportCode(
                access(),
                EXPORT_CODE,
                command()
        ));

        assertRetryable(failure, "provider unavailable");
        assertThat(provider.downloadRequests).hasSize(1);
        assertNoFactWrites();
    }

    private ExportStatus status(String providerExportCode, String downloadUrl, Integer totalRows) {
        ObjectNode node = objectMapper.createObjectNode();
        if (providerExportCode != null) {
            node.put("exportCode", providerExportCode);
        }
        node.put("status", "COMPLETE");
        if (downloadUrl != null) {
            node.put("download_url", downloadUrl);
        }
        if (totalRows != null) {
            node.putObject("result").put("total_rows", totalRows);
        }
        return ExportStatus.from(objectMapper, node, EXPORT_CODE, objectMapper.createObjectNode());
    }

    private void assertRetryable(Throwable failure, String prefix) {
        assertThat(failure)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(prefix);
        assertThat(failurePolicy.decide(failurePolicy.classify(failure.getMessage()), 1).isRetryable()).isTrue();
    }

    private void assertNoFactWrites() {
        verify(mapper, never()).nextReportImportId();
        verify(mapper, never()).deactivatePreviousFbnReceivedReportImports(
                any(), any(), any(), any(), any(), any()
        );
        verify(mapper, never()).insertReportRow(any(ReportRowInsertRecord.class));
        verify(mapper, never()).insertInboundReceiptLine(any(InboundReceiptLineInsertRecord.class));
        verify(mapper, never()).insertReportImport(any(ReportImportInsertRecord.class));
    }

    private static Stream<Arguments> invalidStatuses() {
        return Stream.of(
                arguments("EXP-OTHER", DOWNLOAD_URL, 1, "provider unavailable"),
                arguments(null, DOWNLOAD_URL, 1, "provider unavailable"),
                arguments(EXPORT_CODE, DOWNLOAD_URL, null, "provider unavailable"),
                arguments(EXPORT_CODE, DOWNLOAD_URL, 0, "report not ready"),
                arguments(EXPORT_CODE, null, 1, "report not ready")
        );
    }
}
