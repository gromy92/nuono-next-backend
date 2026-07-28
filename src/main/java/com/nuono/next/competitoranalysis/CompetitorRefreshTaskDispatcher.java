package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

final class CompetitorRefreshTaskDispatcher {
    private static final int MAX_SUBMITTED_TASKS = 1000;
    private static final int MAX_SUBMITTED_PER_ACCOUNT = 50;
    private final CompetitorAnalysisMapper mapper;
    private final OperationalTaskService operationalTaskService;
    private final CompetitorTaskSubmitter taskSubmitter;
    private final Set<Long> submittedTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> submittedByAccount = new HashMap<>();
    private final Object reservationLock = new Object();

    CompetitorRefreshTaskDispatcher(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter
    ) {
        this.mapper = mapper;
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter;
    }

    int availableCapacity(int maximumSubmittedTasks) {
        synchronized (reservationLock) {
            return Math.max(0, Math.min(MAX_SUBMITTED_TASKS, maximumSubmittedTasks) - submittedTaskIds.size());
        }
    }

    boolean submit(
            String accountKey,
            OperationalTask task,
            CompetitorSearchRunRow run,
            String runningMessage,
            Runnable execution
    ) {
        return submit(accountKey, task, run, runningMessage, () -> true, execution);
    }

    boolean submit(
            String accountKey,
            OperationalTask task,
            CompetitorSearchRunRow run,
            String runningMessage,
            BooleanSupplier executionAllowed,
            Runnable execution
    ) {
        if (!reserve(accountKey, task.getId())) {
            return false;
        }
        try {
            taskSubmitter.submit(
                    accountKey,
                    () -> executeClaimed(accountKey, task, run, runningMessage, executionAllowed, execution)
            );
            return true;
        } catch (RuntimeException exception) {
            release(accountKey, task.getId());
            throw exception;
        }
    }

    private void executeClaimed(
            String accountKey,
            OperationalTask task,
            CompetitorSearchRunRow run,
            String runningMessage,
            BooleanSupplier executionAllowed,
            Runnable execution
    ) {
        try {
            if (!executionAllowed.getAsBoolean()) {
                return;
            }
            if (!operationalTaskService.claimQueued(task.getId(), runningMessage)) {
                return;
            }
            if (mapper.markSearchRunRunning(run.getId()) != 1) {
                operationalTaskService.fail(
                        task.getId(),
                        "COMPETITOR_SEARCH_RUN_CLAIM_CONFLICT",
                        "刷新执行记录状态冲突，任务未执行。"
                );
                return;
            }
            execution.run();
        } finally {
            release(accountKey, task.getId());
        }
    }

    private boolean reserve(String accountKey, Long taskId) {
        synchronized (reservationLock) {
            int accountCount = submittedByAccount.getOrDefault(accountKey, 0);
            if (submittedTaskIds.contains(taskId)
                    || submittedTaskIds.size() >= MAX_SUBMITTED_TASKS
                    || accountCount >= MAX_SUBMITTED_PER_ACCOUNT) {
                return false;
            }
            submittedTaskIds.add(taskId);
            submittedByAccount.put(accountKey, accountCount + 1);
            return true;
        }
    }

    private void release(String accountKey, Long taskId) {
        synchronized (reservationLock) {
            if (!submittedTaskIds.remove(taskId)) {
                return;
            }
            int remaining = submittedByAccount.getOrDefault(accountKey, 1) - 1;
            if (remaining <= 0) {
                submittedByAccount.remove(accountKey);
            } else {
                submittedByAccount.put(accountKey, remaining);
            }
        }
    }
}
