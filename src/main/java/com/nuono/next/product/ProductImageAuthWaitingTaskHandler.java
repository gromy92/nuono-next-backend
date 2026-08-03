package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductImageProfileMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskHandler;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class ProductImageAuthWaitingTaskHandler implements NoonAuthWaitingTaskHandler {
    private final ProductImageProfileMapper mapper;

    public ProductImageAuthWaitingTaskHandler(ProductImageProfileMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sourceDomain) {
        return "PRODUCT_IMAGE_SUITE".equalsIgnoreCase(sourceDomain);
    }

    @Override
    public NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        return mapper.markSuiteAuthorizationRecovered(
                item.getId(),
                item.getRecoveryId(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                now
        ) == 1 ? NoonAuthWaitingTaskOutcome.MANUAL_REVIEW : NoonAuthWaitingTaskOutcome.STALE;
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
        return mapper.markSuiteAuthorizationRecoveryFailed(
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
