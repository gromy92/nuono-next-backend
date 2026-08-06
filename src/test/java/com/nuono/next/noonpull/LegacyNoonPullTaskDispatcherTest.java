package com.nuono.next.noonpull;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyNoonPullTaskDispatcherTest {

    @Test
    void dispatchesToFirstAcceptingDomainAdapterOnly() {
        RecordingExecutor first = new RecordingExecutor(NoonPullDataDomain.SALES, false);
        RecordingExecutor second = new RecordingExecutor(NoonPullDataDomain.ORDER, true);
        RecordingExecutor third = new RecordingExecutor(NoonPullDataDomain.ORDER, true);
        LegacyNoonPullTaskDispatcher dispatcher = new LegacyNoonPullTaskDispatcher(
                List.of(first, second, third), () -> null
        );
        NoonPullScheduledExecutionResult result = new NoonPullScheduledExecutionResult();

        dispatcher.dispatch(task(NoonPullDataDomain.ORDER, NoonPullType.REPORT), result);

        assertEquals(0, first.executionCount);
        assertEquals(1, second.executionCount);
        assertEquals(0, third.executionCount);
        assertEquals(1, result.getExecutedTaskCount());
    }

    @Test
    void unknownTaskIsSkippedAndMissingAsnExecutorFailsOnlyThatTask() {
        LegacyNoonPullTaskDispatcher dispatcher = new LegacyNoonPullTaskDispatcher(
                List.of(), () -> null
        );
        NoonPullScheduledExecutionResult result = new NoonPullScheduledExecutionResult();

        dispatcher.dispatch(task(NoonPullDataDomain.PRODUCT, NoonPullType.REPORT), result);
        dispatcher.dispatch(task(NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN,
                NoonPullType.INTERFACE), result);

        assertEquals(1, result.getSkippedTaskCount());
        assertEquals(1, result.getFailedTaskCount());
    }

    private NoonPullTaskRecord task(
            NoonPullDataDomain domain,
            NoonPullType pullType
    ) {
        NoonPullTaskRecord task = new NoonPullTaskRecord();
        task.setId(1L);
        task.setDataDomain(domain);
        task.setPullType(pullType);
        return task;
    }

    private static final class RecordingExecutor implements LegacyNoonTaskExecutor {
        private final NoonPullDataDomain acceptedDomain;
        private final boolean accepts;
        private int executionCount;

        private RecordingExecutor(
                NoonPullDataDomain acceptedDomain,
                boolean accepts
        ) {
            this.acceptedDomain = acceptedDomain;
            this.accepts = accepts;
        }

        @Override
        public boolean accepts(NoonPullTaskRecord task) {
            return accepts && task.getDataDomain() == acceptedDomain;
        }

        @Override
        public void execute(
                NoonPullTaskRecord task,
                NoonPullScheduledExecutionResult result
        ) {
            executionCount++;
            result.executed();
        }
    }
}
