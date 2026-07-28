package com.nuono.next.officialwarehouse;

import com.nuono.next.noonauth.NoonAuthRecoveryTriggerPolicy;
import com.nuono.next.noonauth.NoonProjectAuthRecoveryQueue;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class OfficialWarehouseAppointmentAuthRecovery {
    private static final int RETRY_SECONDS = 60;
    private static final String ERROR_STAGE = "AUTH_RECOVERY";
    private static final String FAILURE_TYPE = "AUTH_RECOVERY_PENDING";

    private final NoonProjectAuthRecoveryQueue recoveryQueue;
    private final NoonPullProjectAuthGate authGate;

    public OfficialWarehouseAppointmentAuthRecovery(
            NoonProjectAuthRecoveryQueue recoveryQueue,
            NoonPullProjectAuthGate authGate
    ) {
        this.recoveryQueue = recoveryQueue;
        this.authGate = authGate;
    }

    static OfficialWarehouseAppointmentAuthRecovery disabled() {
        return new OfficialWarehouseAppointmentAuthRecovery(
                (ownerUserId, projectCode, storeCode) -> Optional.empty(),
                (ownerUserId, projectCode) -> false
        );
    }

    AuthWait blockedWait(Long ownerUserId, String projectCode) {
        if (authGate == null
                || ownerUserId == null
                || !StringUtils.hasText(projectCode)
                || !authGate.isBlocked(ownerUserId, projectCode)) {
            return null;
        }
        return AuthWait.blocked();
    }

    AuthWait enqueue(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String rawFailure
    ) {
        if (recoveryQueue == null
                || ownerUserId == null
                || !StringUtils.hasText(projectCode)
                || !StringUtils.hasText(storeCode)
                || !NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(rawFailure)) {
            return null;
        }
        try {
            return recoveryQueue.enqueueProject(ownerUserId, projectCode, storeCode)
                    .map(AuthWait::queued)
                    .orElse(null);
        } catch (RuntimeException recoveryFailure) {
            return null;
        }
    }

    static final class AuthWait {
        final int retrySeconds;
        final String errorStage;
        final String failureType;
        final String message;

        private AuthWait(Long recoveryId) {
            retrySeconds = RETRY_SECONDS;
            errorStage = ERROR_STAGE;
            failureType = FAILURE_TYPE;
            message = recoveryId == null
                    ? "Noon Project 授权恢复中，恢复后将自动继续原约仓。"
                    : "Noon Project 授权恢复中，恢复后将自动继续原约仓；recoveryId=" + recoveryId;
        }

        static AuthWait blocked() {
            return new AuthWait(null);
        }

        static AuthWait queued(Long recoveryId) {
            return new AuthWait(recoveryId);
        }
    }
}
