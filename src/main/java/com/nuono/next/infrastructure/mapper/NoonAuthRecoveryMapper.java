package com.nuono.next.infrastructure.mapper;

/** Aggregate MyBatis seam for the complete Noon authentication recovery persistence contract. */
public interface NoonAuthRecoveryMapper extends
        NoonAuthRateLimitRecoveryMapper,
        NoonAuthRecoveryQueueMapper,
        NoonAuthRecoveryTransitionMapper,
        NoonAuthRecoveryConfigEpochMapper,
        NoonAuthRecoverySendLedgerMapper,
        NoonAuthRecoveryBindingEpochMapper,
        NoonAuthRecoveryProjectStateMapper,
        NoonAuthRecoveryItemMapper {
}
