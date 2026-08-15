package com.nuono.next.noon;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Boundary for callers that must stop when the shared Noon account needs manual login.
 *
 * <p>It performs no persistence, OTP, mailbox access, task replay, or provider request. A real
 * auth failure only asks the shared account administrator to complete the manual login flow.</p>
 */
@Component
@Profile("local-db")
final class NoonAccountSessionAttention implements NoonAccountSessionAttentionPort {
    private final ObjectProvider<NoonAccountManualOtpService> manualOtpServiceProvider;

    NoonAccountSessionAttention(ObjectProvider<NoonAccountManualOtpService> manualOtpServiceProvider) {
        this.manualOtpServiceProvider = manualOtpServiceProvider;
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
