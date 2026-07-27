package com.nuono.next.noonauth;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class LocalDbNoonProjectAuthStateSynchronizer
        implements NoonProjectAuthStateSynchronizer {

    private final NoonAuthRecoveryMapper mapper;

    public LocalDbNoonProjectAuthStateSynchronizer(
            NoonAuthRecoveryMapper mapper
    ) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void recordVerifiedProjectSession(
            Long ownerUserId,
            String projectCode
    ) {
        if (ownerUserId == null || !StringUtils.hasText(projectCode)) {
            throw new IllegalArgumentException(
                    "Noon Project identity is required."
            );
        }
        String normalizedProjectCode = projectCode.trim();
        NoonProjectAuthStateRecord state = mapper.selectProjectAuthStateForUpdate(
                ownerUserId,
                normalizedProjectCode
        );
        if (state == null
                || state.getStatus() == null
                || !state.getStatus().blocksProviderCalls()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.releaseProjectAfterManualReauthentication(
                ownerUserId,
                normalizedProjectCode,
                state.getAuthVersion(),
                state.getActiveRecoveryId(),
                now
        ) != 1) {
            throw new IllegalStateException(
                    "Noon Project auth state changed during listing reauthentication."
            );
        }
        Long recoveryId = state.getActiveRecoveryId();
        if (recoveryId == null) {
            return;
        }
        mapper.requeueProjectPullTasksAfterManualReauthentication(
                recoveryId,
                ownerUserId,
                normalizedProjectCode,
                now
        );
        mapper.recoverProjectItemsAfterManualReauthentication(
                recoveryId,
                ownerUserId,
                normalizedProjectCode,
                now
        );
        if (mapper.completeRecoveryAfterManualReauthenticationIfDrained(
                recoveryId,
                now
        ) == 1) {
            mapper.promoteSuccessorForPredecessor(recoveryId, now, now);
        }
    }
}
