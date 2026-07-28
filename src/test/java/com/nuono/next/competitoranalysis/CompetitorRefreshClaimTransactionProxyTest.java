package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

class CompetitorRefreshClaimTransactionProxyTest {
    private static final long TASK_ID = 150001L;
    private static final long RUN_ID = 220001L;
    private static final String RUNNING_MESSAGE = "running";

    @Test
    void crashAfterTaskClaimRollsBackBeforeDispatcherCanExecute() {
        try (AnnotationConfigApplicationContext context = context()) {
            Scenario scenario = scenario(context);
            when(scenario.tasks.claimQueued(TASK_ID, RUNNING_MESSAGE))
                    .thenAnswer(invocation -> {
                        scenario.tx.taskStatus = OperationalTaskStatus.RUNNING;
                        return true;
                    });
            when(scenario.mapper.markSearchRunRunning(RUN_ID))
                    .thenThrow(new SimulatedProcessCrash());

            assertTrue(scenario.submit());
            assertThrows(
                    SimulatedProcessCrash.class,
                    scenario.submitted.remove(0)::run
            );

            assertEquals(OperationalTaskStatus.QUEUED, scenario.tx.taskStatus);
            assertEquals("QUEUED", scenario.tx.runStatus);
            assertEquals(0, scenario.executions.get());
            assertEquals(1, scenario.tx.rollbackCount);
        }
    }

    @Test
    void successfulDispatcherClaimCommitsOnlyPairedRunningState() {
        try (AnnotationConfigApplicationContext context = context()) {
            Scenario scenario = scenario(context);
            when(scenario.tasks.claimQueued(TASK_ID, RUNNING_MESSAGE))
                    .thenAnswer(invocation -> {
                        scenario.tx.taskStatus = OperationalTaskStatus.RUNNING;
                        return true;
                    });
            when(scenario.mapper.markSearchRunRunning(RUN_ID))
                    .thenAnswer(invocation -> {
                        scenario.tx.runStatus = "RUNNING";
                        return 1;
                    });

            assertTrue(scenario.submit());
            scenario.submitted.remove(0).run();

            assertEquals(OperationalTaskStatus.RUNNING, scenario.tx.taskStatus);
            assertEquals("RUNNING", scenario.tx.runStatus);
            assertEquals(1, scenario.executions.get());
            assertEquals(1, scenario.tx.commitCount);
            assertEquals(0, scenario.tx.rollbackCount);
        }
    }

    @Test
    void runClaimConflictCommitsFailedTaskInsteadOfActiveClaimGap() {
        try (AnnotationConfigApplicationContext context = context()) {
            Scenario scenario = scenario(context);
            when(scenario.tasks.claimQueued(TASK_ID, RUNNING_MESSAGE))
                    .thenAnswer(invocation -> {
                        scenario.tx.taskStatus = OperationalTaskStatus.RUNNING;
                        return true;
                    });
            when(scenario.mapper.markSearchRunRunning(RUN_ID)).thenReturn(0);
            doAnswer(invocation -> {
                scenario.tx.taskStatus = OperationalTaskStatus.FAILED;
                return null;
            }).when(scenario.tasks).fail(
                    TASK_ID,
                    "COMPETITOR_SEARCH_RUN_CLAIM_CONFLICT",
                    "刷新执行记录状态冲突，任务未执行。"
            );

            assertTrue(scenario.submit());
            scenario.submitted.remove(0).run();

            assertEquals(OperationalTaskStatus.FAILED, scenario.tx.taskStatus);
            assertEquals("QUEUED", scenario.tx.runStatus);
            assertEquals(0, scenario.executions.get());
            assertEquals(1, scenario.tx.commitCount);
            assertEquals(0, scenario.tx.rollbackCount);
            verify(scenario.tasks).fail(
                    TASK_ID,
                    "COMPETITOR_SEARCH_RUN_CLAIM_CONFLICT",
                    "刷新执行记录状态冲突，任务未执行。"
            );
        }
    }

    private static Scenario scenario(
            AnnotationConfigApplicationContext context
    ) {
        CompetitorRefreshExecutionFinalizer finalizer =
                context.getBean(CompetitorRefreshExecutionFinalizer.class);
        assertTrue(AopUtils.isAopProxy(finalizer));
        return new Scenario(
                context.getBean(CompetitorAnalysisMapper.class),
                context.getBean(OperationalTaskService.class),
                context.getBean(ClaimStateTransactionManager.class),
                finalizer
        );
    }

    private static AnnotationConfigApplicationContext context() {
        return new AnnotationConfigApplicationContext(TestConfiguration.class);
    }

    private static final class Scenario {
        private final CompetitorAnalysisMapper mapper;
        private final OperationalTaskService tasks;
        private final ClaimStateTransactionManager tx;
        private final List<Runnable> submitted = new ArrayList<>();
        private final AtomicInteger executions = new AtomicInteger();
        private final CompetitorRefreshTaskDispatcher dispatcher;
        private final OperationalTask task = new OperationalTask();
        private final CompetitorSearchRunRow run = new CompetitorSearchRunRow();

        private Scenario(
                CompetitorAnalysisMapper mapper,
                OperationalTaskService tasks,
                ClaimStateTransactionManager tx,
                CompetitorRefreshExecutionFinalizer finalizer
        ) {
            this.mapper = mapper;
            this.tasks = tasks;
            this.tx = tx;
            task.setId(TASK_ID);
            run.setId(RUN_ID);
            dispatcher = new CompetitorRefreshTaskDispatcher(
                    mapper,
                    tasks,
                    (accountKey, execution) -> submitted.add(execution),
                    finalizer
            );
        }

        private boolean submit() {
            return dispatcher.submit(
                    "501::store",
                    task,
                    run,
                    RUNNING_MESSAGE,
                    executions::incrementAndGet
            );
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
        CompetitorRefreshLeaseGuard leaseGuard(CompetitorAnalysisMapper mapper) {
            return new CompetitorRefreshLeaseGuard(mapper);
        }

        @Bean
        CompetitorRefreshExecutionFinalizer finalizer(
                CompetitorAnalysisMapper mapper,
                OperationalTaskService tasks,
                CompetitorRefreshLeaseGuard leaseGuard
        ) {
            return new CompetitorRefreshExecutionFinalizer(
                    mapper, tasks, leaseGuard
            );
        }

        @Bean
        ClaimStateTransactionManager transactionManager() {
            return new ClaimStateTransactionManager();
        }
    }

    static final class ClaimStateTransactionManager
            extends AbstractPlatformTransactionManager {
        private OperationalTaskStatus taskStatus = OperationalTaskStatus.QUEUED;
        private String runStatus = "QUEUED";
        private OperationalTaskStatus taskBefore;
        private String runBefore;
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
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            commitCount++;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            taskStatus = taskBefore;
            runStatus = runBefore;
            rollbackCount++;
        }
    }

    private static final class SimulatedProcessCrash
            extends RuntimeException {
    }
}
