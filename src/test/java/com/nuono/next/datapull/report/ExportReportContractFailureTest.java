package com.nuono.next.datapull.report;

import static com.nuono.next.datapull.report.ReportJobTestSupport.context;
import static com.nuono.next.datapull.report.ReportJobTestSupport.continueTask;
import static com.nuono.next.datapull.report.ReportJobTestSupport.job;
import static com.nuono.next.datapull.report.ReportJobTestSupport.ready;
import static com.nuono.next.datapull.report.ReportJobTestSupport.task;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.report.ReportJobTestSupport.ScriptedProvider;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.TaskState;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExportReportContractFailureTest {

    @Test
    void pollContractFailureBacksOffWithTheSameHandleAndCheckpointPhase() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-contract")));
        provider.polls.add(ProviderOutcome.contractError("REPORT_ARTIFACT_ROW_COUNT_UNPROVEN"));
        DataPullTask task = task(301L, OperationCode.DP01);
        ExportReportJob job = job(
                OperationCode.DP01,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("REPORT_ARTIFACT_ROW_COUNT_UNPROVEN", result.getSanitizedCode());
        assertEquals("export-contract", result.getRemoteHandle());
        assertEquals(
                ExportReportCheckpoint.Phase.POLL,
                new ExportReportCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertEquals(List.of("CREATE", "POLL:export-contract"), provider.calls);
    }

    @Test
    void createContractFailureReconcilesBeforeAnyPossibleReplay() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.contractError("REPORT_CREATE_INTENT_INVALID"));
        DataPullTask task = task(302L, OperationCode.DP02);
        ExportReportJob job = job(
                OperationCode.DP02,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        continueTask(task, job.advance(context(task)));

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("REPORT_CREATE_INTENT_INVALID", result.getSanitizedCode());
        assertEquals(
                ExportReportCheckpoint.Phase.RECONCILE_CREATE,
                new ExportReportCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertEquals(List.of("CREATE"), provider.calls);
    }

    @Test
    void unknownPollOutcomeWaitsOnTheSameHandleInsteadOfTerminating() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-unknown")));
        provider.polls.add(ProviderOutcome.unknownOutcome("REPORT_POLL_OUTCOME_UNKNOWN"));
        DataPullTask task = task(303L, OperationCode.DP03);
        ExportReportJob job = job(
                OperationCode.DP03,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        String checkpoint = task.getCheckpoint();

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("REPORT_POLL_OUTCOME_UNKNOWN", result.getSanitizedCode());
        assertEquals("export-unknown", result.getRemoteHandle());
        assertEquals(checkpoint, result.getCheckpoint());
        assertEquals(List.of("CREATE", "POLL:export-unknown"), provider.calls);
    }

    @Test
    void unknownCreateStillWaitsWhenItsReadbackCapabilityHasAContractFailure() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.unknownOutcome("REPORT_CREATE_OUTCOME_UNKNOWN"));
        provider.finds.add(ProviderOutcome.contractError("REPORT_CREATE_READBACK_UNAVAILABLE"));
        DataPullTask task = task(304L, OperationCode.DP07B);
        ExportReportJob job = job(
                OperationCode.DP07B,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_REMOTE, result.getNextState());
        assertEquals("REPORT_CREATE_READBACK_UNAVAILABLE", result.getSanitizedCode());
        assertEquals(
                ExportReportCheckpoint.Phase.RECONCILE_CREATE,
                new ExportReportCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertEquals(List.of("CREATE", "FIND"), provider.calls);
    }

    @Test
    void successfulDownloadWithoutArtifactBacksOffOnTheSameExport() {
        ScriptedProvider provider = new ScriptedProvider();
        provider.creates.add(ProviderOutcome.success(new RemoteExportHandle("export-no-body")));
        provider.polls.add(ProviderOutcome.success(
                ready(OperationCode.DP01, "export-no-body", "locator-no-body", 1L)
        ));
        provider.downloads.add(ProviderOutcome.success(null));
        DataPullTask task = task(305L, OperationCode.DP01);
        ExportReportJob job = job(
                OperationCode.DP01,
                provider,
                (intent, artifact) -> ReportImportResult.applied()
        );
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));
        continueTask(task, job.advance(context(task)));

        AdvanceResult result = job.advance(context(task));

        assertEquals(TaskState.WAITING_BACKOFF, result.getNextState());
        assertEquals("DOWNLOAD_SUCCESS_WITHOUT_ARTIFACT", result.getSanitizedCode());
        assertEquals("export-no-body", result.getRemoteHandle());
        assertEquals(
                ExportReportCheckpoint.Phase.DOWNLOAD,
                new ExportReportCheckpointCodec().decode(result.getCheckpoint()).getPhase()
        );
        assertEquals(
                List.of(
                        "CREATE",
                        "POLL:export-no-body",
                        "DOWNLOAD:export-no-body:locator-no-body"
                ),
                provider.calls
        );
    }
}
