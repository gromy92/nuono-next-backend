package com.nuono.next.datapull.wiring;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJobRegistry;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.orchestration.DataPullRuntimeTechnicalHealth;
import com.nuono.next.datapull.orchestration.ScheduleBatchEngine;
import com.nuono.next.datapull.orchestration.ScheduleReconciler;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.schedule.BoundedScheduleBatchEngine;
import com.nuono.next.datapull.schedule.DataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.DataPullScopeAdmissionStore;
import com.nuono.next.datapull.schedule.MyBatisDataPullScheduleAnchorStore;
import com.nuono.next.datapull.schedule.MyBatisDataPullScopeAdmissionStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleBindingBatchApplier;
import com.nuono.next.datapull.schedule.MyBatisScheduleManifestVerifier;
import com.nuono.next.datapull.schedule.MyBatisScheduleReconciliationStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleRotationStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleSealedEpochApplier;
import com.nuono.next.datapull.schedule.MyBatisScheduleTaskBatchApplier;
import com.nuono.next.datapull.schedule.ScheduleTaskPayloadBinder;
import com.nuono.next.datapull.schedule.ScheduleTaskPayloadBinderRegistry;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Composition root for persisted, bounded schedule reconciliation Adapters. */
@Configuration
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class ScheduleRuntimeConfiguration {

    @Bean
    DataPullScheduleAnchorStore dataPullScheduleAnchorStore(
            DataPullScheduleAnchorMapper mapper
    ) {
        return new MyBatisDataPullScheduleAnchorStore(mapper);
    }

    @Bean
    DataPullScopeAdmissionStore dataPullScopeAdmissionStore(
            DataPullScopeAdmissionMapper mapper
    ) {
        return new MyBatisDataPullScopeAdmissionStore(mapper);
    }

    @Bean
    ScheduleTaskPayloadBinderRegistry scheduleTaskPayloadBinderRegistry(
            List<ScheduleTaskPayloadBinder> binders
    ) {
        return new ScheduleTaskPayloadBinderRegistry(binders);
    }

    @Bean
    ScheduleBatchEngine dataPullScheduleBatchEngine(
            MyBatisScheduleRotationStore rotation,
            MyBatisScheduleManifestVerifier manifests,
            MyBatisScheduleReconciliationStore reconciliation,
            DataPullScheduleScanMapper scans,
            MyBatisScheduleSealedEpochApplier admissions,
            MyBatisScheduleBindingBatchApplier bindings,
            MyBatisScheduleTaskBatchApplier tasks
    ) {
        return new BoundedScheduleBatchEngine(
                rotation,
                manifests,
                reconciliation,
                scans,
                admissions,
                bindings,
                tasks
        );
    }

    @Bean
    ScheduleReconciler dataPullScheduleReconciler(
            DataPullScheduleRegistry schedules,
            DataPullJobRegistry jobs,
            DataPullTaskStore tasks,
            DataPullScheduleAnchorStore anchors,
            DataPullScopeAdmissionStore admissions,
            PlatformTransactionManager transactionManager,
            ScheduleBatchEngine boundedScheduleBatchEngine,
            DataPullRuntimeTechnicalHealth technicalHealth
    ) {
        schedules.requireComplete();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setTimeout(DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS);
        return new ScheduleReconciler(
                schedules,
                jobs,
                tasks,
                anchors,
                admissions,
                action -> Objects.requireNonNull(
                        transaction.execute((status) -> action.get()),
                        "operation reconciliation transaction returned null"
                ),
                boundedScheduleBatchEngine,
                technicalHealth::observe
        );
    }
}
