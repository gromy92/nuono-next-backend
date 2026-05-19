package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class AuthSessionTokenServiceTest {

    @Test
    void shouldIssueAndVerifySignedCookieToken() {
        AuthSessionTokenService service = new AuthSessionTokenService("test-secret", 600);
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(10001L);
        login.setRoleId(1L);
        login.setLevel(0);

        String token = service.issue(login);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthSessionTokenService.COOKIE_NAME, token));

        AuthenticatedSession session = service.requireSession(request);

        assertEquals(10001L, session.getUserId());
        assertEquals(1L, session.getRoleId());
        assertEquals(0, session.getLevel());
    }

    @Test
    void shouldRejectTamperedToken() {
        AuthSessionTokenService service = new AuthSessionTokenService("test-secret", 600);
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(10001L);

        String token = service.issue(login);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthSessionTokenService.COOKIE_NAME, token + "tampered"));

        assertThrows(ResponseStatusException.class, () -> service.requireSession(request));
    }
}
