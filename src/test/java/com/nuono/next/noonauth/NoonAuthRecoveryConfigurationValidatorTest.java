package com.nuono.next.noonauth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.nuono.next.noonauth.gateway.NoonAuthRecoveryGateway;
import org.junit.jupiter.api.Test;

class NoonAuthRecoveryConfigurationValidatorTest {

    @Test
    void refusesImplicitOrPartiallyEnabledFullProjectScope() {
        NoonAuthRecoveryGateway gateway = mock(NoonAuthRecoveryGateway.class);
        NoonAuthRecoveryProperties properties = enabledProperties();

        assertThrows(IllegalStateException.class, () -> validate(properties, gateway));

        properties.setAllProjectsEnabled(true);
        assertThrows(IllegalStateException.class, () -> validate(properties, gateway));

        properties.setStartupAuditEnabled(true);
        assertDoesNotThrow(() -> validate(properties, gateway));

        properties.setProjectAllowlist("PRJ100");
        assertDoesNotThrow(() -> validate(properties, gateway));
        assertTrue(properties.allowsProject("PRJ200"));
    }

    @Test
    void projectAdmissionRequiresAnExplicitAllowlistOrFullMode() {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();

        assertFalse(properties.allowsProject("PRJ100"));

        properties.setProjectAllowlist("PRJ100");
        assertTrue(properties.allowsProject("prj100"));
        assertFalse(properties.allowsProject("PRJ200"));

        properties.setProjectAllowlist(null);
        properties.setAllProjectsEnabled(true);
        assertTrue(properties.allowsProject("PRJ200"));
    }

    private static NoonAuthRecoveryProperties enabledProperties() {
        NoonAuthRecoveryProperties properties = new NoonAuthRecoveryProperties();
        properties.setEnabled(true);
        properties.setTrustedSenderDomains("noon.com");
        return properties;
    }

    private static void validate(
            NoonAuthRecoveryProperties properties,
            NoonAuthRecoveryGateway gateway
    ) {
        NoonAuthRecoveryConfigurationValidator.validate(
                properties,
                gateway,
                "shared@example.com",
                "configured-fingerprint"
        );
    }
}
