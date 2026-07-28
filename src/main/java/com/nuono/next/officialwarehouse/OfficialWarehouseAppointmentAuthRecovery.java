package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
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
            Throwable failure
    ) {
        String rawFailure = failureMessage(failure);
        if (recoveryQueue == null
                || ownerUserId == null
                || !StringUtils.hasText(projectCode)
                || !StringUtils.hasText(storeCode)
                || !isExplicitAuthFailure(failure, rawFailure)) {
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

    boolean isExplicitAuthFailure(Throwable failure) {
        return isExplicitAuthFailure(failure, failureMessage(failure));
    }

    private boolean isExplicitAuthFailure(Throwable failure, String rawFailure) {
        if (NoonAuthenticationFailureClassifier.hasPermanentAuthenticationFailureEvidence(failure)) {
            return false;
        }
        return NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)
                || NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(rawFailure);
    }

    private String failureMessage(Throwable failure) {
        StringBuilder details = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                details.append(' ').append(current.getMessage());
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return details.toString().trim();
    }

    static final class AuthWait {
        final Long recoveryId;
        final int retrySeconds;
        final String errorStage;
        final String failureType;
        final String message;

        private AuthWait(Long recoveryId) {
            this.recoveryId = recoveryId;
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
