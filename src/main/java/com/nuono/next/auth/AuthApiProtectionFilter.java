package com.nuono.next.auth;

import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthApiProtectionFilter extends OncePerRequestFilter {

    private final AuthSessionTokenService sessionTokenService;
    private final AuthApiProtectionProperties properties;

    public AuthApiProtectionFilter(
            AuthSessionTokenService sessionTokenService,
            AuthApiProtectionProperties properties
    ) {
        this.sessionTokenService = sessionTokenService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!requiresApiSession(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            sessionTokenService.requireSession(request);
            filterChain.doFilter(request, response);
        } catch (ResponseStatusException exception) {
            writeFailure(response, exception);
        }
    }

    private boolean requiresApiSession(HttpServletRequest request) {
        if (!properties.isEnabled() || request == null || "OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = applicationPath(request);
        if (!path.startsWith("/api/")) {
            return false;
        }
        if (properties.getPublicPaths().contains(path)) {
            return false;
        }
        for (String publicPrefix : properties.getPublicPrefixes()) {
            if (StringUtils.hasText(publicPrefix) && path.startsWith(publicPrefix)) {
                return false;
            }
        }
        return true;
    }

    private String applicationPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return StringUtils.hasText(path) ? path : "/";
    }

    private void writeFailure(HttpServletResponse response, ResponseStatusException exception) throws IOException {
        HttpStatus status = exception.getStatus();
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escapeJson(exception.getReason()) + "\"}");
    }

    private String escapeJson(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
