package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noonpull.NoonReportExportStatus;
import com.nuono.next.noonpull.NoonReportProvider;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReportArtifactContractClassificationTest {

    @Test
    void deterministicArtifactConflictNeverEntersProviderRetryClassification() {
        LegacyNoonReportProviderBridge bridge = new LegacyNoonReportProviderBridge(
                ReportBridgeTestSupport.dp02(),
                unusedProvider(),
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW,
                LegacyNoonReportProviderBridge.EmptyProofMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode.UNAVAILABLE,
                locatorVault(),
                conflictingStore()
        );
        ProviderOutcome<DownloadedReportArtifact> outcome = bridge.download(
                ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER"),
                new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01"),
                "opaque"
        );

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, outcome.getType());
        assertEquals("REPORT_ARTIFACT_RESUME_CONFLICT", outcome.getSanitizedCode());
    }

    static ReportArtifactStore conflictingStore() {
        return new ReportArtifactStore() {
            @Override
            public Optional<DownloadedReportArtifact> findCompleted(
                    ExportReportIntent intent,
                    RemoteExportHandle handle
            ) {
                throw new ReportArtifactContractException(
                        "REPORT_ARTIFACT_RESUME_CONFLICT"
                );
            }

            @Override
            public DownloadedReportArtifact store(
                    ExportReportIntent intent,
                    RemoteExportHandle handle,
                    byte[] content
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StoredReportArtifact readVerified(
                    ExportReportIntent intent,
                    DownloadedReportArtifact artifact
            ) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ReportDownloadLocatorVault locatorVault() {
        return new ReportDownloadLocatorVault() {
            @Override
            public String store(
                    ExportReportIntent intent,
                    RemoteExportHandle handle,
                    String rawLocator
            ) {
                return "opaque";
            }

            @Override
            public String resolve(
                    ExportReportIntent intent,
                    RemoteExportHandle handle,
                    String reference
            ) {
                return "https://download.example.test/report.csv";
            }
        };
    }

    private NoonReportProvider unusedProvider() {
        return new NoonReportProvider() {
            @Override public String createExport(NoonReportPullRequest request) {
                throw new UnsupportedOperationException();
            }
            @Override public NoonReportExportStatus pollExport(
                    NoonReportPullRequest request, String exportId
            ) {
                throw new UnsupportedOperationException();
            }
            @Override public byte[] download(
                    NoonReportPullRequest request, String downloadUrl
            ) {
                throw new AssertionError("deterministic store conflict must stop transport");
            }
        };
    }
}
