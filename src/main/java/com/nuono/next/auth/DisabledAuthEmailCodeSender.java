package com.nuono.next.auth;

import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!T(org.springframework.util.StringUtils)"
        + ".hasText('${nuono.auth.email-code.smtp.host:}')")
public class DisabledAuthEmailCodeSender implements AuthEmailCodeSender {

    @Override
    public void sendLoginCode(String email, String code, LocalDateTime expiresAt) {
        throw new IllegalStateException("邮箱验证码发信未配置。");
    }
}
