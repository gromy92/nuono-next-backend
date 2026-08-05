package com.nuono.next.datapull.report;

import static com.nuono.next.datapull.report.ReportJobTestSupport.SHA;
import static com.nuono.next.datapull.report.ReportJobTestSupport.context;
import static com.nuono.next.datapull.report.ReportJobTestSupport.continueTask;
import static com.nuono.next.datapull.report.ReportJobTestSupport.job;
import static com.nuono.next.datapull.report.ReportJobTestSupport.ready;
import static com.nuono.next.datapull.report.ReportJobTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.report.ReportJobTestSupport.ScriptedProvider;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ExportReportJobLifecycleTest {

    @Test
    void persistsIntentThenCreatesPollsOneHandleDownloadsAndImports() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-77")));
        provider.polls.add(ProviderOutcome.success(ExportPollResult.pending()));
        provider.polls.add(ProviderOutcome.success(ExportPollResult.pending()));
        provider.polls.add(ProviderOutcome.success(
                ready(OperationCode.DP01, "export-77", "locator-77", 3L)
        ));
        provider.downloads.add(ProviderOutcome.success(
                DownloadedReportArtifact.complete("artifact|77", SHA, 128L)
        ));
        AtomicInteger imports = new AtomicInteger();
        ExportReportJob job = job(OperationCode.DP01, provider, (intent, artifact) -> {
            assertEquals("artifact|77", artifact.getArtifactKey());
            imports.incrementAndGet();
            return ReportImportResult.applied();
        });
        DataPullTask task = task(101L, OperationCode.DP01);

        AdvanceResult intent = job.advance(context(task));
        assertEquals(TaskState.QUEUED, intent.getNextState());
        assertTrue(provider.calls.isEmpty());
        continueTask(task, intent);

        AdvanceResult created = job.advance(context(task));
        assertEquals(TaskState.WAITING_REMOTE, created.getNextState());
        assertEquals("export-77", created.getRemoteHandle());
        continueTask(task, created);

        for (int index = 0; index < 3; index++) {
            AdvanceResult polled = job.advance(context(task));
            continueTask(task, polled);
        }
        AdvanceResult downloaded = job.advance(context(task));
        assertEquals(TaskState.QUEUED, downloaded.getNextState());
        continueTask(task, downloaded);
        AdvanceResult imported = job.advance(context(task));

        assertEquals(TaskState.SUCCEEDED, imported.getNextState());
        assertEquals(1, imports.get());
        assertEquals(
                List.of(
                        "CREATE",
                        "POLL:export-77",
                        "POLL:export-77",
                        "POLL:export-77",
                        "DOWNLOAD:export-77:locator-77"
                ),
                provider.calls
        );
        assertEquals(1, provider.stableRequestKeys.size());
    }

    @Test
    void unknownCreateRemainsFailClosedEvenWhenReadbackReportsNotFound() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.unknownOutcome("CREATE_TIMEOUT_UNKNOWN"));
        provider.finds.add(ProviderOutcome.notFound("EXPORT_NOT_FOUND"));
        provider.finds.add(ProviderOutcome.notFound("EXPORT_NOT_FOUND"));
        ExportReportJob job = job(
                OperationCode.DP02,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask task = task(102L, OperationCode.DP02);

        continueTask(task, job.advance(context(task)));
        AdvanceResult unknown = job.advance(context(task));
        assertEquals(TaskState.WAITING_REMOTE, unknown.getNextState());
        continueTask(task, unknown);
        AdvanceResult absent = job.advance(context(task));
        assertEquals(TaskState.WAITING_REMOTE, absent.getNextState());
        assertEquals("CREATE_UNKNOWN_NOT_FOUND_FAIL_CLOSED", absent.getSanitizedCode());
        continueTask(task, absent);
        AdvanceResult stillAbsent = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, stillAbsent.getNextState());
        assertEquals(List.of("CREATE", "FIND", "FIND"), provider.calls);
    }

    @Test
    void lostHandleTransitionAfterSuccessfulCreateStillResumesWithReadback() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-lost")));
        ExportReportJob job = job(
                OperationCode.DP02,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask task = task(104L, OperationCode.DP02);
        provider.finds.add(ProviderOutcome.success(ExportCreateReadback.found(
                ExportReportIntent.from(context(task)),
                new RemoteExportHandle("export-lost")
        )));

        continueTask(task, job.advance(context(task)));
        AdvanceResult createResultWhoseTransitionWasLost = job.advance(context(task));
        assertEquals("export-lost", createResultWhoseTransitionWasLost.getRemoteHandle());
        // The pre-call fence has already persisted RECONCILE_CREATE. Simulate lease expiry
        // and a fresh claim without applying the lost post-call transition.
        task.setFenceEpoch(task.getFenceEpoch() + 1L);
        task.setVersion(task.getVersion() + 1L);
        task.setState(TaskState.RUNNING);

        AdvanceResult reconciled = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, reconciled.getNextState());
        assertEquals("export-lost", reconciled.getRemoteHandle());
        assertEquals(List.of("CREATE", "FIND"), provider.calls);
    }

    @Test
    void unboundTerminalAbsenceProofNeverReplaysAnUnknownCreate() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.unknownOutcome("CREATE_TIMEOUT_UNKNOWN"));
        ExportReportJob job = job(
                OperationCode.DP02,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        DataPullTask task = task(107L, OperationCode.DP02);
        provider.finds.add(ProviderOutcome.success(ExportCreateReadback.terminallyAbsent(
                ExportReportIntent.from(context(task)),
                "provider-final-absence-107"
        )));
        provider.finds.add(ProviderOutcome.notFound("EXPORT_NOT_FOUND"));

        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult absent = job.advance(context(task));
        assertEquals(TaskState.WAITING_REMOTE, absent.getNextState());
        assertEquals("CREATE_ABSENCE_PROOF_UNBOUND_FAIL_CLOSED", absent.getSanitizedCode());
        continueTask(task, absent);
        AdvanceResult stillAbsent = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, stillAbsent.getNextState());
        assertEquals(List.of("CREATE", "FIND", "FIND"), provider.calls);
    }

    @Test
    void createIsNeverCalledWhenThePreCallFenceIsStaleOrUnknown() {
        ScriptedProvider staleProvider = new ScriptedProvider();
        staleProvider.creates.add(ProviderOutcome.success(new RemoteExportHandle("must-not-call")));
        ExportReportJob staleJob = job(
                OperationCode.DP02,
                staleProvider,
                (intent, artifact) -> ReportImportResult.applied(),
                (task, checkpoint, nowUtc) -> false
        );
        DataPullTask staleTask = task(105L, OperationCode.DP02);
        continueTask(staleTask, staleJob.advance(context(staleTask)));

        AdvanceResult stale = staleJob.advance(context(staleTask));

        assertEquals(TaskState.WAITING_REMOTE, stale.getNextState());
        assertEquals("CREATE_INTENT_STALE_FENCE", stale.getSanitizedCode());
        assertTrue(staleProvider.calls.isEmpty());

        ScriptedProvider unknownProvider = new ScriptedProvider();
        unknownProvider.creates.add(ProviderOutcome.success(new RemoteExportHandle("must-not-call")));
        ExportReportJob unknownJob = job(
                OperationCode.DP02,
                unknownProvider,
                (intent, artifact) -> ReportImportResult.applied(),
                (task, checkpoint, nowUtc) -> {
                    throw new IllegalStateException("commit outcome unknown");
                }
        );
        DataPullTask unknownTask = task(106L, OperationCode.DP02);
        continueTask(unknownTask, unknownJob.advance(context(unknownTask)));

        AdvanceResult unknown = unknownJob.advance(context(unknownTask));

        assertEquals(TaskState.WAITING_REMOTE, unknown.getNextState());
        assertEquals("CREATE_INTENT_PREPARE_UNKNOWN", unknown.getSanitizedCode());
        assertTrue(unknownProvider.calls.isEmpty());
    }

    @Test
    void restartContinuesThePersistedHandleWithoutCreatingAgain() {
        ScriptedProvider beforeRestart = new ScriptedProvider();
        beforeRestart.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-restart")));
        DataPullTask task = task(103L, OperationCode.DP03);
        ExportReportJob firstProcess = job(
                OperationCode.DP03,
                beforeRestart,
                (intent, artifact) -> ReportImportResult.applied()
        );
        continueTask(task, firstProcess.advance(context(task)));
        continueTask(task, firstProcess.advance(context(task)));

        ScriptedProvider afterRestart = new ScriptedProvider();
        afterRestart.polls.add(ProviderOutcome.success(
                ready(OperationCode.DP03, "export-restart", "locator-restart", 4L)
        ));
        ExportReportJob restarted = job(
                OperationCode.DP03,
                afterRestart,
                (intent, artifact) -> ReportImportResult.applied()
        );
        AdvanceResult result = restarted.advance(context(task));

        assertEquals(TaskState.QUEUED, result.getNextState());
        assertEquals(List.of("POLL:export-restart"), afterRestart.calls);
        assertTrue(afterRestart.creates.isEmpty());
    }
}
