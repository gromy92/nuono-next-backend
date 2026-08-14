package com.nuono.next.noon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class NoonSessionGatewayManualOtpGatewayTest {

    @Test
    void manualOtpUsesLoginEmailOnlyAndNeverRequiresMailboxPollingConfiguration() {
        NoonSessionGateway sessionGateway = mock(NoonSessionGateway.class);
        when(sessionGateway.configuredMerchantLoginEmail()).thenReturn("merchant@example.com");

        NoonAccountManualOtpGateway.PreparedChallenge challenge =
                new NoonSessionGatewayManualOtpGateway(sessionGateway).sendOneManualOtp();

        assertThat(challenge).isNotNull();
        verify(sessionGateway).configuredMerchantLoginEmail();
        verify(sessionGateway).prepareEmailOtpGeneration("merchant@example.com");
        verify(sessionGateway).sendEmailOtp(null);
        verify(sessionGateway, never()).configuredMerchantEmail();
    }
}
