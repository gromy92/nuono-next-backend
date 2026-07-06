package com.nuono.next.auth;

import java.time.LocalDateTime;

public class DisabledAuthEmailCodeSender implements AuthEmailCodeSender {

    @Override
    public void sendLoginCode(String email, String code, LocalDateTime expiresAt) {
        throw new IllegalStateException("邮箱验证码发信未配置。");
    }
}
