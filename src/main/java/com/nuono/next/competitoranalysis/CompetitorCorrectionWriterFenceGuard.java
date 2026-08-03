package com.nuono.next.competitoranalysis;

import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
class CompetitorCorrectionWriterFenceGuard {
    static final String ACTIVE_CODE = "COMPETITOR_CORRECTION_MAINTENANCE_ACTIVE";
    static final String UNAVAILABLE_CODE =
            "COMPETITOR_CORRECTION_MAINTENANCE_FENCE_UNAVAILABLE";
    private static final String OPEN = "OPEN";
    private static final String ACTIVE = "ACTIVE";

    private final CompetitorCorrectionFenceMapper mapper;
    private final boolean enabled;

    @Autowired
    CompetitorCorrectionWriterFenceGuard(CompetitorCorrectionFenceMapper mapper) {
        this(Objects.requireNonNull(mapper, "mapper"), true);
    }

    private CompetitorCorrectionWriterFenceGuard(
            CompetitorCorrectionFenceMapper mapper,
            boolean enabled
    ) {
        this.mapper = mapper;
        this.enabled = enabled;
    }

    static CompetitorCorrectionWriterFenceGuard disabled() {
        return new CompetitorCorrectionWriterFenceGuard(null, false);
    }

    void acquireForWrite() {
        if (!enabled) {
            return;
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "COMPETITOR_CORRECTION_WRITER_FENCE_TRANSACTION_REQUIRED"
            );
        }
        assertOpen(lockStatus());
    }

    private String lockStatus() {
        try {
            return mapper.lockCompetitorCorrectionWriterFence();
        } catch (RuntimeException exception) {
            throw unavailable(exception);
        }
    }

    private void assertOpen(String status) {
        if (OPEN.equals(status)) {
            return;
        }
        if (ACTIVE.equals(status)) {
            throw new CompetitorCorrectionMaintenanceException(ACTIVE_CODE);
        }
        throw unavailable(null);
    }

    private CompetitorCorrectionMaintenanceException unavailable(
            RuntimeException cause
    ) {
        return cause == null
                ? new CompetitorCorrectionMaintenanceException(UNAVAILABLE_CODE)
                : new CompetitorCorrectionMaintenanceException(
                        UNAVAILABLE_CODE, cause
                );
    }
}
