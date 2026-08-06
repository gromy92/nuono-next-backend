package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.checkpoint.DataPullScopeProgressCommit;
import com.nuono.next.datapull.checkpoint.DataPullScopeProgressStore;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderAuthorizationRow;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Localizes the short fence/authorization/high-water CAS invariants. */
public final class Ali1688Dp10FactCommitGuard {
    private final Ali1688Dp10RuntimeMapper runtimeMapper;
    private final DataPullScopeProgressStore progressStore;

    public Ali1688Dp10FactCommitGuard(
            Ali1688Dp10RuntimeMapper runtimeMapper,
            DataPullScopeProgressStore progressStore
    ) {
        this.runtimeMapper = runtimeMapper;
        this.progressStore = progressStore;
    }

    public Ali1688HistoricalOrderAuthorizationRow lock(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command
    ) {
        Ali1688Dp10FenceGuard.requireLive(
                task, runtimeMapper.lockTask(task.getId()), command.getNowUtc());
        String providerAccountId = Ali1688Dp10ScopeIdentity.providerAccountId(
                command.getAuthorization());
        Ali1688HistoricalOrderAuthorizationRow authorization =
                runtimeMapper.lockEffectiveOpenApiAuthorization(
                        task.getOwnerUserId(), providerAccountId);
        if (authorization == null
                || !Ali1688Dp10ScopeIdentity.accountKey(authorization).equals(task.getAccountKey())
                || !Ali1688Dp10ScopeIdentity.scopeKey(authorization).equals(task.getScopeKey())) {
            throw new Ali1688Dp10AuthorizationUnavailableException();
        }
        return authorization;
    }

    public void commitHighWater(DataPullTask task, Ali1688Dp10ApplyCommand command) {
        LocalDateTime highWaterUtc = LocalDateTime.ofInstant(
                command.getWindowEnd(), ZoneOffset.UTC);
        DataPullScopeProgressCommit commit = new DataPullScopeProgressCommit(
                task, command.getExpectedProgressVersion(), true,
                highWaterUtc, command.getNowUtc());
        if (!progressStore.commitCompletedWindow(commit).isPresent()) {
            throw new Ali1688Dp10ProgressConflictException();
        }
    }
}
