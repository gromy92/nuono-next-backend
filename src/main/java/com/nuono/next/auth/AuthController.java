package com.nuono.next.auth;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final ObjectProvider<LocalDbAuthService> localDbAuthServiceProvider;
    private final ObjectProvider<AuthEmailCodeService> emailCodeServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public AuthController(
            ObjectProvider<LocalDbAuthService> localDbAuthServiceProvider,
            ObjectProvider<AuthEmailCodeService> emailCodeServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.localDbAuthServiceProvider = localDbAuthServiceProvider;
        this.emailCodeServiceProvider = emailCodeServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody AuthLoginCommand command,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账号密码登录已关闭，请使用邮箱验证码登录。");
    }

    @PostMapping("/email-code/request")
    public Map<String, Object> requestEmailCode(@RequestBody AuthEmailCodeRequestCommand command) {
        AuthEmailCodeService emailCodeService = emailCodeServiceProvider.getIfAvailable();
        if (emailCodeService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能发送邮箱验证码。");
        }

        try {
            emailCodeService.requestLoginCode(command);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", "验证码已发送");
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/email-code/login")
    public Map<String, Object> loginWithEmailCode(
            @RequestBody AuthEmailCodeLoginCommand command,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        AuthEmailCodeService emailCodeService = emailCodeServiceProvider.getIfAvailable();
        if (emailCodeService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能执行邮箱验证码登录。");
        }

        try {
            AuthLoginResult result = emailCodeService.login(command);
            return issueSession(result, request, response);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/change-password")
    public Map<String, Object> changePassword(
            @RequestBody AuthChangePasswordCommand command,
            HttpServletRequest request
    ) {
        LocalDbAuthService authService = localDbAuthServiceProvider.getIfAvailable();
        if (authService == null) {
            throw new IllegalStateException("当前仍在无数据库骨架模式，不能修改密码。");
        }

        try {
            AuthenticatedSession session = sessionTokenService.requireSession(request);
            if (command == null) {
                command = new AuthChangePasswordCommand();
            }
            command.setUserId(session.getUserId());
            String message = authService.changePassword(command);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("message", message);
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, clearSessionCookie().toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        return payload;
    }

    @GetMapping("/sample-accounts")
    public void sampleAccounts() {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "样本账号接口已关闭。");
    }

    private ResponseCookie createSessionCookie(String token, HttpServletRequest request) {
        return ResponseCookie.from(AuthSessionTokenService.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(request != null && request.isSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(sessionTokenService.getTtlSeconds())
                .build();
    }

    private ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(AuthSessionTokenService.COOKIE_NAME, "")
                .httpOnly(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    private Map<String, Object> issueSession(
            AuthLoginResult result,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String token = sessionTokenService.issue(result);
        response.addHeader(HttpHeaders.SET_COOKIE, createSessionCookie(token, request).toString());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", true);
        payload.put("session", result);
        return payload;
    }
}
