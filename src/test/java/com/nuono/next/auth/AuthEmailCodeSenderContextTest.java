package com.nuono.next.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

class AuthEmailCodeSenderContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SenderConfiguration.class);

    @Test
    void shouldRegisterDisabledSenderWhenSmtpHostIsMissing() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(AuthEmailCodeSender.class);
            assertThat(context.getBean(AuthEmailCodeSender.class))
                    .isInstanceOf(DisabledAuthEmailCodeSender.class);
        });
    }

    @Test
    void shouldRegisterOnlySmtpSenderWhenHostIsConfigured() {
        contextRunner
                .withPropertyValues("nuono.auth.email-code.smtp.host=smtp.example.com")
                .run(context -> {
                    assertThat(context).hasSingleBean(AuthEmailCodeSender.class);
                    assertThat(context.getBean(AuthEmailCodeSender.class))
                            .isInstanceOf(SmtpAuthEmailCodeSender.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthEmailCodeProperties.class)
    @ComponentScan(
            basePackageClasses = AuthEmailCodeSender.class,
            useDefaultFilters = false,
            includeFilters = {
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = SmtpAuthEmailCodeSender.class
                    ),
                    @ComponentScan.Filter(
                            type = FilterType.ASSIGNABLE_TYPE,
                            classes = DisabledAuthEmailCodeSender.class
                    )
            }
    )
    static class SenderConfiguration {
    }
}
