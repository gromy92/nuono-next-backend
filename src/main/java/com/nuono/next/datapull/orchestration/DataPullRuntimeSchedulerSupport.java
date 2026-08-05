package com.nuono.next.datapull.orchestration;

import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/** Small lifecycle helpers kept outside the scheduler's concurrency state machine. */
final class DataPullRuntimeSchedulerSupport {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataPullRuntimeScheduler.class);

    private DataPullRuntimeSchedulerSupport() { }

    static Duration requirePositive(Duration value) {
        Duration nonNull = Objects.requireNonNull(value, "duration");
        if (nonNull.isZero() || nonNull.isNegative()) {
            throw new IllegalArgumentException("duration must be positive");
        }
        return nonNull;
    }

    static void configure(ThreadPoolTaskScheduler candidate) {
        candidate.setPoolSize(1);
        candidate.setThreadNamePrefix(DataPullRuntimeScheduler.THREAD_NAME_PREFIX);
        candidate.setRemoveOnCancelPolicy(true);
        candidate.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        candidate.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        candidate.setWaitForTasksToCompleteOnShutdown(false);
    }

    static void warn(String message, RuntimeException failure) {
        LOGGER.warn(message + " errorType={}", failure.getClass().getSimpleName());
    }
}
