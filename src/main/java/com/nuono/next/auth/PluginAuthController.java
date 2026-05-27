package com.nuono.next.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/plugin/auth")
public class PluginAuthController {

    private final ObjectProvider<LocalDbAuthService> localDbAuthServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public PluginAuthController(
            ObjectProvider<LocalDbAuthService> localDbAuthServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.localDbAuthServiceProvider = localDbAuthServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody AuthLoginCommand command,
            HttpServletResponse response
    ) {
        LocalDbAuthService authService = localDbAuthServiceProvider.getIfAvailable();
        if (authService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能执行插件登录。");
        }

        try {
            AuthLoginResult result = authService.login(command);
            String token = sessionTokenService.issue(result);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("tokenType", "Bearer");
            payload.put("accessToken", token);
            payload.put("expiresInSeconds", sessionTokenService.getTtlSeconds());
            payload.put("session", result);
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest request) {
        requireBearerSession(request);
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        Map<String, Object> sessionPayload = new LinkedHashMap<>();
        sessionPayload.put("userId", session.getUserId());
        sessionPayload.put("roleId", session.getRoleId());
        sessionPayload.put("level", session.getLevel());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("session", sessionPayload);
        return payload;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("message", "插件会话已退出，请客户端清理本地 token。");
        return payload;
    }

    private void requireBearerSession(HttpServletRequest request) {
        String authorization = request == null ? null : request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring("Bearer ".length()).trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "插件接口需要 Bearer 登录态。");
        }
    }
}
