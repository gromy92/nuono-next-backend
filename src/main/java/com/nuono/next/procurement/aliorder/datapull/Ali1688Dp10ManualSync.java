package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.persistence.DataPullTaskStore;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderManualSync;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderOAuthService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Admits an immediate owner-scoped request into the DP-10 state machine. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class Ali1688Dp10ManualSync implements Ali1688HistoricalOrderManualSync {
    private final Ali1688HistoricalOrderMapper authorizations;
    private final DataPullTaskStore tasks;
    private final Clock clock;

    @Autowired
    public Ali1688Dp10ManualSync(
            Ali1688HistoricalOrderMapper authorizations,
            DataPullTaskStore tasks
    ) {
        this(authorizations, tasks, Clock.systemUTC());
    }

    Ali1688Dp10ManualSync(
            Ali1688HistoricalOrderMapper authorizations,
            DataPullTaskStore tasks,
            Clock clock
    ) {
        this.authorizations = Objects.requireNonNull(authorizations, "authorizations");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean request(Long ownerUserId, Long authorizationId, Long operatorUserId) {
        if (ownerUserId == null || ownerUserId <= 0L
                || authorizationId == null || authorizationId <= 0L
                || operatorUserId == null || operatorUserId <= 0L) {
            return false;
        }
        Ali1688HistoricalOrderAuthorizationRow authorization =
                authorizations.selectAuthorizationById(ownerUserId, authorizationId);
        if (!isEligible(ownerUserId, authorizationId, authorization)) return false;
        LocalDateTime nowUtc = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        String scopeKey = Ali1688Dp10ScopeIdentity.scopeKey(authorization);
        String windowKey = "DP10:manual:" + nowUtc + ":operator:" + operatorUserId;
        DataPullTask queued = DataPullTask.queued(
                tasks.nextTaskId(),
                OperationCode.DP10,
                Ali1688Dp10ScopeIdentity.PROVIDER_CHANNEL,
                ownerUserId,
                null,
                Ali1688Dp10ScopeIdentity.accountKey(authorization),
                null,
                null,
                null,
                null,
                scopeKey,
                nowUtc,
                windowKey,
                Ali1688Dp10Job.INITIAL_STEP,
                nowUtc
        );
        tasks.enqueue(queued);
        return true;
    }

    private static boolean isEligible(
            Long ownerUserId,
            Long authorizationId,
            Ali1688HistoricalOrderAuthorizationRow authorization
    ) {
        return authorization != null
                && authorizationId.equals(authorization.getId())
                && ownerUserId.equals(authorization.getOwnerUserId())
                && Ali1688HistoricalOrderOAuthService.PROVIDER_CODE.equals(
                        authorization.getProviderCode())
                && "authorized".equalsIgnoreCase(authorization.getStatus())
                && authorization.getRevokedAt() == null;
    }
}
