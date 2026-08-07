package com.nuono.next.datapull.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noonpull.NoonReportExportStatus;
import com.nuono.next.noonpull.NoonReportProvider;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LegacyNoonReportProviderBridgeTest {
    @Test
    void delegateProvenReadbackUsesTheExactWindowAndNeverCreatesAgain() {
        RecordingProvider delegate = new RecordingProvider();
        LegacyNoonReportProviderBridge bridge = bridge(
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW
        );
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );

        assertEquals(ProviderOutcomeType.SUCCESS, bridge.findByRequestKey(intent).getType());
        assertEquals(0, delegate.createCalls.get());
        assertEquals(1, delegate.pollCalls.get());
        assertEquals("2026-08-01", delegate.lastRequest.get().getDateFrom().toString());
        assertEquals("2026-08-01", delegate.lastRequest.get().getDateTo().toString());
    }
    @Test
    void readyPollPersistsOnlyAnOpaqueReferenceAndDownloadResolvesIt() {
        RecordingProvider delegate = new RecordingProvider();
        AtomicReference<String> raw = new AtomicReference<>();
        ReportDownloadLocatorVault vault = new ReportDownloadLocatorVault() {
            @Override
            public String store(ExportReportIntent intent, RemoteExportHandle handle, String value) {
                raw.set(value);
                return "opaque-locator-reference";
            }

            @Override
            public String resolve(ExportReportIntent intent, RemoteExportHandle handle, String reference) {
                assertEquals("opaque-locator-reference", reference);
                return raw.get();
            }
        };
        LegacyNoonReportProviderBridge bridge = new LegacyNoonReportProviderBridge(
                ReportBridgeTestSupport.dp02(),
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW,
                LegacyNoonReportProviderBridge.EmptyProofMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
                vault,
                artifactStore()
        );
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );
        RemoteExportHandle handle = new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01");

        ExportPollResult poll = bridge.poll(intent, handle).getValue();
        assertEquals("opaque-locator-reference", poll.getDownloadLocatorReference());
        assertFalse(poll.getDownloadLocatorReference().contains("signature"));
        assertEquals(ProviderOutcomeType.SUCCESS,
                bridge.download(intent, handle, poll.getDownloadLocatorReference()).getType());
        assertEquals(delegate.downloadUrl, delegate.seenDownloadUrl.get());
    }

    @Test
    void ambiguousCreateNeverBecomesAnAutomaticSecondCreate() {
        RecordingProvider delegate = new RecordingProvider();
        delegate.failCreate = true;
        LegacyNoonReportProviderBridge bridge = bridge(
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW
        );
        ExportReportIntent intent = ReportBridgeTestSupport.intent(
                OperationCode.DP02, "NOON_REPORT_ORDER"
        );

        assertEquals(ProviderOutcomeType.UNKNOWN_OUTCOME, bridge.create(intent).getType());
        assertEquals(1, delegate.createCalls.get());
        assertEquals(ProviderOutcomeType.SUCCESS, bridge.findByRequestKey(intent).getType());
        assertEquals(1, delegate.createCalls.get());
    }
    @Test
    void explicitCreateRiskIsPreservedForTheSharedBackoffPolicy() {
        RecordingProvider delegate = new RecordingProvider();
        delegate.createFailureMessage = "HTTP 429 CAPTCHA";
        LegacyNoonReportProviderBridge bridge = bridge(
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW
        );

        assertEquals(
                ProviderOutcomeType.RISK_CONTROL,
                bridge.create(ReportBridgeTestSupport.intent(
                        OperationCode.DP02, "NOON_REPORT_ORDER"
                )).getType()
        );
        assertEquals(1, delegate.createCalls.get());
    }
    @Test
    void http403IsAlwaysRiskControlInsteadOfAnAuthOrBusinessOutcome() {
        RecordingProvider delegate = new RecordingProvider();
        delegate.createFailureMessage = "HTTP 403 Forbidden";
        LegacyNoonReportProviderBridge bridge = bridge(
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW
        );

        assertEquals(
                ProviderOutcomeType.RISK_CONTROL,
                bridge.create(ReportBridgeTestSupport.intent(
                        OperationCode.DP02, "NOON_REPORT_ORDER"
                )).getType()
        );
        assertEquals(1, delegate.createCalls.get());
    }

    @Test
    void expiredSignedDownloadLocatorRequestsAHandleRepoll() {
        RecordingProvider delegate = new RecordingProvider();
        delegate.downloadFailureMessage = "Request has expired";
        LegacyNoonReportProviderBridge bridge = bridge(
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW
        );

        assertEquals(
                ProviderOutcomeType.NOT_FOUND,
                bridge.download(
                        ReportBridgeTestSupport.intent(OperationCode.DP02, "NOON_REPORT_ORDER"),
                        new RemoteExportHandle("sales-dashboard-export:2026-08-01..2026-08-01"),
                        "opaque"
                ).getType()
        );
    }

    @Test
    void unavailableReadbackIsAnExplicitReleaseBlocker() {
        RecordingProvider delegate = new RecordingProvider();
        LegacyNoonReportProviderBridge bridge = bridge(
                delegate,
                LegacyNoonReportProviderBridge.ReadbackMode.UNAVAILABLE
        );
        assertEquals(
                ProviderOutcomeType.CONTRACT_ERROR,
                bridge.findByRequestKey(ReportBridgeTestSupport.intent(
                        OperationCode.DP02, "NOON_REPORT_ORDER"
                )).getType()
        );
        ReportRuntimeReleaseEvidence evidence = new ReportRuntimeReleaseEvidence(
                unavailable(
                        OperationCode.DP01,
                        ReportProviderCapabilities.CreateReadbackEvidence.UNAVAILABLE
                ),
                unavailable(
                        OperationCode.DP02,
                        ReportProviderCapabilities.CreateReadbackEvidence.UNAVAILABLE
                ),
                unavailable(
                        OperationCode.DP03,
                        ReportProviderCapabilities.CreateReadbackEvidence.UNAVAILABLE
                ),
                unavailable(
                        OperationCode.DP07B,
                        ReportProviderCapabilities.CreateReadbackEvidence
                                .STABLE_REQUEST_KEY_UNAVAILABLE
                )
        );
        assertEquals(List.of(
                "DP01_CREATE_READBACK_UNAVAILABLE",
                "DP01_EMPTY_PROOF_UNAVAILABLE",
                "DP01_ARTIFACT_COMPLETENESS_UNAVAILABLE",
                "DP02_CREATE_READBACK_UNAVAILABLE",
                "DP02_EMPTY_PROOF_UNAVAILABLE",
                "DP02_ARTIFACT_COMPLETENESS_UNAVAILABLE",
                "DP03_CREATE_READBACK_UNAVAILABLE",
                "DP03_EMPTY_PROOF_UNAVAILABLE",
                "DP03_ARTIFACT_COMPLETENESS_UNAVAILABLE",
                "DP07B_CREATE_READBACK_STABLE_REQUEST_KEY_UNAVAILABLE",
                "DP07B_EMPTY_PROOF_UNAVAILABLE",
                "DP07B_ARTIFACT_COMPLETENESS_UNAVAILABLE"
        ), evidence.getBlockers());
        assertFalse(evidence.verified());
    }
    private LegacyNoonReportProviderBridge bridge(
            NoonReportProvider delegate,
            LegacyNoonReportProviderBridge.ReadbackMode mode
    ) {
        return new LegacyNoonReportProviderBridge(
                ReportBridgeTestSupport.dp02(),
                delegate,
                mode,
                LegacyNoonReportProviderBridge.EmptyProofMode.UNAVAILABLE,
                LegacyNoonReportProviderBridge.ArtifactCompletenessMode.UNAVAILABLE,
                locatorVault(),
                artifactStore()
        );
    }

    private ReportProviderCapabilitySource unavailable(
            OperationCode operation,
            ReportProviderCapabilities.CreateReadbackEvidence createReadbackEvidence
    ) {
        return () -> new ReportProviderCapabilities(
                operation,
                createReadbackEvidence,
                ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE,
                ReportProviderCapabilities.ArtifactCompletenessEvidence.UNAVAILABLE
        );
    }

    private ReportDownloadLocatorVault locatorVault() {
        return new ReportDownloadLocatorVault() {
            @Override
            public String store(ExportReportIntent intent, RemoteExportHandle handle, String raw) {
                return "opaque";
            }

            @Override
            public String resolve(ExportReportIntent intent, RemoteExportHandle handle, String reference) {
                return "https://download.example.test/file";
            }
        };
    }

    private ReportArtifactStore artifactStore() {
        return new ReportArtifactStore() {
            @Override
            public DownloadedReportArtifact store(
                    ExportReportIntent intent,
                    RemoteExportHandle handle,
                    byte[] content
            ) {
                return DownloadedReportArtifact.complete(
                        "artifact", ReportDigestSupport.sha256(content), content.length
                );
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

    private static final class RecordingProvider implements NoonReportProvider {
        private final AtomicInteger createCalls = new AtomicInteger();
        private final AtomicInteger pollCalls = new AtomicInteger();
        private final AtomicReference<NoonReportPullRequest> lastRequest = new AtomicReference<>();
        private final AtomicReference<String> seenDownloadUrl = new AtomicReference<>();
        private final String downloadUrl = "https://download.example.test/file?signature=secret";
        private boolean failCreate;
        private String createFailureMessage;
        private String downloadFailureMessage;

        @Override
        public String createExport(NoonReportPullRequest request) {
            createCalls.incrementAndGet();
            if (failCreate) {
                throw new IllegalStateException("connection reset");
            }
            if (createFailureMessage != null) {
                throw new IllegalStateException(createFailureMessage);
            }
            return "sales-dashboard-export:2026-08-01..2026-08-01";
        }

        @Override
        public NoonReportExportStatus pollExport(NoonReportPullRequest request, String exportId) {
            pollCalls.incrementAndGet();
            lastRequest.set(request);
            return NoonReportExportStatus.readyForProviderExport(
                    "sales-dashboard-export:2026-08-01..2026-08-01",
                    downloadUrl,
                    1
            );
        }

        @Override
        public byte[] download(NoonReportPullRequest request, String value) {
            if (downloadFailureMessage != null) {
                throw new IllegalStateException(downloadFailureMessage);
            }
            seenDownloadUrl.set(value);
            return "header\nvalue\n".getBytes(StandardCharsets.UTF_8);
        }
    }
}
