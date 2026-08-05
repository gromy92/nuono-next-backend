package com.nuono.next.datapull.report;

import com.nuono.next.datapull.orchestration.DataPullScope;
import com.nuono.next.datapull.orchestration.ExecutionContext;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ReportJobTestSupport {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 12, 0);
    static final String SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private ReportJobTestSupport() {
    }

    static ExportReportJob job(
            OperationCode operation,
            ScriptedProvider provider,
            ExportReportImporter importer
    ) {
        return job(operation, provider, importer, (task, reconcileCheckpoint, nowUtc) -> {
            task.setStepCode("REPORT_RECONCILE_CREATE");
            task.setCheckpoint(new ExportReportCheckpointCodec().encode(
                    reconcileCheckpoint
            ));
            task.setVersion(task.getVersion() + 1L);
            return true;
        });
    }

    static ExportReportJob job(
            OperationCode operation,
            ScriptedProvider provider,
            ExportReportImporter importer,
            ReportCreateAttemptFence createAttemptFence
    ) {
        DataPullScope scope = new DataPullScope(
                307L,
                108065L,
                "account-307",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa"
        );
        return new ExportReportJob(
                operation,
                "noon-report",
                () -> List.of(scope),
                provider,
                importer,
                createAttemptFence,
                waitTransition(),
                Duration.ofMinutes(2),
                Duration.ofMinutes(1)
        );
    }

    static ProviderWaitTransition waitTransition() {
        return new ProviderWaitTransition(new BackoffPolicy(
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                0.0d
        ));
    }

    static ExportPollResult ready(
            OperationCode operation,
            String handle,
            String locator,
            long declaredRowCount
    ) {
        DataPullTask authorityTask = task(1L, operation);
        RemoteExportHandle remoteHandle = new RemoteExportHandle(handle);
        return ExportPollResult.ready(
                ExportReportIntent.from(context(authorityTask)),
                remoteHandle,
                locator,
                declaredRowCount
        );
    }

    static DataPullTask task(long id, OperationCode operation) {
        DataPullTask task = DataPullTask.queued(
                id,
                operation,
                "noon-report",
                307L,
                108065L,
                "account-307",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "scope-sa",
                NOW.minusHours(1),
                operation.name() + ":date-range:2026-08-01..2026-08-01",
                "REPORT_PREPARE_INTENT",
                NOW.minusHours(2)
        );
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(NOW.plusHours(1));
        task.setFenceEpoch(1L);
        task.setVersion(1L);
        return task;
    }

    static ExecutionContext context(DataPullTask task) {
        task.setState(TaskState.RUNNING);
        return new ExecutionContext(task, NOW);
    }

    static void continueTask(DataPullTask task, AdvanceResult result) {
        if (result.getStepCode() != null) {
            task.setStepCode(result.getStepCode());
        }
        if (result.getRemoteHandle() != null) {
            task.setRemoteHandle(result.getRemoteHandle());
        }
        task.setCheckpoint(result.getCheckpoint());
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(task.getFenceEpoch() + 1L);
        task.setVersion(task.getVersion() + 1L);
    }

    static final class ScriptedProvider implements ExportReportProvider {
        final Deque<ProviderOutcome<RemoteExportHandle>> creates = new ArrayDeque<>();
        final Deque<ProviderOutcome<ExportCreateReadback>> finds = new ArrayDeque<>();
        final Deque<ProviderOutcome<ExportPollResult>> polls = new ArrayDeque<>();
        final Deque<ProviderOutcome<DownloadedReportArtifact>> downloads = new ArrayDeque<>();
        final List<String> calls = new ArrayList<>();
        final Set<String> stableRequestKeys = new LinkedHashSet<>();

        @Override
        public ProviderOutcome<RemoteExportHandle> create(ExportReportIntent intent) {
            record("CREATE", intent);
            return creates.removeFirst();
        }

        @Override
        public ProviderOutcome<ExportCreateReadback> findByRequestKey(ExportReportIntent intent) {
            record("FIND", intent);
            return finds.removeFirst();
        }

        @Override
        public ProviderOutcome<ExportPollResult> poll(
                ExportReportIntent intent,
                RemoteExportHandle handle
        ) {
            record("POLL:" + handle.getValue(), intent);
            return polls.removeFirst();
        }

        @Override
        public ProviderOutcome<DownloadedReportArtifact> download(
                ExportReportIntent intent,
                RemoteExportHandle handle,
                String downloadLocatorReference
        ) {
            record(
                    "DOWNLOAD:" + handle.getValue() + ":" + downloadLocatorReference,
                    intent
            );
            return downloads.removeFirst();
        }

        private void record(String call, ExportReportIntent intent) {
            calls.add(call);
            stableRequestKeys.add(intent.getStableRequestKey());
        }
    }
}
