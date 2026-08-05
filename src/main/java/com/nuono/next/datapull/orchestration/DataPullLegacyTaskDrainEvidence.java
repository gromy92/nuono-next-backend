package com.nuono.next.datapull.orchestration;

import com.nuono.next.infrastructure.mapper.DataPullLegacyCutoverMapper;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Fails closed until every retired task/waiting-task writer cohort is terminal. */
public final class DataPullLegacyTaskDrainEvidence
        implements DataPullRuntimeReleaseEvidence {
    enum Kind {
        NOON_PULL,
        DP05_OPERATIONAL_TASK,
        DP10_SYNC_TASK,
        SALES_SYNC_TASK,
        LEGACY_AUTH_WAIT
    }

    private final DataPullLegacyCutoverMapper mapper;

    public DataPullLegacyTaskDrainEvidence(DataPullLegacyCutoverMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.LEGACY_TASK_DRAIN;
    }

    @Override
    public boolean verified() {
        try {
            List<DataPullLegacyCutoverRow> rows = List.copyOf(Objects.requireNonNull(
                    mapper.selectActiveCohort(), "legacy cutover cohort"
            ));
            EnumSet<Kind> seen = EnumSet.noneOf(Kind.class);
            for (DataPullLegacyCutoverRow row : rows) {
                DataPullLegacyCutoverRow value = Objects.requireNonNull(row, "legacy row");
                Kind kind = Kind.valueOf(Objects.requireNonNull(
                        value.getRecordKind(), "legacy record kind"
                ));
                if (!seen.add(kind)
                        || value.getActiveCount() == null
                        || value.getActiveCount() != 0L
                        || value.getSupersedableSnapshotCount() == null
                        || value.getSupersedableSnapshotCount() != 0L) {
                    return false;
                }
            }
            return seen.equals(EnumSet.allOf(Kind.class));
        } catch (RuntimeException invalidEvidence) {
            return false;
        }
    }
}
