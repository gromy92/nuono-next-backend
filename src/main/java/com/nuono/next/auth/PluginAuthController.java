package com.nuono.next.auth;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
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
    public Map<String, Object> login(@RequestBody AuthLoginCommand command) {
        LocalDbAuthService authService = localDbAuthServiceProvider.getIfAvailable();
        if (authService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能执行插件登录。");
        }

        try {
            AuthLoginResult result = authService.login(command);
            requirePluginTaskAccount(result);
            String token = sessionTokenService.issue(result);
            long ttlSeconds = sessionTokenService.getTtlSeconds();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("tokenType", "Bearer");
            payload.put("accessToken", token);
            payload.put("expiresInSeconds", ttlSeconds);
            payload.put("expiresAt", Instant.now().plusSeconds(ttlSeconds).toString());
            payload.put("session", result);
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return payload;
    }

    private void requirePluginTaskAccount(AuthLoginResult result) {
        String roleName = result == null ? null : result.getRoleName();
        if (!StringUtils.hasText(roleName) || (!roleName.contains("采购") && !roleName.contains("老板"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "插件只允许采购或老板账号登录。");
        }
    }
}
