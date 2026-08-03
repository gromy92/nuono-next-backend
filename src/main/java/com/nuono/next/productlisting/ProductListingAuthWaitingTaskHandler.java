package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingAuthRecoveryMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthWaitingTaskHandler;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-db")
public class ProductListingAuthWaitingTaskHandler implements NoonAuthWaitingTaskHandler {
    private final ProductListingAuthRecoveryMapper mapper;

    public ProductListingAuthWaitingTaskHandler(ProductListingAuthRecoveryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(String sourceDomain) {
        return "PRODUCT_LISTING".equalsIgnoreCase(sourceDomain);
    }

    @Override
    public NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        return mapper.markTaskAuthorizationRecovered(
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
        return mapper.markTaskAuthorizationRecoveryFailed(
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
