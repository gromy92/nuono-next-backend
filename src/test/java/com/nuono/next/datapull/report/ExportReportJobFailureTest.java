package com.nuono.next.datapull.report;

import static com.nuono.next.datapull.report.ReportJobTestSupport.SHA;
import static com.nuono.next.datapull.report.ReportJobTestSupport.context;
import static com.nuono.next.datapull.report.ReportJobTestSupport.continueTask;
import static com.nuono.next.datapull.report.ReportJobTestSupport.job;
import static com.nuono.next.datapull.report.ReportJobTestSupport.ready;
import static com.nuono.next.datapull.report.ReportJobTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.report.ReportJobTestSupport.ScriptedProvider;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.RiskShareLevel;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExportReportJobFailureTest {

    @Test
    void immutableArtifactContractFailureBacksOffAndKeepsItsHandleAndArtifact() {
        ScriptedProvider provider = readyArtifactProvider("export-bad", "artifact-bad");
        AtomicInteger importAttempts = new AtomicInteger();
        ExportReportJob job = job(OperationCode.DP07B, provider, (intent, artifact) -> {
            assertEquals("artifact-bad", artifact.getArtifactKey());
            importAttempts.incrementAndGet();
            return ReportImportResult.contractError("CSV_UNCLOSED_QUOTE");
        });
        DataPullTask task = task(201L, OperationCode.DP07B);

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("CSV_UNCLOSED_QUOTE", result.getSanitizedCode());
        assertEquals("export-bad", result.getRemoteHandle());
        assertEquals(
                ExportReportCheckpoint.Phase.APPLY,
                new ExportReportCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertEquals(1, importAttempts.get());
        assertEquals(
                List.of(
                        "CREATE",
                        "POLL:export-bad",
                        "DOWNLOAD:export-bad:locator-export-bad"
                ),
                provider.calls
        );
    }

    @Test
    void terminalProviderFailureWaitsBeforeCreatingAReplacementExport() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-failed")));
        provider.polls.add(ProviderOutcome.success(
                ExportPollResult.terminalFailure("REMOTE_EXPORT_FAILED")
        ));
        ExportReportJob job = job(
                OperationCode.DP01,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask task = task(208L, OperationCode.DP01);

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult failure = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, failure.getNextState());
        assertEquals("REMOTE_EXPORT_FAILED", failure.getSanitizedCode());
        assertNull(failure.getRemoteHandle());
        assertEquals(
                ExportReportCheckpoint.Phase.CREATE,
                new ExportReportCheckpointCodec().decode(failure.getCheckpoint()).getPhase()
        );
        assertEquals(List.of("CREATE", "POLL:export-failed"), provider.calls);
    }

    @Test
    void confirmedEmptySucceedsButMissingKnownHandleWaitsWithoutRecreate() {
        ScriptedProvider emptyProvider = new ScriptedProvider();
        RemoteExportHandle emptyHandle = new RemoteExportHandle("export-empty");
        emptyProvider.creates.add(ProviderOutcome.success(emptyHandle));
        AtomicInteger imports = new AtomicInteger();
        ExportReportJob emptyJob = job(OperationCode.DP02, emptyProvider, (intent, artifact) -> {
            imports.incrementAndGet();
            return ReportImportResult.applied();
        });
        DataPullTask emptyTask = task(202L, OperationCode.DP02);
        emptyProvider.polls.add(ProviderOutcome.success(
                ExportPollResult.authoritativeEmpty(
                        ExportReportIntent.from(context(emptyTask)),
                        emptyHandle,
                        0
                )
        ));
        continueTask(emptyTask, emptyJob.advance(context(emptyTask)));
        continueTask(emptyTask, emptyJob.advance(context(emptyTask)));
        AdvanceResult empty = emptyJob.advance(context(emptyTask));

        assertEquals(TaskState.SUCCEEDED, empty.getNextState());
        assertEquals(0, imports.get());
        assertEquals(List.of("CREATE", "POLL:export-empty"), emptyProvider.calls);

        ScriptedProvider missingProvider = new ScriptedProvider();
        missingProvider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-gone")));
        missingProvider.polls.add(ProviderOutcome.notFound("REMOTE_EXPORT_NOT_FOUND"));
        ExportReportJob missingJob = job(
                OperationCode.DP02,
                missingProvider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask missingTask = task(203L, OperationCode.DP02);
        continueTask(missingTask, missingJob.advance(context(missingTask)));
        continueTask(missingTask, missingJob.advance(context(missingTask)));
        AdvanceResult missing = missingJob.advance(context(missingTask));

        assertEquals(TaskState.WAITING_REMOTE, missing.getNextState());
        assertEquals("REMOTE_EXPORT_NOT_FOUND", missing.getSanitizedCode());
        assertEquals("export-gone", missing.getRemoteHandle());
        assertEquals(
                ExportReportCheckpoint.Phase.POLL,
                new ExportReportCheckpointCodec().decode(missing.getCheckpoint()).getPhase()
        );
        assertEquals(List.of("CREATE", "POLL:export-gone"), missingProvider.calls);
    }

    @Test
    void riskUsesRetryAfterAndRetainsVerifiedShareLevelForIntegration() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.riskControl(
                "HTTP_429",
                Duration.ofMinutes(9),
                RiskShareLevel.ACCOUNT
        ));
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-after-risk")));
        ExportReportJob job = job(
                OperationCode.DP01,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask task = task(204L, OperationCode.DP01);
        continueTask(task, job.advance(context(task)));
        AdvanceResult held = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, held.getNextState());
        assertEquals(Duration.ofMinutes(9), held.getRetryAfter());
        assertEquals(RiskShareLevel.ACCOUNT, held.getBackoffShareLevel());
        ExportReportCheckpoint checkpoint = new ExportReportCheckpointCodec().decode(
                held.getCheckpoint()
        );
        assertEquals(ExportReportCheckpoint.Phase.CREATE, checkpoint.getPhase());
        assertEquals(false, checkpoint.isCreateOutcomeUnknown());
        continueTask(task, held);
        AdvanceResult retryCreate = job.advance(context(task));
        assertEquals(TaskState.WAITING_REMOTE, retryCreate.getNextState());
        assertEquals("export-after-risk", retryCreate.getRemoteHandle());
        assertEquals(List.of("CREATE", "CREATE"), provider.calls);
    }

    @Test
    void authWaitKeepsTheSamePollHandleAndCheckpoint() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-auth")));
        provider.polls.add(ProviderOutcome.authRequired("PROJECT_AUTH_REQUIRED"));
        ExportReportJob job = job(
                OperationCode.DP03,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask task = task(205L, OperationCode.DP03);
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_AUTH, result.getNextState());
        assertEquals("export-auth", result.getRemoteHandle());
        assertEquals(Duration.ofMinutes(5), result.getRetryAfter());
        ExportReportCheckpoint checkpoint = new ExportReportCheckpointCodec().decode(
                result.getCheckpoint()
        );
        assertEquals(ExportReportCheckpoint.Phase.POLL, checkpoint.getPhase());
        assertEquals(List.of("CREATE", "POLL:export-auth"), provider.calls);
    }

    @Test
    void downloadedHeaderOnlyArtifactRepollsSameHandleForAuthoritativeEmptyProof() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-file-empty")));
        provider.polls.add(ProviderOutcome.success(
                ready(OperationCode.DP03, "export-file-empty", "locator-empty", 1L)
        ));
        provider.downloads.add(ProviderOutcome.success(
                DownloadedReportArtifact.complete("artifact-empty", SHA, 12L)
        ));
        AtomicInteger imports = new AtomicInteger();
        ExportReportJob job = job(OperationCode.DP03, provider, (intent, artifact) -> {
            imports.incrementAndGet();
            return ReportImportResult.awaitingAuthoritativeEmptyProof();
        });
        DataPullTask task = task(206L, OperationCode.DP03);
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("export-file-empty", result.getRemoteHandle());
        assertEquals("REPORT_EMPTY_PROOF_REQUIRED", result.getSanitizedCode());
        assertEquals(1, imports.get());
        assertEquals(
                ExportReportCheckpoint.Phase.POLL,
                new ExportReportCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertTrue(provider.calls.contains("DOWNLOAD:export-file-empty:locator-empty"));
    }

    @Test
    void expiredDownloadLocatorRepollsSameHandleWithoutRecreatingExport() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-expired")));
        provider.polls.add(ProviderOutcome.success(
                ready(OperationCode.DP01, "export-expired", "locator-old", 1L)
        ));
        provider.downloads.add(ProviderOutcome.notFound("DOWNLOAD_LOCATOR_EXPIRED"));
        DataPullTask task = task(207L, OperationCode.DP01);
        ExportReportJob job = job(
                OperationCode.DP01,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("export-expired", result.getRemoteHandle());
        ExportReportCheckpoint checkpoint = new ExportReportCheckpointCodec().decode(
                result.getCheckpoint()
        );
        assertEquals(ExportReportCheckpoint.Phase.POLL, checkpoint.getPhase());
        assertEquals(
                List.of(
                        "CREATE",
                        "POLL:export-expired",
                        "DOWNLOAD:export-expired:locator-old"
                ),
                provider.calls
        );
    }

    private ScriptedProvider readyArtifactProvider(String handle, String artifactKey) {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle(handle)));
        provider.polls.add(ProviderOutcome.success(
                ready(OperationCode.DP07B, handle, "locator-" + handle, 1L)
        ));
        provider.downloads.add(ProviderOutcome.success(
                DownloadedReportArtifact.complete(artifactKey, SHA, 10L)
        ));
        return provider;
    }
}
