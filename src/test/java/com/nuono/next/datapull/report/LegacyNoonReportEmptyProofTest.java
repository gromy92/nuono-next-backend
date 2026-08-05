package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noonpull.NoonReportExportStatus;
import com.nuono.next.noonpull.NoonReportProvider;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LegacyNoonReportEmptyProofTest {

    @Test
    void exactProviderHandleAndExplicitZeroProduceBoundAuthoritativeProof() {
        RemoteExportHandle handle = new RemoteExportHandle(
                "sales-dashboard-export:2026-08-01..2026-08-01"
        );
        LegacyNoonReportProviderBridge bridge = bridge(
                NoonReportExportStatus.readyForProviderExport(
                        handle.getValue(),
                        null,
                        0
                ),
                LegacyNoonReportProviderBridge.EmptyProofMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
        );
        ExportReportIntent intent = intent();

        ProviderOutcome<ExportPollResult> outcome = bridge.poll(intent, handle);

        assertEquals(ProviderOutcomeType.SUCCESS, outcome.getType());
        assertEquals(ExportPollResult.Status.EMPTY, outcome.getValue().getStatus());
        assertTrue(outcome.getValue().provesAuthoritativeEmptyFor(intent, handle));
        assertFalse(outcome.getValue().provesAuthoritativeEmptyFor(
                intent,
                new RemoteExportHandle("different-handle")
        ));
    }

    @Test
    void absentRowCountAndUnprovenHandleCannotBecomeEmpty() {
        RemoteExportHandle handle = new RemoteExportHandle(
                "sales-dashboard-export:2026-08-01..2026-08-01"
        );
        ProviderOutcome<ExportPollResult> absent = bridge(
                NoonReportExportStatus.readyForProviderExport(
                        handle.getValue(),
                        null,
                        null
                ),
                LegacyNoonReportProviderBridge.EmptyProofMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
        ).poll(intent(), handle);
        ProviderOutcome<ExportPollResult> unprovenHandle = bridge(
                NoonReportExportStatus.ready(null, 0),
                LegacyNoonReportProviderBridge.EmptyProofMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
        ).poll(intent(), handle);

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, absent.getType());
        assertEquals("REPORT_READY_LOCATOR_MISSING", absent.getSanitizedCode());
        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, unprovenHandle.getType());
        assertEquals("REPORT_EMPTY_HANDLE_UNPROVEN", unprovenHandle.getSanitizedCode());
    }

    @Test
    void localFactoryRejectsMissingOrNonzeroAuthoritativeCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportPollResult.authoritativeEmpty(
                        intent(),
                        new RemoteExportHandle("handle"),
                        null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> ExportPollResult.authoritativeEmpty(
                        intent(),
                        new RemoteExportHandle("handle"),
                        1
                )
        );
    }

    private ExportReportIntent intent() {
        return ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER");
    }

    private LegacyNoonReportProviderBridge bridge(
            NoonReportExportStatus status,
            LegacyNoonReportProviderBridge.EmptyProofMode emptyProofMode
    ) {
        NoonReportProvider delegate = new NoonReportProvider() {
            @Override
            public String createExport(NoonReportPullRequest request) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NoonReportExportStatus pollExport(
                    NoonReportPullRequest request,
                    String exportId
            ) {
                return status;
            }

            @Override
            public byte[] download(NoonReportPullRequest request, String downloadUrl) {
                return "header\n".getBytes(StandardCharsets.UTF_8);
            }
        };
        return new LegacyNoonReportProviderBridge(
                ReportBridgeTestSupport.dp02(),
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW,
                emptyProofMode,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
                new ReportDownloadLocatorVault() {
                    @Override
                    public String store(
                            ExportReportIntent intent,
                            RemoteExportHandle handle,
                            String raw
                    ) {
                        return "opaque";
                    }

                    @Override
                    public String resolve(
                            ExportReportIntent intent,
                            RemoteExportHandle handle,
                            String reference
                    ) {
                        return "https://download.test/report.csv";
                    }
                },
                new ReportArtifactStore() {
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
                }
        );
    }
}
