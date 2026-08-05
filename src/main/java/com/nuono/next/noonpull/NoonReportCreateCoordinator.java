package com.nuono.next.noonpull;

import org.springframework.util.StringUtils;

/** Keeps legacy report creation fail-closed when the provider cannot read back an unknown POST. */
final class NoonReportCreateCoordinator {
    private NoonReportCreateCoordinator() {
    }

    static Attempt ensureHandle(
            Long taskId,
            NoonPullTaskRecord task,
            String diagnostic,
            RemoteCreate create,
            NoonPullFoundationService foundationService,
            NoonPullFailurePolicy failurePolicy
    ) {
        if (StringUtils.hasText(task.getReportExportId())) {
            return Attempt.ready(task, task.getReportExportId());
        }
        if (NoonReportCreateAttemptState.isUnresolved(task)) {
            return hold(taskId, task.getReportPollAttempts(), foundationService);
        }
        NoonPullTaskRecord intent = foundationService.recordReportExportCreateIntent(
                taskId,
                diagnostic + "; exportCreateIntent=true"
        );
        final String exportId;
        try {
            exportId = create.invoke();
        } catch (RuntimeException failure) {
            NoonPullFailureType failureType = failurePolicy.classify(safeMessage(failure));
            if (NoonReportCreateAttemptState.isDefiniteRejection(failureType)) {
                foundationService.recordReportExportCreateRejected(taskId, safeMessage(failure));
                throw failure;
            }
            return hold(taskId, intent.getReportPollAttempts(), foundationService);
        }
        if (!StringUtils.hasText(exportId)) {
            return hold(taskId, intent.getReportPollAttempts(), foundationService);
        }
        try {
            return Attempt.ready(
                    foundationService.recordReportExportCreated(
                            taskId,
                            exportId,
                            diagnostic + "; exportCreated=true; exportId=" + exportId
                    ),
                    exportId
            );
        } catch (RuntimeException persistenceFailure) {
            NoonPullTaskRecord waiting = foundationService.recordReportExportTransientFailure(
                    taskId,
                    exportId,
                    "CREATED",
                    1,
                    "provider unavailable: report_handle_persistence_recovery; " + safeMessage(persistenceFailure)
            );
            return Attempt.waiting(waiting);
        }
    }

    private static Attempt hold(Long taskId, Integer attempts, NoonPullFoundationService foundationService) {
        NoonPullTaskRecord waiting = foundationService.recordReportExportTransientFailure(
                taskId,
                null,
                NoonReportCreateAttemptState.INTENT,
                Math.max(1, attempts == null ? 0 : attempts),
                NoonReportCreateAttemptState.UNKNOWN_OUTCOME
        );
        return Attempt.waiting(waiting);
    }

    private static String safeMessage(RuntimeException failure) {
        return failure != null && StringUtils.hasText(failure.getMessage())
                ? failure.getMessage()
                : failure == null ? "unknown failure" : failure.getClass().getSimpleName();
    }

    @FunctionalInterface
    interface RemoteCreate {
        String invoke();
    }

    static final class Attempt {
        private final NoonPullTaskRecord task;
        private final String exportId;

        private Attempt(NoonPullTaskRecord task, String exportId) {
            this.task = task;
            this.exportId = exportId;
        }

        static Attempt ready(NoonPullTaskRecord task, String exportId) {
            return new Attempt(task, exportId);
        }

        static Attempt waiting(NoonPullTaskRecord task) {
            return new Attempt(task, null);
        }

        NoonPullTaskRecord task() {
            return task;
        }

        String exportId() {
            return exportId;
        }

        boolean isWaiting() {
            return !StringUtils.hasText(exportId);
        }
    }
}
