package com.nuono.next.officialwarehouse;

import com.nuono.next.infrastructure.mapper.NoonAuthOfficialWarehouseTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskHandler;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class OfficialWarehouseAuthWaitingTaskHandler implements NoonAuthWaitingTaskHandler {
    private final NoonAuthOfficialWarehouseTaskMapper mapper;

    public OfficialWarehouseAuthWaitingTaskHandler(NoonAuthOfficialWarehouseTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sourceDomain) {
        return "OFFICIAL_WAREHOUSE_APPOINTMENT".equalsIgnoreCase(sourceDomain);
    }

    @Override
    public NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        return mapper.resumeAfterAuthorization(
                item.getId(),
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
        return mapper.failAuthorizationRecovery(
                item.getId(),
                item.getRecoveryId(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                diagnostic,
                now
        ) == 1 ? NoonAuthWaitingTaskOutcome.MANUAL_REVIEW : NoonAuthWaitingTaskOutcome.STALE;
    }
}
