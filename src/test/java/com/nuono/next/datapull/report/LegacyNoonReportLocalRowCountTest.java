package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonReportExportStatus;
import com.nuono.next.noonpull.NoonReportProvider;
import com.nuono.next.noonpull.NoonReportPullRequest;
import org.junit.jupiter.api.Test;

class LegacyNoonReportLocalRowCountTest {
    @Test
    void sameIntentLatestUsesLocalRowCountWithoutProviderMetadata() {
        LegacyNoonReportProviderBridge bridge = bridge(null);
        ExportReportIntent intent = intent();
        RemoteExportHandle handle = handle();

        ExportPollResult result = bridge.poll(intent, handle).getValue();

        assertEquals(ExportPollResult.Status.READY, result.getStatus());
        assertEquals(Long.MAX_VALUE, result.getArtifactAuthority().getDeclaredRowCount());
        assertTrue(result.getArtifactAuthority().usesLocalRowCount());
    }

    @Test
    void sameIntentLatestNeverTreatsUnprovenEmptyAsSuccess() {
        ExportPollResult result = bridge(0).poll(intent(), handle()).getValue();

        assertEquals(ExportPollResult.Status.PENDING, result.getStatus());
    }

    @Test
    void sameIntentReadbackRequiresADeterministicSyntheticHandle() {
        NoonReportDefinition noHandle = new NoonReportDefinition(
                OperationCode.DP01,
                "NOON_REPORT_SALES",
                NoonPullDataDomain.SALES,
                "sales",
                null
        );

        assertThrows(IllegalArgumentException.class, () -> new LegacyNoonReportProviderBridge(
                noHandle,
                new ReadyProvider(null),
                LegacyNoonReportProviderBridge.ReadbackMode
                        .SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION,
                LegacyNoonReportProviderBridge.EmptyProofMode
                        .UNPROVEN_EMPTY_REMAINS_WAITING,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode
                        .COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION,
                vault(),
                mock(ReportArtifactStore.class)
        ));
    }

    private LegacyNoonReportProviderBridge bridge(Integer totalRows) {
        return new LegacyNoonReportProviderBridge(
                ReportBridgeTestSupport.dp02(),
                new ReadyProvider(totalRows),
                LegacyNoonReportProviderBridge.ReadbackMode
                        .SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION,
                LegacyNoonReportProviderBridge.EmptyProofMode
                        .UNPROVEN_EMPTY_REMAINS_WAITING,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode
                        .COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION,
                vault(),
                mock(ReportArtifactStore.class)
        );
    }

    private ReportDownloadLocatorVault vault() {
        return new ReportDownloadLocatorVault() {
            @Override
            public String store(ExportReportIntent intent, RemoteExportHandle handle, String raw) {
                return "opaque";
            }

            @Override
            public String resolve(
                    ExportReportIntent intent,
                    RemoteExportHandle handle,
                    String reference
            ) {
                return "https://download.example.test/file";
            }
        };
    }

    private ExportReportIntent intent() {
        return ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER");
    }

    private RemoteExportHandle handle() {
        return new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01");
    }

    private static final class ReadyProvider implements NoonReportProvider {
        private final Integer totalRows;

        private ReadyProvider(Integer totalRows) {
            this.totalRows = totalRows;
        }

        @Override
        public String createExport(NoonReportPullRequest request) {
            return "sales-dashboard-export:2026-08-01..2026-08-01";
        }

        @Override
        public NoonReportExportStatus pollExport(
                NoonReportPullRequest request,
                String exportId
        ) {
            return NoonReportExportStatus.ready(
                    "https://download.example.test/file",
                    totalRows
            );
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String downloadUrl) {
            throw new UnsupportedOperationException();
        }
    }
}
