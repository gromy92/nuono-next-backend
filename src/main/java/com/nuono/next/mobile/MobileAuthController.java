package com.nuono.next.mobile;

import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/auth")
public class MobileAuthController {

    private final ObjectProvider<LocalDbMobileAuthService> mobileAuthServiceProvider;

    public MobileAuthController(ObjectProvider<LocalDbMobileAuthService> mobileAuthServiceProvider) {
        this.mobileAuthServiceProvider = mobileAuthServiceProvider;
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
