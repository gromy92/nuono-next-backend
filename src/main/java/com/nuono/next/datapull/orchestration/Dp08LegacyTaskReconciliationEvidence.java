package com.nuono.next.datapull.orchestration;

import com.nuono.next.infrastructure.mapper.Dp08LegacyTaskReconciliationMapper;
import java.util.Objects;

/** Fail-closed proof that no legacy DP08 work can outlive the stopped predecessor. */
public final class Dp08LegacyTaskReconciliationEvidence
        implements DataPullRuntimeReleaseEvidence {
    private final Dp08LegacyTaskReconciliationMapper mapper;

    public Dp08LegacyTaskReconciliationEvidence(
            Dp08LegacyTaskReconciliationMapper mapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public DataPullRuntimeReleaseRequirement requirement() {
        return DataPullRuntimeReleaseRequirement.DP08_LEGACY_TASK_RECONCILIATION;
    }

    @Override
    public boolean verified() {
        try {
            return mapper.countActiveRows() == 0;
        } catch (RuntimeException invalidEvidence) {
            return false;
        }
    }
}
