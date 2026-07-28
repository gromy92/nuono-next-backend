package com.nuono.next.competitoranalysis;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
class CompetitorRefreshLeaseGuard {
    private final CompetitorAnalysisMapper mapper;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    CompetitorRefreshLeaseGuard(CompetitorAnalysisMapper mapper) {
        this(mapper, Clock.systemUTC(), true);
    }

    CompetitorRefreshLeaseGuard(
            CompetitorAnalysisMapper mapper,
            Clock clock,
            boolean enabled
    ) {
        this.mapper = mapper;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.enabled = enabled;
    }

    static CompetitorRefreshLeaseGuard disabled(CompetitorAnalysisMapper mapper) {
        return new CompetitorRefreshLeaseGuard(mapper, Clock.systemUTC(), false);
    }

    void acquire(Long taskId, Long runId, Long watchProductId) {
        if (!enabled) {
            return;
        }
        if (taskId == null || runId == null || watchProductId == null) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Competitor refresh lease requires an active transaction."
            );
        }
        if (!Objects.equals(taskId, mapper.lockRunningRefreshTask(taskId))) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
        mapper.heartbeatRunningRefreshTask(taskId, LocalDateTime.now(clock));
        if (!Objects.equals(
                runId,
                mapper.lockRunningRefreshRun(taskId, runId, watchProductId)
        )) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
    }

    void requireMutation(int affectedRows, Long taskId, Long runId) {
        if (enabled && affectedRows != 1) {
            throw new CompetitorRefreshLeaseLostException(taskId, runId);
        }
    }
}
