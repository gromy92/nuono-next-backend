package com.nuono.next.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class AuthEmailCodeSenderConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SenderConfiguration.class);

    @Test
    void registersDisabledFallbackWhenSmtpHostIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthEmailCodeSender.class);
            assertThat(context.getBean(AuthEmailCodeSender.class))
                    .isInstanceOf(DisabledAuthEmailCodeSender.class);
        });
    }

    @Test
    void usesSmtpSenderWhenSmtpHostIsConfigured() {
        contextRunner
                .withPropertyValues("nuono.auth.email-code.smtp.host=smtp.example.test")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthEmailCodeSender.class);
                    assertThat(context.getBean(AuthEmailCodeSender.class))
                            .isInstanceOf(SmtpAuthEmailCodeSender.class);
                    assertThat(context).doesNotHaveBean(DisabledAuthEmailCodeSender.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthEmailCodeProperties.class)
    @Import({
            AuthEmailCodeSenderConfiguration.class,
            SmtpAuthEmailCodeSender.class
    })
    static class SenderConfiguration {
    }
}
