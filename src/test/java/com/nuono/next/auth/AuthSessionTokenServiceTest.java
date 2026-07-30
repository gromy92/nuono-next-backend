package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.AuthMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class AuthSessionTokenServiceTest {

    @Test
    void shouldIssueAndVerifySignedCookieToken() {
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> sessionState(0L, 1L, 0)
        );
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
        assertEquals(0L, session.getCredentialVersion());
    }

    @Test
    void shouldRejectTamperedToken() {
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> sessionState(0L, 1L, 0)
        );
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(10001L);

        String token = service.issue(login);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthSessionTokenService.COOKIE_NAME, token + "tampered"));

        assertThrows(ResponseStatusException.class, () -> service.requireSession(request));
    }

    @Test
    void shouldRejectCookieAndBearerTokensAfterCredentialVersionChanges() {
        AtomicLong credentialVersion = new AtomicLong(0L);
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> sessionState(credentialVersion.get(), 1L, 0)
        );
        AuthLoginResult login = login(10001L);
        String oldToken = service.issue(login);

        assertEquals(10001L, service.requireSession(cookieRequest(oldToken)).getUserId());

        credentialVersion.incrementAndGet();

        assertThrows(
                ResponseStatusException.class,
                () -> service.requireSession(cookieRequest(oldToken))
        );
        assertThrows(
                ResponseStatusException.class,
                () -> service.requireSession(bearerRequest(oldToken))
        );

        login.setCredentialVersion(credentialVersion.get());
        String newToken = service.issue(login);
        assertEquals(10001L, service.requireSession(bearerRequest(newToken)).getUserId());
    }

    @Test
    void shouldTreatLegacyFiveFieldTokenAsInitialCredentialVersion() throws Exception {
        AtomicLong credentialVersion = new AtomicLong(0L);
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> sessionState(credentialVersion.get(), 1L, 0)
        );
        String legacyToken = legacyToken("test-secret", 10001L, 1L, 0);

        assertEquals(10001L, service.requireSession(cookieRequest(legacyToken)).getUserId());

        credentialVersion.incrementAndGet();

        assertThrows(
                ResponseStatusException.class,
                () -> service.requireSession(cookieRequest(legacyToken))
        );
    }

    @Test
    void shouldReadCurrentSessionStateFromAuthMapper() {
        @SuppressWarnings("unchecked")
        ObjectProvider<AuthMapper> provider = mock(ObjectProvider.class);
        AuthMapper authMapper = mock(AuthMapper.class);
        when(provider.getIfAvailable()).thenReturn(authMapper);
        when(authMapper.selectSessionState(10001L)).thenReturn(sessionState(0L, 1L, 0));
        AuthSessionTokenService service = new AuthSessionTokenService("test-secret", 600, provider);
        String token = service.issue(login(10001L));

        assertEquals(10001L, service.requireSession(cookieRequest(token)).getUserId());
        verify(authMapper).selectSessionState(10001L);
    }

    @Test
    void shouldFailClosedWhenCredentialVersionStoreIsUnavailable() {
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> null
        );
        String token = service.issue(login(10001L));

        assertThrows(
                ResponseStatusException.class,
                () -> service.requireSession(cookieRequest(token))
        );
    }

    @Test
    void shouldUseCurrentRoleInsteadOfRoleStoredInToken() {
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> sessionState(0L, 4L, 3)
        );
        AuthLoginResult login = login(10001L);
        String token = service.issue(login);

        AuthenticatedSession session = service.requireSession(cookieRequest(token));

        assertEquals(4L, session.getRoleId());
        assertEquals(3, session.getLevel());
    }

    @Test
    void shouldKeepLoginSnapshotVersionWhenIssueRacesWithPasswordReset() {
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> sessionState(1L, 1L, 0)
        );
        AuthLoginResult staleLoginSnapshot = login(10001L);
        staleLoginSnapshot.setCredentialVersion(0L);

        String token = service.issue(staleLoginSnapshot);

        assertThrows(
                ResponseStatusException.class,
                () -> service.requireSession(cookieRequest(token))
        );
    }

    @Test
    void shouldReturnServiceUnavailableWithoutAuthorizingWhenSessionStoreThrows() {
        AuthSessionTokenService service = new AuthSessionTokenService(
                "test-secret",
                600,
                ignored -> {
                    throw new IllegalStateException("database unavailable");
                }
        );
        String token = service.issue(login(10001L));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requireSession(cookieRequest(token))
        );

        assertEquals(503, error.getRawStatusCode());
    }

    private static AuthLoginResult login(Long userId) {
        AuthLoginResult login = new AuthLoginResult();
        login.setUserId(userId);
        login.setRoleId(1L);
        login.setLevel(0);
        return login;
    }

    private static AuthSessionState sessionState(
            Long credentialVersion,
            Long roleId,
            Integer level
    ) {
        AuthSessionState state = new AuthSessionState();
        state.setCredentialVersion(credentialVersion);
        state.setRoleId(roleId);
        state.setLevel(level);
        return state;
    }

    private static MockHttpServletRequest cookieRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(AuthSessionTokenService.COOKIE_NAME, token));
        return request;
    }

    private static MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String legacyToken(
            String secret,
            Long userId,
            Long roleId,
            Integer level
    ) throws Exception {
        String payload = userId
                + ":" + roleId
                + ":" + level
                + ":" + Instant.now().plusSeconds(600).getEpochSecond()
                + ":legacy-session";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + encoder.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
