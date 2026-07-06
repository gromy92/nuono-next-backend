package com.nuono.next.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
public class AuthEmailCodeSenderConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuthEmailCodeSender.class)
    @Conditional(SmtpHostMissingCondition.class)
    public AuthEmailCodeSender disabledAuthEmailCodeSender() {
        return new DisabledAuthEmailCodeSender();
    }

    static class SmtpHostMissingCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String host = context.getEnvironment().getProperty("nuono.auth.email-code.smtp.host");
            return !StringUtils.hasText(host);
        }
    }
}
