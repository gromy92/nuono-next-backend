package com.nuono.next.datapull.orchestration;

import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.checkpoint.MyBatisDataPullScopeProgressStore;
import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.persistence.MyBatisDataPullTaskStore;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.infrastructure.mapper.DataPullBackoffHoldMapper;
import com.nuono.next.infrastructure.mapper.DataPullEmergencyClaimHoldMapper;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskRepairMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executor;
import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Composition root for the single local-db DP runtime. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class DataPullRuntimeConfiguration {

    @Bean
    static BeanPostProcessor dataPullDeadlineAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if ("dataSource".equals(beanName)
                        && bean instanceof DataSource
                        && !(bean instanceof DataPullDeadlineAwareDataSource)) {
                    return new DataPullDeadlineAwareDataSource((DataSource) bean);
                }
                return bean;
            }
        };
    }

    @Bean
    DataPullTaskStore dataPullTaskStore(
            DataPullRuntimeMapper mapper,
            DataPullTaskCompactionMapper compactionMapper,
            DataPullTaskRepairMapper repairMapper
    ) {
        return new MyBatisDataPullTaskStore(mapper, compactionMapper, repairMapper);
    }

    @Bean
    DataPullTaskRepairService dataPullTaskRepairService(DataPullTaskStore tasks) {
        return new DataPullTaskRepairService(tasks, Clock.systemUTC());
    }

    @Bean
    BackoffHoldStore dataPullBackoffHoldStore(DataPullBackoffHoldMapper mapper) {
        return new MyBatisBackoffHoldStore(mapper);
    }

    @Bean
    EmergencyClaimHoldStore dataPullEmergencyClaimHoldStore(
            DataPullEmergencyClaimHoldMapper mapper
    ) {
        return new MyBatisEmergencyClaimHoldStore(mapper);
    }

    @Bean
    DataPullScopeProgressStore dataPullScopeProgressStore(DataPullScopeProgressMapper mapper) {
        return new MyBatisDataPullScopeProgressStore(mapper);
    }

    @Bean
    BackoffPolicy dataPullBackoffPolicy(DataPullRuntimeProperties properties) {
        properties.validate();
        return new BackoffPolicy(
                properties.backoffBaseDelay(),
                properties.backoffMaximumDelay(),
                properties.getBackoffJitterRatio()
        );
    }

    @Bean
    ProviderWaitTransition dataPullProviderWaitTransition(BackoffPolicy backoffPolicy) {
        return new ProviderWaitTransition(backoffPolicy);
    }

    @Bean
    DataPullMyBatisDeadlineInterceptor dataPullMyBatisDeadlineInterceptor() {
        return new DataPullMyBatisDeadlineInterceptor();
    }

    @Bean
    DataPullRuntimeStopSignal dataPullRuntimeStopSignal() {
        return new DataPullRuntimeStopSignal();
    }

    @Bean
    DataPullJobRegistry dataPullJobRegistry(List<DataPullJob> jobs) {
        DataPullJobRegistry registry = new DataPullJobRegistry(jobs);
        registry.requireComplete();
        return registry;
    }

    @Bean
    FairDispatcher dataPullFairDispatcher(
            DataPullTaskStore tasks,
            BackoffHoldStore holds,
            EmergencyClaimHoldStore emergencyClaimHolds
    ) {
        return new FairDispatcher(tasks, holds, emergencyClaimHolds);
    }

    @Bean(name = "dataPullWorkerExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor dataPullWorkerExecutor(DataPullRuntimeProperties properties) {
        properties.validate();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getWorkerCount());
        executor.setMaxPoolSize(properties.getWorkerCount());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("dp-runtime-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean
    RuntimeTransitionCommitter dataPullRuntimeTransitionCommitter(
            DataPullTaskStore tasks,
            BackoffHoldStore holds,
            DataPullAuthRecoveryQueue authRecoveryQueue
    ) {
        return new RuntimeTransitionCommitter(tasks, holds, authRecoveryQueue);
    }

    @Bean
    DataPullAuthRecoveryQueue dataPullAuthRecoveryQueue(
            NoonAuthWaitQueue authWaitQueue
    ) {
        return new NoonDataPullAuthRecoveryQueue(authWaitQueue);
    }

    @Bean
    RuntimeExecutor dataPullRuntimeExecutor(
            DataPullJobRegistry jobs,
            RuntimeTransitionCommitter transitionCommitter,
            DataPullRuntimeStopSignal stopSignal
    ) {
        return new RuntimeExecutor(jobs, transitionCommitter, Clock.systemUTC(), stopSignal);
    }

    @Bean
    DataPullRuntimeCoordinator dataPullRuntimeCoordinator(
            DataPullRuntimeReconciler reconciler,
            FairDispatcher dispatcher,
            RuntimeExecutor runtimeExecutor,
            List<DataPullRuntimeMaintenance> maintenance,
            @Qualifier("dataPullWorkerExecutor") Executor workerExecutor,
            DataPullRuntimeProperties properties,
            DataPullRuntimeReleaseGate releaseGate,
            DataPullRuntimeLeadership leadership
    ) {
        releaseGate.requireReady();
        return new DataPullRuntimeCoordinator(
                reconciler,
                dispatcher,
                runtimeExecutor,
                maintenance,
                workerExecutor,
                Clock.systemUTC(),
                leadership,
                properties.leaseDuration(),
                properties.getMaximumClaimsPerTick(),
                properties.getWorkerCount()
        );
    }

    @Bean
    DataPullRuntimeScheduler dataPullRuntimeScheduler(
            DataPullRuntimeCoordinator coordinator,
            DataPullRuntimeProperties properties,
            TaskSchedulerBuilder schedulerBuilder,
            DataPullRuntimeStopSignal stopSignal
    ) {
        return new DataPullRuntimeScheduler(
                coordinator,
                properties,
                Clock.systemUTC(),
                () -> schedulerBuilder.poolSize(1)
                        .threadNamePrefix(DataPullRuntimeScheduler.THREAD_NAME_PREFIX)
                        .build(),
                stopSignal
        );
    }

}
