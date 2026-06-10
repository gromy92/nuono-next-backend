package com.nuono.next.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthApiProtectionFilterTest {

    private final AuthSessionTokenService sessionTokenService = new AuthSessionTokenService("test-secret", 600);
    private final AuthApiProtectionFilter filter = new AuthApiProtectionFilter(
            sessionTokenService,
            new AuthApiProtectionProperties()
    );

    @Test
    void shouldRejectProtectedApiWithoutSession() throws ServletException, IOException {
        MockHttpServletRequest request = request("GET", "/api/product-master/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(called));

        assertFalse(called.get());
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("请先登录"));
    }

    @Test
    void shouldAllowPublicAuthEndpointWithoutSession() throws ServletException, IOException {
        MockHttpServletRequest request = request("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(called));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowAli1688OpenApiCallbackWithoutSession() throws ServletException, IOException {
        MockHttpServletRequest request = request(
                "GET",
                "/api/procurement/ali1688-orders/authorizations/open-api/callback"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(called));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowPluginAuthEndpointWithoutSession() throws ServletException, IOException {
        MockHttpServletRequest request = request("POST", "/api/plugin/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(called));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowMobileApiToUseMobileTokenFlow() throws ServletException, IOException {
        MockHttpServletRequest request = request("GET", "/api/mobile/dashboard/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(called));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAcceptLocalDevSessionHeadersForProtectedApi() throws ServletException, IOException {
        MockHttpServletRequest request = request("POST", "/api/procurement/select-candidate");
        request.addHeader("Host", "localhost:18080");
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Nuono-Dev-Session-User-Id", "10001");
        request.addHeader("X-Nuono-Dev-Session-Role-Id", "1");
        request.addHeader("X-Nuono-Dev-Session-Level", "0");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);

        filter.doFilter(request, response, chain(called));

        assertTrue(called.get());
        assertEquals(200, response.getStatus());
        assertInstanceOf(
                AuthenticatedSession.class,
                request.getAttribute(AuthSessionTokenService.SESSION_REQUEST_ATTRIBUTE)
        );
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRequestURI(path);
        return request;
    }

    private FilterChain chain(AtomicBoolean called) {
        return (request, response) -> called.set(true);
    }
}
