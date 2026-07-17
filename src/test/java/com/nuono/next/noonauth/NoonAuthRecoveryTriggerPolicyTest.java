package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NoonAuthRecoveryTriggerPolicyTest {

    @Test
    void acceptsOnlyExplicitCookieOrIdentityExpirySignals() {
        assertTrue(NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(
                "auth_required: Noon Cookie 无效或已过期; project=PRJ1"
        ));
        assertTrue(NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry("whoami HTTP 307 redirect"));
        assertTrue(NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry("HTTP 401 unauthorized"));
        assertFalse(NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry("HTTP 503 provider unavailable"));
    }

    @Test
    void projectAccessFailureDoesNotBurnAnotherOtpGeneration() {
        assertFalse(NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(
                "auth_required: account does not contain current project PRJ404"
        ));
        assertFalse(NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry("账号不包含当前项目"));
    }
}
