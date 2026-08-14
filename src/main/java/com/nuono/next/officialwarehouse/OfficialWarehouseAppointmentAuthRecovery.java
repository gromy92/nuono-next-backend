package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noonauth.NoonAuthRecoveryTriggerPolicy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class OfficialWarehouseAppointmentAuthRecovery {
    private static final String ERROR_STAGE = "AUTH_REQUIRED";
    private static final String FAILURE_TYPE = "MANUAL_LOGIN_REQUIRED";

    private final NoonAccountSessionAttentionPort accountSessionAttention;

    public OfficialWarehouseAppointmentAuthRecovery(
            NoonAccountSessionAttentionPort accountSessionAttention
    ) {
        this.accountSessionAttention = accountSessionAttention;
    }

    static OfficialWarehouseAppointmentAuthRecovery disabled() {
        return new OfficialWarehouseAppointmentAuthRecovery(null);
    }

    AuthWait blockedWait(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            Long appointmentId
    ) {
        if (accountSessionAttention == null || !accountSessionAttention.blocksProviderCalls()) {
            return null;
        }
        accountSessionAttention.requireManualLogin();
        return AuthWait.manualLoginRequired();
    }

    AuthWait enqueue(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            Long appointmentId,
            String rawFailure
    ) {
        if (accountSessionAttention == null
                || !NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(rawFailure)) {
            return null;
        }
        accountSessionAttention.requireManualLogin();
        return AuthWait.manualLoginRequired();
    }

    static final class AuthWait {
        final String errorStage;
        final String failureType;
        final String message;

        private AuthWait() {
            errorStage = ERROR_STAGE;
            failureType = FAILURE_TYPE;
            message = "Noon 共享账号需要人工登录；系统不会自动发送验证码或继续原约仓。";
        }

        static AuthWait manualLoginRequired() {
            return new AuthWait();
        }
    }
}
