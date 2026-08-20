package com.nuono.next.noonauth;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import org.springframework.util.StringUtils;

final class NoonAuthRecoveryConfigurationValidator {
    private NoonAuthRecoveryConfigurationValidator() {
    }

    static void validate(
            NoonAuthRecoveryProperties properties,
            NoonAuthRecoveryGateway gateway,
            String configuredIdentityKey,
            String configuredFingerprint
    ) {
        if (!properties.isEnabled()) {
            return;
        }
        boolean hasAllowlist = !properties.normalizedProjectAllowlist().isEmpty();
        if (!properties.isAllProjectsEnabled() && !hasAllowlist) {
            throw new IllegalStateException(
                    "Noon auth recovery requires an explicit Project allowlist or full-project mode."
            );
        }
        if (properties.isAllProjectsEnabled()
                && (!properties.isSessionAuditEnabled() || !properties.isStartupAuditEnabled())) {
            throw new IllegalStateException(
                    "Noon auth recovery full-project mode requires startup and daily session audits."
            );
        }
        if (!StringUtils.hasText(configuredIdentityKey)) {
            throw new IllegalStateException("Noon auth recovery requires a configured shared mailbox email.");
        }
        if (!StringUtils.hasText(configuredFingerprint)) {
            throw new IllegalStateException("Noon auth recovery requires a configured mailbox credential.");
        }
        if (properties.normalizedTrustedSenderDomains().isEmpty()) {
            throw new IllegalStateException("Noon auth recovery requires trusted sender domains.");
        }
        if (gateway != null
                && gateway.requiresCheckpointSecret()
                && !StringUtils.hasText(properties.getCheckpointCipherSecret())) {
            throw new IllegalStateException("Noon auth recovery requires a checkpoint cipher secret.");
        }
        if (gateway == null) {
            throw new IllegalStateException("Noon auth recovery gateway is not configured.");
        }
    }
}
