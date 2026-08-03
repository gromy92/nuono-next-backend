package com.nuono.next.mobile;

import com.nuono.next.auth.AuthLoginCommand;
import com.nuono.next.auth.AuthLoginResult;
import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.auth.LocalDbAuthService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

    private final ObjectProvider<LocalDbMobileAuthService> mobileAuthServiceProvider;
    private final ObjectProvider<LocalDbAuthService> accountAuthServiceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public MobileAuthController(
            ObjectProvider<LocalDbMobileAuthService> mobileAuthServiceProvider,
            ObjectProvider<LocalDbAuthService> accountAuthServiceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.mobileAuthServiceProvider = mobileAuthServiceProvider;
        this.accountAuthServiceProvider = accountAuthServiceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/login")
    public Map<String, Object> accountLogin(@RequestBody AuthLoginCommand command) {
        LocalDbAuthService accountAuthService = accountAuthServiceProvider.getIfAvailable();
        if (accountAuthService == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "当前仍在无数据库骨架模式，不能执行账号登录。"
            );
        }
        try {
            AuthLoginResult result = accountAuthService.login(command);
            long ttlSeconds = sessionTokenService.getTtlSeconds();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("success", true);
            payload.put("tokenType", "Bearer");
            payload.put("accessToken", sessionTokenService.issue(result));
            payload.put("expiresInSeconds", ttlSeconds);
            payload.put("expiresAt", Instant.now().plusSeconds(ttlSeconds).toString());
            payload.put("session", result);
            return payload;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/me")
    public Map<String, Object> accountSession(HttpServletRequest request) {
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
    public Map<String, Object> accountLogout() {
        return Map.of("success", true);
    }

    @PostMapping("/wechatLogin")
    public MobileApiResponse<MobileAuthResponse> wechatLogin(@RequestBody MobileWechatLoginCommand command) {
        return execute(service -> service.wechatLogin(command));
    }

    @PostMapping("/sendCode")
    public MobileApiResponse<MobileSendCodeResponse> sendCode(@RequestBody MobileSendCodeCommand command) {
        return execute(service -> service.sendCode(command));
    }

    @PostMapping("/bindPhone")
    public MobileApiResponse<MobileAuthResponse> bindPhone(@RequestBody MobileBindPhoneCommand command) {
        return execute(service -> service.bindPhone(command));
    }

    @PostMapping("/smsLogin")
    public MobileApiResponse<MobileAuthResponse> smsLogin(@RequestBody MobileSmsLoginCommand command) {
        return execute(service -> service.smsLogin(command));
    }

    @PostMapping("/refreshToken")
    public MobileApiResponse<MobileAuthResponse> refreshToken(@RequestBody MobileRefreshTokenCommand command) {
        return execute(service -> service.refreshToken(command));
    }

    private <T> MobileApiResponse<T> execute(Function<LocalDbMobileAuthService, T> action) {
        LocalDbMobileAuthService mobileAuthService = mobileAuthServiceProvider.getIfAvailable();
        if (mobileAuthService == null) {
            return MobileApiResponse.failure(503, "当前仍在无数据库骨架模式，不能执行移动端登录。");
        }
        try {
            return MobileApiResponse.success(action.apply(mobileAuthService));
        } catch (MobileApiException exception) {
            return MobileApiResponse.failure(exception.getCode(), exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return MobileApiResponse.failure(400, exception.getMessage());
        }
    }
}
