package com.nuono.next.datapull.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.ReportCreateAttemptMapper;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

class ReportCreateAttemptFenceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 2, 12, 0);

    @Test
    void successfulCasMovesTheClaimedTaskToReadbackBeforeTheRemoteCreate() {
        CapturingMapper mapper = new CapturingMapper(1);
        MyBatisReportCreateAttemptFence fence = new MyBatisReportCreateAttemptFence(mapper);
        DataPullTask task = runningTask();
        ExportReportCheckpoint checkpoint = ExportReportCheckpoint.at(
                ExportReportCheckpoint.Phase.RECONCILE_CREATE,
                "request-101"
        ).unknownCreateOutcome();

        boolean prepared = fence.prepareReadbackBeforeCreate(task, checkpoint, NOW);

        assertThat(prepared).isTrue();
        assertThat(mapper.taskId).isEqualTo(101L);
        assertThat(mapper.fenceEpoch).isEqualTo(7L);
        assertThat(mapper.version).isEqualTo(11L);
        assertThat(mapper.leaseOwner).isEqualTo("worker-a");
        assertThat(mapper.expectedCheckpoint).isEqualTo("old-checkpoint");
        assertThat(mapper.reconcileStep).isEqualTo("REPORT_RECONCILE_CREATE");
        ExportReportCheckpoint persisted = new ExportReportCheckpointCodec()
                .decode(mapper.reconcileCheckpoint);
        assertThat(persisted.getPhase())
                .isEqualTo(ExportReportCheckpoint.Phase.RECONCILE_CREATE);
        assertThat(persisted.getStableRequestKey()).isEqualTo("request-101");
        assertThat(persisted.isCreateOutcomeUnknown()).isTrue();
        assertThat(mapper.reconcileCheckpoint).doesNotContain("request-101");
        assertThat(task.getStepCode()).isEqualTo("REPORT_RECONCILE_CREATE");
        assertThat(task.getCheckpoint()).isEqualTo(mapper.reconcileCheckpoint);
        assertThat(task.getVersion()).isEqualTo(12L);
        assertThat(task.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void staleCasDoesNotMutateTheInMemoryClaim() {
        MyBatisReportCreateAttemptFence fence = new MyBatisReportCreateAttemptFence(
                new CapturingMapper(0)
        );
        DataPullTask task = runningTask();

        boolean prepared = fence.prepareReadbackBeforeCreate(
                task,
                ExportReportCheckpoint.at(
                        ExportReportCheckpoint.Phase.RECONCILE_CREATE,
                        "request-101"
                ),
                NOW
        );

        assertThat(prepared).isFalse();
        assertThat(task.getStepCode()).isEqualTo("REPORT_CREATE");
        assertThat(task.getCheckpoint()).isEqualTo("old-checkpoint");
        assertThat(task.getVersion()).isEqualTo(11L);
    }

    @Test
    void invalidChangedRowCountIsAnUnknownCommitOutcome() {
        MyBatisReportCreateAttemptFence fence = new MyBatisReportCreateAttemptFence(
                new CapturingMapper(2)
        );

        assertThatThrownBy(() -> fence.prepareReadbackBeforeCreate(
                runningTask(),
                ExportReportCheckpoint.at(
                        ExportReportCheckpoint.Phase.RECONCILE_CREATE,
                        "request-101"
                ),
                NOW
        )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mapperCasRequiresTheExactLiveClaimAndOldCheckpoint() throws Exception {
        Method method = ReportCreateAttemptMapper.class.getMethod(
                "prepareReadbackBeforeCreate",
                long.class,
                long.class,
                long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                LocalDateTime.class
        );
        String sql = String.join(" ", method.getAnnotation(Update.class).value())
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("state = 'RUNNING'")
                .contains("fence_epoch = #{fenceEpoch}")
                .contains("version_no = #{version}")
                .contains("BINARY lease_owner = BINARY #{leaseOwner}")
                .contains("lease_until > #{nowUtc}")
                .contains("BINARY checkpoint = BINARY #{expectedCheckpoint}")
                .contains("version_no = version_no + 1");
    }

    private DataPullTask runningTask() {
        DataPullTask task = new DataPullTask();
        task.setId(101L);
        task.setFenceEpoch(7L);
        task.setVersion(11L);
        task.setLeaseOwner("worker-a");
        task.setStepCode("REPORT_CREATE");
        task.setCheckpoint("old-checkpoint");
        return task;
    }

    private static final class CapturingMapper implements ReportCreateAttemptMapper {
        private final int changed;
        private long taskId;
        private long fenceEpoch;
        private long version;
        private String leaseOwner;
        private String expectedCheckpoint;
        private String reconcileStep;
        private String reconcileCheckpoint;

        private CapturingMapper(int changed) {
            this.changed = changed;
        }

        @Override
        public int prepareReadbackBeforeCreate(
                long taskId,
                long fenceEpoch,
                long version,
                String leaseOwner,
                String expectedCheckpoint,
                String reconcileStep,
                String reconcileCheckpoint,
                LocalDateTime nowUtc
        ) {
            this.taskId = taskId;
            this.fenceEpoch = fenceEpoch;
            this.version = version;
            this.leaseOwner = leaseOwner;
            this.expectedCheckpoint = expectedCheckpoint;
            this.reconcileStep = reconcileStep;
            this.reconcileCheckpoint = reconcileCheckpoint;
            return changed;
        }
    }
}
