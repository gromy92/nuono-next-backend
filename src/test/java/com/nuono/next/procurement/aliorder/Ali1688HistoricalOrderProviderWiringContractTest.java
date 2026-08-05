package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.infrastructure.mapper.Ali1688OpenApiAuthorizationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class Ali1688HistoricalOrderProviderWiringContractTest {

    @Test
    void fakeProviderIsDefaultOnlyInTestProfileWhenOpenApiSwitchIsMissing() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=test")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Ali1688HistoricalOrderProvider.class);
                    assertThat(context).hasSingleBean(FakeAli1688HistoricalOrderProvider.class);
                    assertThat(context).doesNotHaveBean(HttpAli1688HistoricalOrderProvider.class);
                });
    }

    @Test
    void httpProviderReplacesFakeProviderOnlyWhenOpenApiSwitchIsEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        "nuono.data-pull.execution-mode=RUNTIME",
                        "nuono.procurement.ali1688.historical-order.open-api.enabled=true",
                        "nuono.procurement.ali1688.historical-order.open-api.app-key=5890829",
                        "nuono.procurement.ali1688.historical-order.open-api.app-secret=test-secret",
                        "nuono.procurement.ali1688.historical-order.open-api.token-cipher-secret=test-token-secret",
                        "nuono.procurement.ali1688.historical-order.open-api.redirect-uri="
                                + "https://www.nuoon.com/ai/api/procurement/ali1688-orders/"
                                + "authorizations/open-api/callback",
                        "nuono.procurement.ali1688.historical-order.open-api.modified-from-parameter-name=modifyStartTime",
                        "nuono.procurement.ali1688.historical-order.open-api.modified-at-response-field-names=modifyTime"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withBean(Ali1688HistoricalOrderMapper.class, () -> mock(Ali1688HistoricalOrderMapper.class))
                .withBean(
                        Ali1688OpenApiAuthorizationMapper.class,
                        () -> mock(Ali1688OpenApiAuthorizationMapper.class)
                )
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Ali1688HistoricalOrderProvider.class);
                    assertThat(context).hasSingleBean(HttpAli1688HistoricalOrderProvider.class);
                    assertThat(context).doesNotHaveBean(
                            "ali1688Dp10OpenApiReleaseEvidence");
                    assertThat(context).doesNotHaveBean(FakeAli1688HistoricalOrderProvider.class);
                });
    }

    @Test
    void localDbWithoutRealConfigurationUsesFailClosedProviderAndNeverFakeFixtures() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        "nuono.data-pull.execution-mode=LEGACY"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Ali1688HistoricalOrderProvider.class);
                    assertThat(context).hasSingleBean(UnavailableAli1688HistoricalOrderProvider.class);
                    assertThat(context).doesNotHaveBean(FakeAli1688HistoricalOrderProvider.class);
                    assertThat(context).doesNotHaveBean(HttpAli1688HistoricalOrderProvider.class);
                });
    }

    @Test
    void legacySchedulerPropertyDoesNotImplicitlyEnableOpenApiProvider() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        "nuono.data-pull.execution-mode=LEGACY",
                        "nuono.procurement.ali1688.historical-order.scheduler.enabled=true"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(UnavailableAli1688HistoricalOrderProvider.class);
                    assertThat(context).doesNotHaveBean(HttpAli1688HistoricalOrderProvider.class);
                });
    }

    @Test
    void localDbDp10RequiredWithoutRealIncrementalConfigurationFailsStartup() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=local-db",
                        "nuono.data-pull.execution-mode=RUNTIME",
                        "nuono.procurement.ali1688.historical-order.open-api.required-for-dp10=true"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> assertThat(context.getStartupFailure())
                        .isNotNull()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("DP-10 requires a real 1688 OpenAPI adapter"));
    }

    @Configuration
    @EnableConfigurationProperties(Ali1688HistoricalOrderOpenApiProperties.class)
    @Import({
            FakeAli1688HistoricalOrderProvider.class,
            UnavailableAli1688HistoricalOrderProvider.class,
            HttpAli1688HistoricalOrderProvider.class,
            Ali1688HistoricalOrderProviderConfigurationGuard.class,
            Ali1688OpenApiSigner.class,
            Ali1688TokenCipher.class
    })
    static class ProviderConfig {
    }
}
