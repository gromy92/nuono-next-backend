package com.nuono.next.datapull.wiring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullJobRegistry;
import com.nuono.next.datapull.orchestration.DataPullRuntimeReconciler;
import com.nuono.next.datapull.orchestration.DataPullRuntimeTechnicalHealth;
import com.nuono.next.datapull.orchestration.ScheduleBatchEngine;
import com.nuono.next.datapull.orchestration.ScheduleReconciler;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.persistence.MyBatisDataPullTaskBatchStore;
import com.nuono.next.datapull.schedule.BoundedScheduleBatchEngine;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.MyBatisScheduleBindingBatchApplier;
import com.nuono.next.datapull.schedule.MyBatisScheduleManifestVerifier;
import com.nuono.next.datapull.schedule.MyBatisScheduleReconciliationStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleRotationStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleSealedEpochApplier;
import com.nuono.next.datapull.schedule.MyBatisScheduleTaskBatchApplier;
import com.nuono.next.datapull.schedule.ScheduleTaskPayloadBinderRegistry;
import com.nuono.next.datapull.snapshot.SnapshotFactApplyGuard;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskBatchMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class DataPullCompositionWiringTest {

    @Test
    void scheduleCompositionPublishesOneKernelInterfaceAdapter() {
        DataPullScheduleScanMapper scans = mock(DataPullScheduleScanMapper.class);
        DataPullScheduleAnchorMapper anchors = mock(DataPullScheduleAnchorMapper.class);
        DataPullScopeAdmissionMapper admissions = mock(DataPullScopeAdmissionMapper.class);
        DataPullScheduleApplyMapper apply = mock(DataPullScheduleApplyMapper.class);
        ScheduleTaskPayloadBinderRegistry payloads =
                new ScheduleTaskPayloadBinderRegistry(java.util.List.of());
        runtimeContext()
                .withBean(MyBatisScheduleRotationStore.class, () -> mock(
                        MyBatisScheduleRotationStore.class
                ))
                .withBean(DataPullScheduleRegistry.class, DataPullScheduleRegistry::new)
                .withBean(DataPullJobRegistry.class,
                        () -> new DataPullJobRegistry(java.util.List.of()))
                .withBean(DataPullTaskStore.class, () -> mock(DataPullTaskStore.class))
                .withBean(DataPullRuntimeTechnicalHealth.class,
                        DataPullRuntimeTechnicalHealth::new)
                .withBean(PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .withBean(DataPullScheduleAnchorMapper.class, () -> anchors)
                .withBean(DataPullScopeAdmissionMapper.class, () -> admissions)
                .withBean(MyBatisScheduleManifestVerifier.class,
                        () -> new MyBatisScheduleManifestVerifier(scans, anchors))
                .withBean(MyBatisScheduleReconciliationStore.class, () -> mock(
                        MyBatisScheduleReconciliationStore.class
                ))
                .withBean(DataPullScheduleScanMapper.class, () -> scans)
                .withBean(MyBatisScheduleSealedEpochApplier.class, () ->
                        new MyBatisScheduleSealedEpochApplier(
                                scans,
                                apply,
                                admissions,
                                anchors
                        ))
                .withBean(MyBatisScheduleBindingBatchApplier.class, () ->
                        new MyBatisScheduleBindingBatchApplier(
                                scans,
                                apply,
                                mock(DataPullScopeBindingMapper.class)
                        ))
                .withBean(MyBatisScheduleTaskBatchApplier.class, () ->
                        new MyBatisScheduleTaskBatchApplier(
                                scans,
                                mock(DataPullScheduleTaskPlanMapper.class),
                                new MyBatisDataPullTaskBatchStore(
                                        mock(DataPullScheduleTaskBatchMapper.class),
                                        mock(DataPullTaskCompactionMapper.class)
                                ),
                                payloads
                        ))
                .withUserConfiguration(ScheduleRuntimeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ScheduleBatchEngine.class);
                    assertThat(context).hasSingleBean(BoundedScheduleBatchEngine.class);
                    assertThat(context).hasSingleBean(
                            ScheduleTaskPayloadBinderRegistry.class
                    );
                    assertThat(context).hasSingleBean(ScheduleReconciler.class);
                    assertThat(context).hasSingleBean(DataPullRuntimeReconciler.class);
                    assertThat(context.getBean(DataPullRuntimeReconciler.class))
                            .isSameAs(context.getBean(ScheduleReconciler.class));
                });
    }

    @Test
    void snapshotCompositionOwnsTheSingleSharedApplyGuard() {
        runtimeContext()
                .withBean(SnapshotFactApplyMapper.class, () -> mock(
                        SnapshotFactApplyMapper.class
                ))
                .withBean(SnapshotCarryProgressMapper.class, () -> mock(
                        SnapshotCarryProgressMapper.class
                ))
                .withUserConfiguration(SnapshotRuntimeConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SnapshotFactApplyGuard.class);
                });
    }

    private ApplicationContextRunner runtimeContext() {
        return new ApplicationContextRunner().withPropertyValues(
                "spring.profiles.active=local-db",
                DataPullExecutionMode.PROPERTY + "=RUNTIME"
        );
    }
}
