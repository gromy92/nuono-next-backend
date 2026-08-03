package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.NoonAuthProductTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitingTaskHandler;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class ProductAuthWaitingTaskHandler implements NoonAuthWaitingTaskHandler {
    private final NoonAuthProductTaskMapper mapper;

    public ProductAuthWaitingTaskHandler(NoonAuthProductTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sourceDomain) {
        return "PRODUCT_DELETE".equalsIgnoreCase(sourceDomain)
                || "PRODUCT_PUBLISH".equalsIgnoreCase(sourceDomain);
    }

    @Override
    public NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        if (item.getResumePolicy() == NoonAuthResumePolicy.READBACK_REQUIRED) {
            return NoonAuthWaitingTaskOutcome.MANUAL_REVIEW;
        }
        if (item.getResumePolicy() != NoonAuthResumePolicy.AUTO_RESUME) {
            return NoonAuthWaitingTaskOutcome.STALE;
        }
        return mapper.resumeSafeProductTask(
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
        return mapper.failProductTask(
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
