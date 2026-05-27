package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
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
    void shouldReturnBearerTokenForPluginLoginWithoutSettingCookie() {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo("operator001");
        command.setPassword("secret-password");
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(10001L);
        login.setAccountNo("operator001");
        login.setRealName("采购员");
        login.setRoleId(2L);
        login.setRoleName("采购");
        login.setLevel(3);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(authService.login(command)).thenReturn(login);
        when(sessionTokenService.issue(login)).thenReturn("signed-plugin-token");
        when(sessionTokenService.getTtlSeconds()).thenReturn(600L);

        Map<String, Object> payload = controller.login(command, response);

        assertEquals(Boolean.TRUE, payload.get("success"));
        assertEquals("Bearer", payload.get("tokenType"));
        assertEquals("signed-plugin-token", payload.get("accessToken"));
        assertEquals(600L, payload.get("expiresInSeconds"));
        assertSame(login, payload.get("session"));
        assertNull(response.getHeader(HttpHeaders.SET_COOKIE));
        assertFalse(payload.toString().contains("secret-password"));
    }

    @Test
    void shouldReturnStableFailureForInvalidPluginLogin() {
        AuthLoginCommand command = new AuthLoginCommand();
        command.setAccountNo("operator001");
        command.setPassword("wrong-password");

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(authService.login(command)).thenThrow(new IllegalArgumentException("账号或密码不正确。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.login(command, new MockHttpServletResponse())
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("账号或密码不正确。", error.getReason());
    }

    @Test
    void shouldReturnCurrentBearerSessionSummary() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-plugin-token");
        AuthenticatedSession session = new AuthenticatedSession(10001L, 2L, 3);

        when(sessionTokenService.requireSession(request)).thenReturn(session);

        Map<String, Object> payload = controller.me(request);

        assertEquals(Boolean.TRUE, payload.get("success"));
        Map<?, ?> sessionSummary = (Map<?, ?>) payload.get("session");
        assertEquals(10001L, sessionSummary.get("userId"));
        assertEquals(2L, sessionSummary.get("roleId"));
        assertEquals(3, sessionSummary.get("level"));
        verify(sessionTokenService).requireSession(request);
    }

    @Test
    void shouldRejectPluginSessionCheckWithoutBearerHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.me(request));

        assertEquals(HttpStatus.UNAUTHORIZED, error.getStatus());
        assertEquals("插件接口需要 Bearer 登录态。", error.getReason());
        verifyNoInteractions(sessionTokenService);
    }

    @Test
    void shouldAllowPluginLogoutWithoutServerRevocation() {
        Map<String, Object> payload = controller.logout();

        assertEquals(Boolean.TRUE, payload.get("success"));
        assertTrue(payload.get("message").toString().contains("客户端"));
    }
}
