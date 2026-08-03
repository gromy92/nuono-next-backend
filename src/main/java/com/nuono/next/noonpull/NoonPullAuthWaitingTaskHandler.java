package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonAuthRecoveryMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskHandler;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class NoonPullAuthWaitingTaskHandler implements NoonAuthWaitingTaskHandler {
    private final NoonAuthRecoveryMapper mapper;

    public NoonPullAuthWaitingTaskHandler(NoonAuthRecoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sourceDomain) {
        if ("NOON_PULL".equalsIgnoreCase(sourceDomain)) {
            return true;
        }
        if (sourceDomain == null) {
            return false;
        }
        try {
            NoonPullDataDomain.valueOf(sourceDomain.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        return mapper.requeueBlockedTaskAfterRecoveryCas(
                item.getSourceTaskId(),
                item.getRecoveryId(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                now
        ) == 1 ? NoonAuthWaitingTaskOutcome.RESUMED : NoonAuthWaitingTaskOutcome.STALE;
    }

    @Override
    public NoonAuthWaitingTaskOutcome fail(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        return mapper.failBlockedTaskAfterRecovery(
                item.getSourceTaskId(),
                item.getRecoveryId(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                failureCode,
                diagnostic,
                now
        ) == 1 ? NoonAuthWaitingTaskOutcome.MANUAL_REVIEW : NoonAuthWaitingTaskOutcome.STALE;
    }
}
