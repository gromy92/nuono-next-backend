package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.util.StringUtils;

final class CompetitorStaleTaskReconciler {
    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;

    CompetitorStaleTaskReconciler(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
    }

    Outcome claim(
            OperationalTask staleTask,
            CompetitorSearchRunRow staleRun,
            LocalDateTime staleBefore,
            String errorCode,
            String errorMessage
    ) {
        if (staleTask == null || staleTask.getId() == null || staleBefore == null) {
            return Outcome.NOT_CLAIMED;
        }
        if (staleRun != null && !Objects.equals(staleTask.getId(), staleRun.getTaskId())) {
            throw new IllegalArgumentException(
                    "Competitor search run is not linked to the stale task."
            );
        }
        if (!operationalTaskService.failStaleRunning(
                staleTask.getId(), staleBefore, errorCode, errorMessage
        )) {
            return Outcome.NOT_CLAIMED;
        }
        if (staleRun == null || mapper.markActiveSearchRunFailedForTask(
                staleRun.getId(), staleTask.getId(), errorCode, errorMessage
        ) == 1) {
            return Outcome.REPLACEMENT_REQUIRED;
        }
        CompetitorSearchRunRow currentRun = mapper.selectSearchRunByTaskId(staleTask.getId());
        if (!sameTerminalRun(staleRun, currentRun)) {
            throw new IllegalStateException(
                    "Competitor search run changed during stale recovery."
            );
        }
        alignTerminalTask(staleTask.getId(), errorCode, currentRun);
        return Outcome.TERMINAL_RECONCILED;
    }

    private void alignTerminalTask(
            Long taskId,
            String claimedErrorCode,
            CompetitorSearchRunRow terminalRun
    ) {
        boolean failed = "FAILED".equals(terminalRun.getStatus());
        String taskStatus = failed ? "FAILED" : "SUCCEEDED";
        String terminalErrorCode = failed
                ? firstNonBlank(terminalRun.getErrorCode(), "REFRESH_FAILED")
                : null;
        String message = failed
                ? firstNonBlank(terminalRun.getErrorMessage(), "竞品刷新失败。")
                : "PARTIAL_FAILED".equals(terminalRun.getStatus())
                        ? "竞品刷新部分完成。"
                        : "竞品刷新已完成。";
        mapper.updateLatestRefreshRunIfNotOlder(
                terminalRun.getWatchProductId(),
                terminalRun.getId(),
                terminalRun.getStatus(),
                terminalRun.getRequestedBy()
        );
        if (mapper.alignFailedStaleTaskToTerminalRun(
                taskId, claimedErrorCode, taskStatus, terminalErrorCode, message
        ) != 1) {
            throw new IllegalStateException(
                    "Competitor stale task changed during terminal reconciliation."
            );
        }
    }

    private boolean sameTerminalRun(
            CompetitorSearchRunRow expected,
            CompetitorSearchRunRow current
    ) {
        if (expected == null
                || current == null
                || !Objects.equals(expected.getId(), current.getId())
                || !Objects.equals(expected.getTaskId(), current.getTaskId())) {
            return false;
        }
        return "SUCCEEDED".equals(current.getStatus())
                || "PARTIAL_FAILED".equals(current.getStatus())
                || "FAILED".equals(current.getStatus());
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first.trim() : fallback;
    }

    enum Outcome {
        NOT_CLAIMED,
        REPLACEMENT_REQUIRED,
        TERMINAL_RECONCILED
    }
}
