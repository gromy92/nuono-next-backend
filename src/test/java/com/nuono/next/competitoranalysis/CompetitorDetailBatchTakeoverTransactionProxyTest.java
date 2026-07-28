package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class CompetitorDetailBatchTakeoverTransactionProxyTest {
    private static final long WATCH_ID = 180123L;
    private static final long TASK_ID = 150300L;
    private static final long RUN_ID = 220300L;

    @Test
    void callerThrowsOnlyAfterCurrentSupersessionCommits() {
        try (AnnotationConfigApplicationContext context = context()) {
            Scenario scenario = scenario(context);
            scenario.stubLease();
            scenario.stubNewerOwner();
            scenario.stubSupersede(true);

            CompetitorDetailBatchTakeoverFence fence =
                    new CompetitorDetailBatchTakeoverFence(
                            scenario.takeover, TASK_ID, RUN_ID, WATCH_ID
                    );

            assertThrows(CompetitorRefreshLeaseLostException.class, fence::run);
            assertEquals("SUCCEEDED", scenario.tx.taskStatus);
            assertEquals("SUCCEEDED", scenario.tx.runStatus);
            assertEquals(1, scenario.tx.commitCount);
            assertEquals(0, scenario.tx.rollbackCount);
            assertEquals(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    scenario.tx.propagation
            );
        }
    }

    @Test
    void runCasFailureRollsBackTaskSupersession() {
        try (AnnotationConfigApplicationContext context = context()) {
            Scenario scenario = scenario(context);
            scenario.stubLease();
            scenario.stubNewerOwner();
            scenario.stubSupersede(false);

            assertThrows(
                    IllegalStateException.class,
                    () -> scenario.takeover.takeoverOlderBatches(
                            TASK_ID, RUN_ID, WATCH_ID
                    )
            );
            assertEquals("RUNNING", scenario.tx.taskStatus);
            assertEquals("RUNNING", scenario.tx.runStatus);
            assertEquals(0, scenario.tx.commitCount);
            assertEquals(1, scenario.tx.rollbackCount);
        }
    }

    @Test
    void leaseLossRollsBackHeartbeat() {
        try (AnnotationConfigApplicationContext context = context()) {
            Scenario scenario = scenario(context);
            when(scenario.mapper.lockRunningRefreshTask(TASK_ID))
                    .thenReturn(TASK_ID);
            when(scenario.mapper.heartbeatRunningRefreshTask(
                    eq(TASK_ID), any(LocalDateTime.class)
            )).thenAnswer(invocation -> {
                scenario.tx.heartbeat = true;
                return 1;
            });
            when(scenario.mapper.lockRunningRefreshRun(
                    TASK_ID, RUN_ID, WATCH_ID
            )).thenReturn(null);

            assertThrows(
                    CompetitorRefreshLeaseLostException.class,
                    () -> scenario.takeover.takeoverOlderBatches(
                            TASK_ID, RUN_ID, WATCH_ID
                    )
            );
            assertFalse(scenario.tx.heartbeat);
            assertEquals(0, scenario.tx.commitCount);
            assertEquals(1, scenario.tx.rollbackCount);
        }
    }

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    private static Scenario scenario(
            AnnotationConfigApplicationContext context
    ) {
        CompetitorDetailBatchTakeover takeover =
                context.getBean(CompetitorDetailBatchTakeover.class);
        assertTrue(AopUtils.isAopProxy(takeover));
        return new Scenario(
                context.getBean(CompetitorAnalysisMapper.class),
                context.getBean(OperationalTaskService.class),
                context.getBean(TakeoverTransactionManager.class),
                takeover
        );
    }

    private static final class Scenario {
        private final CompetitorAnalysisMapper mapper;
        private final OperationalTaskService tasks;
        private final TakeoverTransactionManager tx;
        private final CompetitorDetailBatchTakeover takeover;

        private Scenario(
                CompetitorAnalysisMapper mapper,
                OperationalTaskService tasks,
                TakeoverTransactionManager tx,
                CompetitorDetailBatchTakeover takeover
        ) {
            this.mapper = mapper;
            this.tasks = tasks;
            this.tx = tx;
            this.takeover = takeover;
        }

        private void stubLease() {
            when(mapper.lockRunningRefreshTask(TASK_ID)).thenReturn(TASK_ID);
            when(mapper.heartbeatRunningRefreshTask(
                    eq(TASK_ID), any(LocalDateTime.class)
            )).thenAnswer(invocation -> {
                tx.heartbeat = true;
                return 1;
            });
            when(mapper.lockRunningRefreshRun(TASK_ID, RUN_ID, WATCH_ID))
                    .thenReturn(RUN_ID);
        }

        private void stubNewerOwner() {
            when(tasks.find(TASK_ID)).thenReturn(Optional.of(task()));
            when(mapper.selectSearchRunById(RUN_ID)).thenReturn(run());
            when(mapper.listScheduledDetailOwnershipCandidates(
                    WATCH_ID, TASK_ID, RUN_ID
            )).thenReturn(List.of(newerCandidate()));
        }

        private void stubSupersede(boolean runSucceeds) {
            when(mapper.lockActiveScheduledDetailTask(TASK_ID))
                    .thenAnswer(invocation -> tx.taskStatus);
            when(mapper.lockActiveScheduledDetailRun(TASK_ID, RUN_ID, WATCH_ID))
                    .thenAnswer(invocation -> tx.runStatus);
            when(mapper.supersedeActiveScheduledDetailTask(
                    eq(TASK_ID), eq("RUNNING"), any(), any()
            )).thenAnswer(invocation -> {
                tx.taskStatus = "SUCCEEDED";
                return 1;
            });
            when(mapper.supersedeActiveScheduledDetailRun(
                    TASK_ID, RUN_ID, WATCH_ID, "RUNNING"
            )).thenAnswer(invocation -> {
                if (runSucceeds) {
                    tx.runStatus = "SUCCEEDED";
                    return 1;
                }
                return 0;
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        CompetitorAnalysisMapper mapper() {
            return mock(CompetitorAnalysisMapper.class);
        }

        @Bean
        OperationalTaskService operationalTaskService() {
            return mock(OperationalTaskService.class);
        }

        @Bean
        CompetitorDetailBatchTakeover takeover(
                CompetitorAnalysisMapper mapper,
                OperationalTaskService tasks
        ) {
            CompetitorRefreshExecutionFinalizer finalizer =
                    new CompetitorRefreshExecutionFinalizer(
                            mapper,
                            tasks,
                            new CompetitorRefreshLeaseGuard(mapper)
                    );
            return new CompetitorDetailBatchTakeover(mapper, tasks, finalizer);
        }

        @Bean
        TakeoverTransactionManager transactionManager() {
            return new TakeoverTransactionManager();
        }
    }

    static final class TakeoverTransactionManager
            extends AbstractPlatformTransactionManager {
        private String taskStatus = "RUNNING";
        private String runStatus = "RUNNING";
        private boolean heartbeat;
        private String taskBefore;
        private String runBefore;
        private boolean heartbeatBefore;
        private int propagation;
        private int commitCount;
        private int rollbackCount;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(
                Object transaction,
                TransactionDefinition definition
        ) {
            taskBefore = taskStatus;
            runBefore = runStatus;
            heartbeatBefore = heartbeat;
            propagation = definition.getPropagationBehavior();
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            taskStatus = taskBefore;
            runStatus = runBefore;
            heartbeat = heartbeatBefore;
            rollbackCount++;
        }
    }

    private static OperationalTask task() {
        OperationalTask task = new OperationalTask();
        task.setId(TASK_ID);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setPayloadJson(payload("day-0"));
        return task;
    }

    private static CompetitorSearchRunRow run() {
        CompetitorSearchRunRow run = new CompetitorSearchRunRow();
        run.setId(RUN_ID);
        run.setTaskId(TASK_ID);
        run.setWatchProductId(WATCH_ID);
        run.setTriggerMode(
                CompetitorRefreshExecutionMode.SCHEDULED_DETAIL.triggerMode()
        );
        run.setStatus("RUNNING");
        return run;
    }

    private static CompetitorDetailTakeoverCandidateRow newerCandidate() {
        CompetitorDetailTakeoverCandidateRow candidate =
                new CompetitorDetailTakeoverCandidateRow();
        candidate.setTaskId(150400L);
        candidate.setRunId(220400L);
        candidate.setTaskStatus("QUEUED");
        candidate.setRunStatus("QUEUED");
        candidate.setPayloadJson(payload("day-1"));
        return candidate;
    }

    private static String payload(String batchKey) {
        return "{\"watchProductId\":" + WATCH_ID
                + ",\"batchKey\":\"" + batchKey + "\"}";
    }
}
