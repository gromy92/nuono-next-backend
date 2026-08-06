package com.nuono.next.officialwarehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.report.DownloadedReportArtifact;
import com.nuono.next.datapull.report.ExportCreateReadback;
import com.nuono.next.datapull.report.ExportReportIntent;
import com.nuono.next.datapull.report.ExportPollResult;
import com.nuono.next.datapull.report.FbnReceivedExportReportProvider;
import com.nuono.next.datapull.report.NoonReportDefinition;
import com.nuono.next.datapull.report.RemoteExportHandle;
import com.nuono.next.datapull.report.ReportArtifactContractException;
import com.nuono.next.datapull.report.ReportArtifactStore;
import com.nuono.next.datapull.report.ReportDownloadLocatorVault;
import com.nuono.next.datapull.report.ReportProviderCapabilities;
import com.nuono.next.datapull.report.StoredReportArtifact;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportListPage;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FbnReceivedExportReportProviderBridgeTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void realListApiCannotReadBackTheCallerStableRequestKey() {
        FakeProvider delegate = new FakeProvider(objectMapper);
        FbnReceivedExportReportProvider bridge = bridge(delegate);

        ProviderOutcome<ExportCreateReadback> result = bridge.findByRequestKey(intent());

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, result.getType());
        assertEquals(
                "DP07B_CREATE_READBACK_STABLE_REQUEST_KEY_UNAVAILABLE",
                result.getSanitizedCode()
        );
        assertEquals(0, delegate.listCalls);
        assertEquals(
                ReportProviderCapabilities.CreateReadbackEvidence
                        .STABLE_REQUEST_KEY_UNAVAILABLE,
                bridge.reportProviderCapabilities().getCreateReadbackEvidence()
        );
        assertEquals(0, delegate.createCalls);
    }

    @Test
    void positiveReadyRequiresExactHandleAndDeclaredRowCount() {
        ObjectNode raw = objectMapper.createObjectNode()
                .put("export_code", "EXP-POSITIVE")
                .put("status_code", "COMPLETE")
                .put("download_url", "https://download.test/report.csv");
        raw.set("result", objectMapper.createObjectNode().put("total_rows", 2));
        FakeProvider delegate = new FakeProvider(objectMapper);
        delegate.status = ExportStatus.from(objectMapper, raw, "EXP-POSITIVE", raw);
        FbnReceivedExportReportProvider bridge = bridge(
                delegate,
                ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE,
                ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
        );
        RemoteExportHandle handle = new RemoteExportHandle("EXP-POSITIVE");

        ProviderOutcome<ExportPollResult> result = bridge.poll(intent(), handle);

        assertEquals(ProviderOutcomeType.SUCCESS, result.getType());
        assertEquals(ExportPollResult.Status.READY, result.getValue().getStatus());
        assertEquals(true, result.getValue().provesReadyFor(intent(), handle));
        assertEquals(2L, result.getValue().getArtifactAuthority().getDeclaredRowCount());
    }

    @Test
    void explicitZeroRowStatusMustMatchTheExactProviderHandle() {
        JsonNode raw = objectMapper.createObjectNode()
                .put("export_code", "EXP-ZERO")
                .put("status_code", "COMPLETE")
                .set("result", objectMapper.createObjectNode().put("total_rows", 0));
        FakeProvider delegate = new FakeProvider(objectMapper);
        delegate.status = ExportStatus.from(objectMapper, raw, "EXP-ZERO", raw);
        FbnReceivedExportReportProvider bridge = bridge(
                delegate,
                ReportProviderCapabilities.EmptyProofEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
                ReportProviderCapabilities.ArtifactCompletenessEvidence.UNAVAILABLE
        );
        RemoteExportHandle handle = new RemoteExportHandle("EXP-ZERO");

        ProviderOutcome<ExportPollResult> result = bridge.poll(intent(), handle);

        assertEquals(ProviderOutcomeType.SUCCESS, result.getType());
        assertEquals(ExportPollResult.Status.EMPTY, result.getValue().getStatus());
        assertEquals(true, result.getValue().provesAuthoritativeEmptyFor(intent(), handle));
    }

    @Test
    void deterministicArtifactConflictIsAContractErrorNotAProviderRetry() {
        FbnReceivedExportReportProvider bridge = bridge(
                new FakeProvider(objectMapper),
                ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE,
                ReportProviderCapabilities.ArtifactCompletenessEvidence.UNAVAILABLE,
                conflictingStore()
        );

        ProviderOutcome<DownloadedReportArtifact> result = bridge.download(
                intent(), new RemoteExportHandle("EXP-CONFLICT"), "opaque"
        );

        assertEquals(ProviderOutcomeType.CONTRACT_ERROR, result.getType());
        assertEquals("REPORT_ARTIFACT_IDEMPOTENCY_CONFLICT", result.getSanitizedCode());
    }

    private FbnReceivedExportReportProvider bridge(FakeProvider delegate) {
        return bridge(
                delegate,
                ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE,
                ReportProviderCapabilities.ArtifactCompletenessEvidence.UNAVAILABLE
        );
    }

    private FbnReceivedExportReportProvider bridge(
            FakeProvider delegate,
            ReportProviderCapabilities.EmptyProofEvidence emptyProofEvidence,
            ReportProviderCapabilities.ArtifactCompletenessEvidence artifactEvidence
    ) {
        return bridge(delegate, emptyProofEvidence, artifactEvidence, unusedStore());
    }

    private FbnReceivedExportReportProvider bridge(
            FakeProvider delegate,
            ReportProviderCapabilities.EmptyProofEvidence emptyProofEvidence,
            ReportProviderCapabilities.ArtifactCompletenessEvidence artifactEvidence,
            ReportArtifactStore artifactStore
    ) {
        return new FbnReceivedExportReportProvider(
                new NoonReportDefinition(
                        OperationCode.DP07B,
                        "NOON_FBN_REPORT",
                        NoonPullDataDomain.OFFICIAL_WAREHOUSE_FBN_RECEIVED,
                        FbnReceivedExportReportProvider.REPORT_TYPE,
                        null
                ),
                delegate,
                (request, url, sink) -> {
                    throw new UnsupportedOperationException();
                },
                new ReportDownloadLocatorVault() {
                    @Override
                    public String store(ExportReportIntent intent, RemoteExportHandle handle, String raw) {
                        return "opaque";
                    }

                    @Override
                    public String resolve(ExportReportIntent intent, RemoteExportHandle handle, String reference) {
                        return "https://download.test/report.csv";
                    }
                },
                artifactStore,
                emptyProofEvidence,
                artifactEvidence
        );
    }

    private ReportArtifactStore unusedStore() {
        return new ReportArtifactStore() {
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

    private ReportArtifactStore conflictingStore() {
        return new ReportArtifactStore() {
            @Override
            public Optional<DownloadedReportArtifact> findCompleted(
                    ExportReportIntent intent,
                    RemoteExportHandle handle
            ) {
                throw new ReportArtifactContractException(
                        "REPORT_ARTIFACT_IDEMPOTENCY_CONFLICT"
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

    private ExportReportIntent intent() {
        DataPullTask task = new DataPullTask();
        task.setId(9002L);
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(1L);
        task.setLeaseOwner("test-worker");
        task.setOperationCode(OperationCode.DP07B);
        task.setProviderChannel("NOON_FBN_REPORT");
        task.setOwnerUserId(307L);
        task.setLogicalStoreId(91L);
        task.setAccountKey("PRJ108065");
        task.setProjectCode("PRJ108065");
        task.setStoreCode("STR108065-NSA");
        task.setSiteCode("SA");
        task.setScopeKey("NOON:307:91:PRJ108065:STR108065-NSA:SA");
        task.setBusinessWindowKey("DP07B:date-range:2026-08-01..2026-08-01");
        return ExportReportIntent.from(new ExecutionContext(
                task, LocalDateTime.of(2026, 8, 2, 23, 30)
        ));
    }

    private static final class FakeProvider extends OfficialWarehouseFbnExportProvider {
        private ExportListPage page;
        private ExportStatus status;
        private int listCalls;
        private int createCalls;

        private FakeProvider(ObjectMapper objectMapper) {
            super(objectMapper, null, null);
        }

        @Override
        public ExportListPage listExports(PullRequest request, int pageNumber, int perPage) {
            listCalls++;
            return page;
        }

        @Override
        public CreateExportResult createExport(PullRequest request, CreateExportRequest createRequest) {
            createCalls++;
            throw new UnsupportedOperationException();
        }

        @Override
        public ExportStatus exportStatus(PullRequest request, String exportCode, boolean log) {
            return status;
        }
    }
}
