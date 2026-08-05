package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Routes one legacy task to exactly one domain executor. */
final class LegacyNoonPullTaskDispatcher {
    private final List<LegacyNoonTaskExecutor> executors;
    private final Supplier<NoonPullRetryExecutor> retryExecutor;

    LegacyNoonPullTaskDispatcher(
            List<LegacyNoonTaskExecutor> executors,
            Supplier<NoonPullRetryExecutor> retryExecutor
    ) {
        this.executors = executors == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(executors));
        this.retryExecutor = retryExecutor;
    }

    void dispatch(
            NoonPullTaskRecord task,
            NoonPullScheduledExecutionResult result
    ) {
        if (task == null) {
            result.skipped();
            return;
        }
        if (isAsnInterface(task)) {
            NoonPullRetryExecutor retry = retryExecutor == null
                    ? null : retryExecutor.get();
            if (retry == null) {
                result.failed();
            } else {
                retry.executeAsn(task, result);
            }
            return;
        }
        for (LegacyNoonTaskExecutor executor : executors) {
            if (executor != null && executor.accepts(task)) {
                executor.execute(task, result);
                return;
            }
        }
        result.skipped();
    }

    private boolean isAsnInterface(NoonPullTaskRecord task) {
        return task.getPullType() == NoonPullType.INTERFACE
                && task.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN;
    }
}
