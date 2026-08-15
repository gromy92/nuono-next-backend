package com.nuono.next.datapull.orchestration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.leader.DataPullRuntimeLeaderStore;
import com.nuono.next.datapull.leader.DataPullRuntimeLeadership;
import com.nuono.next.datapull.persistence.MyBatisDataPullTaskBatchStore;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.schedule.DataPullScheduleRegistry;
import com.nuono.next.datapull.schedule.MyBatisScheduleBindingBatchApplier;
import com.nuono.next.datapull.schedule.MyBatisScheduleManifestVerifier;
import com.nuono.next.datapull.schedule.MyBatisScheduleReconciliationStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleRotationStore;
import com.nuono.next.datapull.schedule.MyBatisScheduleSealedEpochApplier;
import com.nuono.next.datapull.schedule.MyBatisScheduleTaskBatchApplier;
import com.nuono.next.datapull.schedule.ScheduleEpochRetention;
import com.nuono.next.datapull.schedule.ScheduleScopeSource;
import com.nuono.next.datapull.schedule.ScheduleScopeSourceRegistry;
import com.nuono.next.datapull.schedule.ScheduleSourcePage;
import com.nuono.next.datapull.schedule.ScheduleTaskPayloadBinderRegistry;
import com.nuono.next.infrastructure.mapper.DataPullBackoffHoldMapper;
import com.nuono.next.infrastructure.mapper.DataPullEmergencyClaimHoldMapper;
import com.nuono.next.infrastructure.mapper.DataPullRuntimeMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleAnchorMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleApplyMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleEpochRetentionMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleScanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskBatchMapper;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskPlanMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeAdmissionMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskRepairMapper;
import com.nuono.next.infrastructure.mapper.SnapshotCarryProgressMapper;
import com.nuono.next.infrastructure.mapper.SnapshotFactApplyMapper;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.task.TaskSchedulerBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/** Minimal real composition-root fixture; every external edge is an explicit inert test double. */
final class DataPullRuntimeContextFixture {

    private DataPullRuntimeContextFixture() {
    }

    static ApplicationContextRunner runtime(boolean includeScheduleAdapters) {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        DataPullExecutionMode.PROPERTY + "=RUNTIME"
                )
                .withInitializer(context -> registerJobs(
                        (GenericApplicationContext) context
                ))
                .withBean(DataPullRuntimeProperties.class, DataPullRuntimeContextFixture::properties)
                .withBean(DataPullRuntimeMapper.class, () -> mock(DataPullRuntimeMapper.class))
                .withBean(DataPullTaskCompactionMapper.class,
                        () -> mock(DataPullTaskCompactionMapper.class))
                .withBean(DataPullTaskRepairMapper.class,
                        () -> mock(DataPullTaskRepairMapper.class))
                .withBean(DataPullBackoffHoldMapper.class,
                        () -> mock(DataPullBackoffHoldMapper.class))
                .withBean(DataPullEmergencyClaimHoldMapper.class,
                        () -> mock(DataPullEmergencyClaimHoldMapper.class))
                .withBean(DataPullScopeProgressMapper.class,
                        () -> mock(DataPullScopeProgressMapper.class))
                .withBean(SnapshotFactApplyMapper.class,
                        () -> mock(SnapshotFactApplyMapper.class))
                .withBean(SnapshotCarryProgressMapper.class,
                        () -> mock(SnapshotCarryProgressMapper.class))
                .withBean(NoonAccountSessionAttentionPort.class,
                        () -> mock(NoonAccountSessionAttentionPort.class))
                .withBean(TaskSchedulerBuilder.class, TaskSchedulerBuilder::new)
                .withBean(DataPullRuntimeReleaseGate.class,
                        DataPullRuntimeContextFixture::readyReleaseGate)
                .withBean(DataPullRuntimeLeadership.class,
                        DataPullRuntimeContextFixture::idleLeadership)
                .withBean(DataPullRuntimeTechnicalHealth.class,
                        DataPullRuntimeTechnicalHealth::new);
        return includeScheduleAdapters
                ? withScheduleAdapters(runner)
                : runner;
    }

    private static ApplicationContextRunner withScheduleAdapters(
            ApplicationContextRunner runner
    ) {
        DataPullScheduleScanMapper scans = mock(DataPullScheduleScanMapper.class);
        DataPullScheduleAnchorMapper anchors = mock(DataPullScheduleAnchorMapper.class);
        DataPullScheduleApplyMapper apply = mock(DataPullScheduleApplyMapper.class);
        DataPullScopeAdmissionMapper admissions = mock(DataPullScopeAdmissionMapper.class);
        ScheduleEpochRetention retention = new ScheduleEpochRetention(
                mock(DataPullScheduleEpochRetentionMapper.class)
        );
        MyBatisDataPullTaskBatchStore taskStore = new MyBatisDataPullTaskBatchStore(
                mock(DataPullScheduleTaskBatchMapper.class),
                mock(DataPullTaskCompactionMapper.class)
        );
        return runner
                .withBean(DataPullScheduleRegistry.class, DataPullScheduleRegistry::new)
                .withBean(DataPullScheduleAnchorMapper.class, () -> anchors)
                .withBean(DataPullScopeAdmissionMapper.class, () -> admissions)
                .withBean(PlatformTransactionManager.class,
                        () -> mock(PlatformTransactionManager.class))
                .withBean(DataPullScheduleScanMapper.class, () -> scans)
                .withBean(MyBatisScheduleRotationStore.class,
                        () -> new MyBatisScheduleRotationStore(scans))
                .withBean(MyBatisScheduleManifestVerifier.class,
                        () -> new MyBatisScheduleManifestVerifier(scans, anchors))
                .withBean(MyBatisScheduleReconciliationStore.class,
                        () -> new MyBatisScheduleReconciliationStore(
                                scans,
                                new ScheduleScopeSourceRegistry(List.of(
                                        new AllOperationSource()
                                )),
                                retention
                        ))
                .withBean(MyBatisScheduleSealedEpochApplier.class,
                        () -> new MyBatisScheduleSealedEpochApplier(
                                scans, apply, admissions, anchors
                        ))
                .withBean(MyBatisScheduleBindingBatchApplier.class,
                        () -> new MyBatisScheduleBindingBatchApplier(
                                scans, apply, mock(DataPullScopeBindingMapper.class)
                        ))
                .withBean(MyBatisScheduleTaskBatchApplier.class,
                        () -> new MyBatisScheduleTaskBatchApplier(
                                scans,
                                mock(DataPullScheduleTaskPlanMapper.class),
                                taskStore,
                                new ScheduleTaskPayloadBinderRegistry(List.of())
                        ));
    }

    private static void registerJobs(GenericApplicationContext context) {
        for (OperationCode operation : OperationCode.values()) {
            context.registerBean(
                    "contextSmokeJob" + operation.name(),
                    DataPullJob.class,
                    () -> new InertJob(operation)
            );
        }
    }

    private static DataPullRuntimeProperties properties() {
        DataPullRuntimeProperties properties = new DataPullRuntimeProperties();
        properties.setSchedulerInitialDelayMs(Duration.ofHours(1).toMillis());
        return properties;
    }

    private static DataPullRuntimeReleaseGate readyReleaseGate() {
        List<DataPullRuntimeReleaseEvidence> evidence = new ArrayList<>();
        for (DataPullRuntimeReleaseRequirement requirement
                : DataPullRuntimeReleaseRequirement.values()) {
            evidence.add(new VerifiedEvidence(requirement));
        }
        return new DataPullRuntimeReleaseGate(
                new DataPullRuntimeReleaseEvidenceRegistry(evidence)
        );
    }

    private static DataPullRuntimeLeadership idleLeadership() {
        DataPullRuntimeLeaderStore store = mock(DataPullRuntimeLeaderStore.class);
        when(store.acquireOrRenew(anyString(), any(Duration.class)))
                .thenReturn(Optional.empty());
        return new DataPullRuntimeLeadership(store, "execution-context-smoke", Duration.ofMinutes(2));
    }

    private static final class VerifiedEvidence implements DataPullRuntimeReleaseEvidence {
        private final DataPullRuntimeReleaseRequirement requirement;

        private VerifiedEvidence(DataPullRuntimeReleaseRequirement requirement) {
            this.requirement = requirement;
        }

        @Override public DataPullRuntimeReleaseRequirement requirement() { return requirement; }
        @Override public boolean verified() { return true; }
    }

    private static final class AllOperationSource implements ScheduleScopeSource {
        @Override
        public Set<OperationCode> operations() {
            return EnumSet.allOf(OperationCode.class);
        }

        @Override
        public ScheduleSourcePage readPage(
                OperationCode operation,
                String afterCursor,
                Instant reconcileUntil,
                int limit
        ) {
            throw new UnsupportedOperationException("context fixture never reconciles schedules");
        }
    }

    private static final class InertJob implements DataPullJob {
        private final OperationCode operation;

        private InertJob(OperationCode operation) { this.operation = operation; }
        @Override public OperationCode operationCode() { return operation; }
        @Override public String providerChannel() { return "context-smoke"; }
        @Override public String initialStep() { return "context-smoke"; }
        @Override public List<DataPullScope> listScopes() { return List.of(); }
        @Override public AdvanceResult advance(ExecutionContext context) {
            throw new UnsupportedOperationException("context fixture never advances jobs");
        }
    }
}
