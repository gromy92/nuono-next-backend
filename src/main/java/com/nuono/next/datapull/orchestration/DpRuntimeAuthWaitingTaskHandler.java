package com.nuono.next.datapull.orchestration;

import com.nuono.next.infrastructure.mapper.DataPullAuthWaitingTaskMapper;
import com.nuono.next.noonauth.NoonAuthRecoveryItemRecord;
import com.nuono.next.noonauth.NoonAuthRecoveryStatus;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitingTaskHandler;
import com.nuono.next.noonauth.NoonAuthWaitingTaskOutcome;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Version-fenced DP task transition owned by the shared Noon authorization worker. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class DpRuntimeAuthWaitingTaskHandler implements NoonAuthWaitingTaskHandler {
    static final String MANUAL_REVIEW_CODE = "AUTH_MANUAL_REVIEW";
    private static final String LEGACY_SOURCE_DOMAIN = "DP_RUNTIME";

    private final DataPullAuthWaitingTaskMapper mapper;

    public DpRuntimeAuthWaitingTaskHandler(DataPullAuthWaitingTaskMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public boolean supports(String sourceDomain) {
        return LEGACY_SOURCE_DOMAIN.equalsIgnoreCase(sourceDomain);
    }

    @Override
    public NoonAuthWaitingTaskOutcome resume(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now
    ) {
        Long waitingTaskVersion = validatedWaitingTaskVersion(item);
        if (waitingTaskVersion == null || !hasLiveRecoveryFence(
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                now,
                true
        )) {
            return NoonAuthWaitingTaskOutcome.STALE;
        }
        return mapper.resumeAfterAuthorization(
                item.getId(),
                item.getRecoveryId(),
                item.getSourceTaskId(),
                waitingTaskVersion,
                item.getSourceCheckpoint(),
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
        Long waitingTaskVersion = validatedWaitingTaskVersion(item);
        if (waitingTaskVersion == null || !hasLiveRecoveryFence(
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                now,
                false
        )) {
            return NoonAuthWaitingTaskOutcome.STALE;
        }
        return mapper.holdAuthorizationManualReview(
                item.getId(),
                item.getRecoveryId(),
                item.getSourceTaskId(),
                waitingTaskVersion,
                item.getSourceCheckpoint(),
                recoveryStatus,
                recoveryVersion,
                leaseToken,
                MANUAL_REVIEW_CODE,
                now
        ) == 1 ? NoonAuthWaitingTaskOutcome.MANUAL_REVIEW : NoonAuthWaitingTaskOutcome.STALE;
    }

    @Override
    public NoonAuthWaitingTaskOutcome hold(
            NoonAuthRecoveryItemRecord item,
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            String failureCode,
            String diagnostic,
            LocalDateTime now
    ) {
        return fail(
                item, recoveryStatus, recoveryVersion, leaseToken,
                failureCode, diagnostic, now
        );
    }

    private Long validatedWaitingTaskVersion(NoonAuthRecoveryItemRecord item) {
        if (item == null
                || item.getId() == null
                || item.getRecoveryId() == null
                || item.getSourceTaskId() == null
                || item.getOwnerUserId() == null
                || item.getOwnerUserId() <= 0L
                || !hasText(item.getProjectCode())
                || !hasText(item.getStoreCode())
                || !hasText(item.getSiteCode())
                || !LEGACY_SOURCE_DOMAIN.equals(item.getSourceDomain())
                || item.getResumePolicy() != NoonAuthResumePolicy.AUTO_RESUME) {
            return null;
        }
        String checkpoint = item.getSourceCheckpoint();
        if (checkpoint == null
                || checkpoint.isEmpty()
                || (checkpoint.length() > 1 && checkpoint.charAt(0) == '0')) {
            return null;
        }
        for (int index = 0; index < checkpoint.length(); index++) {
            char value = checkpoint.charAt(index);
            if (value < '0' || value > '9') {
                return null;
            }
        }
        try {
            long version = Long.parseLong(checkpoint);
            return version < 0 ? null : version;
        } catch (NumberFormatException invalidCheckpoint) {
            return null;
        }
    }

    private boolean hasLiveRecoveryFence(
            NoonAuthRecoveryStatus recoveryStatus,
            Long recoveryVersion,
            String leaseToken,
            LocalDateTime now,
            boolean resuming
    ) {
        return recoveryStatus != null
                && recoveryStatus.isActive()
                && (!resuming || recoveryStatus == NoonAuthRecoveryStatus.RECOVERING_PULLS)
                && recoveryVersion != null
                && recoveryVersion >= 0L
                && leaseToken != null
                && !leaseToken.isBlank()
                && now != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
