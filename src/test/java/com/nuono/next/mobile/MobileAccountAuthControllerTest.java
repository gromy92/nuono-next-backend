package com.nuono.next.mobile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthLoginCommand;
import com.nuono.next.auth.AuthLoginResult;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.auth.LocalDbAuthService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

class MobileAccountAuthControllerTest {

    @Test
    void issuesBearerSessionFromTheExistingAccountPasswordContract() {
        LocalDbAuthService accountAuthService = mock(LocalDbAuthService.class);
        AuthSessionTokenService sessionTokenService = mock(AuthSessionTokenService.class);
        AuthLoginResult loginResult = loginResult();
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo("warehouse001");
        command.setPassword("secret");
        when(accountAuthService.login(command)).thenReturn(loginResult);
        when(sessionTokenService.issue(loginResult)).thenReturn("signed-mobile-token");
        when(sessionTokenService.getTtlSeconds()).thenReturn(600L);

        Map<String, Object> payload = controller(accountAuthService, sessionTokenService).accountLogin(command);

        assertThat(payload)
                .containsEntry("success", true)
                .containsEntry("tokenType", "Bearer")
                .containsEntry("accessToken", "signed-mobile-token")
                .containsEntry("expiresInSeconds", 600L)
                .containsEntry("session", loginResult);
        assertThat(payload.get("expiresAt")).isInstanceOf(String.class);
    }

    @Test
    void verifiesBearerSessionWithoutTrustingClientIdentityFields() {
        AuthSessionTokenService sessionTokenService = mock(AuthSessionTokenService.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request))
                .thenReturn(new AuthenticatedSession(307L, 2L, 1, 9L));

        Map<String, Object> payload = controller(mock(LocalDbAuthService.class), sessionTokenService)
                .accountSession(request);

        assertThat(payload).containsEntry("success", true);
        assertThat(payload.get("session"))
                .isEqualTo(Map.of("userId", 307L, "roleId", 2L, "level", 1));
    }

    @Test
    void logoutIsStatelessAndConfirmsLocalTokenRemoval() {
        Map<String, Object> payload = controller(
                mock(LocalDbAuthService.class),
                mock(AuthSessionTokenService.class)
        ).accountLogout();

        assertThat(payload).containsExactly(Map.entry("success", true));
    }

    private MobileAuthController controller(
            LocalDbAuthService accountAuthService,
            AuthSessionTokenService sessionTokenService
    ) {
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalDbMobileAuthService> mobileProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalDbAuthService> accountProvider = mock(ObjectProvider.class);
        when(accountProvider.getIfAvailable()).thenReturn(accountAuthService);
        return new MobileAuthController(mobileProvider, accountProvider, sessionTokenService);
    }

    private AuthLoginResult loginResult() {
        AuthLoginResult result = new AuthLoginResult();
        result.setUserId(307L);
        result.setCredentialVersion(9L);
        result.setAccountNo("warehouse001");
        result.setRealName("仓库小王");
        result.setRoleId(2L);
        result.setRoleName("仓管");
        result.setLevel(1);
        return result;
    }
}
