package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private ObjectProvider<LocalDbAuthService> authServiceProvider;

    @Mock
    private LocalDbAuthService authService;

    @Mock
    private AuthSessionTokenService sessionTokenService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authServiceProvider, sessionTokenService);
    }

    @Test
    void shouldSetSessionCookieAfterPasswordLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthLoginCommand command = new AuthLoginCommand();
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(10001L);

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(authService.login(command)).thenReturn(login);
        when(sessionTokenService.issue(login)).thenReturn("signed-token");
        when(sessionTokenService.getTtlSeconds()).thenReturn(600L);

        Map<String, Object> payload = controller.login(command, request, response);

        assertEquals(Boolean.TRUE, payload.get("success"));
        assertEquals(login, payload.get("session"));
        assertTrue(response.getHeader(HttpHeaders.SET_COOKIE).contains(AuthSessionTokenService.COOKIE_NAME + "=signed-token"));
    }

    @Test
    void shouldRequestEmailCode() {
        AuthEmailCodeRequestCommand command = new AuthEmailCodeRequestCommand();
        command.setEmail("login@example.com");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.requestEmailCode(command)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("邮箱验证码登录不用于诺诺管家入口，请使用账号密码登录。", error.getReason());
    }

    @Test
    void shouldCloseEmailCodeLoginEndpointForManagerLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthEmailCodeLoginCommand command = new AuthEmailCodeLoginCommand();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.loginWithEmailCode(command, request, response)
        );

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
        assertEquals("邮箱验证码登录不用于诺诺管家入口，请使用账号密码登录。", error.getReason());
    }

    @Test
    void shouldChangeOnlyAuthenticatedUserPassword() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AuthChangePasswordCommand command = new AuthChangePasswordCommand();
        command.setUserId(99999L);
        command.setNewPassword("Next123!");

        when(authServiceProvider.getIfAvailable()).thenReturn(authService);
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(10001L, 1L, 0));
        when(authService.changePassword(command)).thenReturn("密码修改成功");

        assertEquals("密码修改成功", controller.changePassword(command, request).get("message"));

        ArgumentCaptor<AuthChangePasswordCommand> captor = ArgumentCaptor.forClass(AuthChangePasswordCommand.class);
        verify(authService).changePassword(captor.capture());
        assertEquals(10001L, captor.getValue().getUserId());
    }

    @Test
    void shouldCloseSampleAccountsEndpoint() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.sampleAccounts());

        assertEquals(HttpStatus.NOT_FOUND, error.getStatus());
    }
}
