package com.nuono.next.noon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonAccountSessionDailyVerifierTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validExistingProjectSessionsBecomeActiveWithoutSendingOrValidatingOtp() throws Exception {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonAccountManualOtpService service = manualService();
        NoonAccountSessionProjectTarget target = target("PRJ307", "STR307", "sid=existing");
        when(mapper.listBoundProjects()).thenReturn(List.of(target));
        when(gateway.configuredMerchantLoginEmail()).thenReturn("merchant@example.com");
        when(gateway.whoamiWithCookie(anyString(), anyString(), anyString())).thenReturn(
                objectMapper.readTree("{\"projectCode\":\"PRJ307\"}")
        );

        new NoonAccountSessionDailyVerifier(mapper, gateway, service).verifyNow();

        assertThat(service.status().getStatus()).isEqualTo(NoonAccountSessionStatus.ACTIVE);
        verify(gateway).validateCatalogSessionWithCookie(
                "sid=existing", "PRJ307", "STR307", "merchant@example.com"
        );
    }

    @Test
    void invalidSessionRequiresManualOtpAndNeverSendsOneAutomatically() throws Exception {
        NoonAccountSessionMapper mapper = mock(NoonAccountSessionMapper.class);
        NoonSessionGateway gateway = mock(NoonSessionGateway.class);
        NoonAccountManualOtpService service = manualService();
        when(mapper.listBoundProjects()).thenReturn(List.of(target("PRJ307", "STR307", "sid=expired")));
        when(gateway.configuredMerchantLoginEmail()).thenReturn("merchant@example.com");
        when(gateway.whoamiWithCookie(anyString(), anyString(), anyString())).thenReturn(
                objectMapper.readTree("{\"projectCode\":\"PRJ307\"}")
        );
        doThrow(new IllegalStateException("expired")).when(gateway).validateCatalogSessionWithCookie(
                anyString(), anyString(), anyString(), anyString()
        );

        new NoonAccountSessionDailyVerifier(mapper, gateway, service).verifyNow();

        assertThat(service.status().getStatus()).isEqualTo(NoonAccountSessionStatus.MANUAL_OTP_REQUIRED);
    }

    private static NoonAccountManualOtpService manualService() {
        return new NoonAccountManualOtpService(
                new NoonAccountManualOtpGateway() {
                    @Override
                    public PreparedChallenge sendOneManualOtp() {
                        throw new AssertionError("daily check must not send OTP");
                    }

                    @Override
                    public AuthenticatedGrant validateSubmittedOtp(PreparedChallenge challenge, String otpCode) {
                        throw new AssertionError("daily check must not validate OTP");
                    }

                    @Override
                    public VerifiedProjectSession createVerifiedProjectSession(
                            AuthenticatedGrant grant, String projectCode, String storeCode
                    ) {
                        throw new AssertionError("daily check must not create a Project session");
                    }
                },
                (grant, operatorUserId) -> new NoonAccountProjectSessionRefresher.RefreshResult(0, 0)
        );
    }

    private static NoonAccountSessionProjectTarget target(String projectCode, String storeCode, String cookie) {
        NoonAccountSessionProjectTarget target = new NoonAccountSessionProjectTarget();
        target.setOwnerUserId(307L);
        target.setProjectCode(projectCode);
        target.setStoreCode(storeCode);
        target.setSessionCookie(cookie);
        return target;
    }
}
