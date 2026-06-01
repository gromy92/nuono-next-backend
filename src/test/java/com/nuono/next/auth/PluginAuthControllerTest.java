package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class PluginAuthControllerTest {

    @Mock
    private ObjectProvider<LocalDbAuthService> authServiceProvider;

    @Mock
    private LocalDbAuthService authService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private PluginAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new PluginAuthController(authServiceProvider, sessionTokenService);
    }

    @Test
    void pluginLoginReturnsBearerTokenWithoutSettingPasswordOrCookieContract() {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo("operator001");
        command.setPassword("secret");
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(307L);
        login.setAccountNo("operator001");
        login.setRealName("星耀运营");
        login.setRoleId(2L);
        login.setRoleName("采购");
        login.setLevel(3);

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(authService.login(command)).thenReturn(login);
        when(sessionTokenService.issue(login)).thenReturn("signed-plugin-token");
        when(sessionTokenService.getTtlSeconds()).thenReturn(600L);

        Map<String, Object> payload = controller.login(command);

        assertEquals(Boolean.TRUE, payload.get("success"));
        assertEquals("Bearer", payload.get("tokenType"));
        assertEquals("signed-plugin-token", payload.get("accessToken"));
        assertEquals(600L, payload.get("expiresInSeconds"));
        assertEquals(login, payload.get("session"));
        assertFalse(payload.containsKey("password"));
    }
}
