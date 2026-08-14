package com.nuono.next.noon;

import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Compatibility boundary for callers that used automatic authorization recovery.
 *
 * <p>It performs no persistence, OTP, mailbox access, task replay, or provider request. A real
 * auth failure only asks the shared account administrator to complete the manual login flow.</p>
 */
@Component
@Profile("local-db")
final class NoonAccountSessionAttention implements
        NoonAccountSessionAttentionPort,
        NoonAuthWaitQueue,
        NoonPullProjectAuthGate {
    private final ObjectProvider<NoonAccountManualOtpService> manualOtpServiceProvider;

    NoonAccountSessionAttention(ObjectProvider<NoonAccountManualOtpService> manualOtpServiceProvider) {
        this.manualOtpServiceProvider = manualOtpServiceProvider;
    }

    @Override
    public Optional<Long> enqueue(NoonAuthWaitRequest request) {
        requireManualLogin();
        return Optional.empty();
    }

    @Override
    public boolean isBlocked(Long ownerUserId, String projectCode) {
        return blocksProviderCalls();
    }

    @Override
    public void requireManualLogin() {
        manualOtpService().recordAuthenticationRequired();
    }

    @Override
    public boolean blocksProviderCalls() {
        return manualOtpService().blocksProviderCalls();
    }

    private NoonAccountManualOtpService manualOtpService() {
        NoonAccountManualOtpService service = manualOtpServiceProvider.getIfAvailable();
        if (service == null) {
            throw new IllegalStateException("Noon 单账号人工登录服务不可用。");
        }
        return service;
    }
}
