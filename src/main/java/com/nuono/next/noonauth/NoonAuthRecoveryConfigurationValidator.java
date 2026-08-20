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
