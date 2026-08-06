package com.nuono.next.noonpull;

/** One legacy task-domain adapter behind the scheduled execution facade. */
interface LegacyNoonTaskExecutor {
    boolean accepts(NoonPullTaskRecord task);

    void execute(NoonPullTaskRecord task, NoonPullScheduledExecutionResult result);
}
