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
    private final CompetitorTaskSubmitter taskSubmitter;
    private final CompetitorRefreshExecutionFinalizer executionFinalizer;
    private final Set<Long> submittedTaskIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> submittedByAccount = new HashMap<>();
    private final Object reservationLock = new Object();

    CompetitorRefreshTaskDispatcher(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter
    ) {
        this(
                mapper,
                operationalTaskService,
                taskSubmitter,
                CompetitorRefreshExecutionFinalizer.unfenced(
                        mapper, operationalTaskService
                )
        );
    }

    CompetitorRefreshTaskDispatcher(
            CompetitorAnalysisMapper mapper,
            OperationalTaskService operationalTaskService,
            CompetitorTaskSubmitter taskSubmitter,
            CompetitorRefreshExecutionFinalizer executionFinalizer
    ) {
        this.taskSubmitter = taskSubmitter;
        this.executionFinalizer = executionFinalizer;
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
            if (!executionFinalizer.claimQueued(
                    task.getId(), run.getId(), runningMessage
            )) {
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
