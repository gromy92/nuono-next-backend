package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void pluginLoginReturnsBearerTokenForBossAccount() {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo("boss001");
        command.setPassword("secret");
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(307L);
        login.setAccountNo("boss001");
        login.setRealName("星耀老板");
        login.setRoleId(1L);
        login.setRoleName("老板");
        login.setLevel(1);

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(authService.login(command)).thenReturn(login);
        when(sessionTokenService.issue(login)).thenReturn("signed-boss-plugin-token");
        when(sessionTokenService.getTtlSeconds()).thenReturn(600L);

        Map<String, Object> payload = controller.login(command);

        assertEquals(Boolean.TRUE, payload.get("success"));
        assertEquals("signed-boss-plugin-token", payload.get("accessToken"));
        assertEquals(login, payload.get("session"));
    }

    @Test
    void pluginLoginRejectsNonProcurementAndNonBossAccountBeforeIssuingToken() {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo("admin001");
        command.setPassword("secret");
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(1L);
        login.setAccountNo("admin001");
        login.setRealName("系统管理员");
        login.setRoleId(1L);
        login.setRoleName("系统管理员");
        login.setLevel(0);

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(authService.login(command)).thenReturn(login);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.login(command)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        assertEquals("插件只允许采购或老板账号登录。", exception.getReason());
        verify(sessionTokenService, never()).issue(login);
    }
}
