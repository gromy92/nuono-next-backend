package com.nuono.next.datapull.report;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.ReportCreateAttemptMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/** Production create-intent Implementation; no external call runs inside this transaction. */
public class MyBatisReportCreateAttemptFence implements ReportCreateAttemptFence {
    private static final String RECONCILE_STEP = "REPORT_RECONCILE_CREATE";

    private final ReportCreateAttemptMapper mapper;
    private final ExportReportCheckpointCodec checkpointCodec = new ExportReportCheckpointCodec();

    public MyBatisReportCreateAttemptFence(ReportCreateAttemptMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    @Transactional
    public boolean prepareReadbackBeforeCreate(
            DataPullTask task,
            ExportReportCheckpoint reconcileCheckpoint,
            LocalDateTime nowUtc
    ) {
        DataPullTask claimed = Objects.requireNonNull(task, "task");
        ExportReportCheckpoint checkpoint = Objects.requireNonNull(
                reconcileCheckpoint,
                "reconcileCheckpoint"
        );
        LocalDateTime now = Objects.requireNonNull(nowUtc, "nowUtc");
        if (checkpoint.getPhase() != ExportReportCheckpoint.Phase.RECONCILE_CREATE) {
            throw new IllegalArgumentException("report create fence requires reconcile phase");
        }
        String encoded = checkpointCodec.encode(checkpoint);
        int changed = mapper.prepareReadbackBeforeCreate(
                claimed.getId(),
                claimed.getFenceEpoch(),
                claimed.getVersion(),
                claimed.getLeaseOwner(),
                claimed.getCheckpoint(),
                RECONCILE_STEP,
                encoded,
                now
        );
        if (changed == 0) {
            return false;
        }
        if (changed != 1) {
            throw new IllegalStateException("report create fence changed an invalid row count");
        }
        claimed.setStepCode(RECONCILE_STEP);
        claimed.setCheckpoint(encoded);
        claimed.setVersion(Math.addExact(claimed.getVersion(), 1L));
        claimed.setUpdatedAt(now);
        return true;
    }
}
