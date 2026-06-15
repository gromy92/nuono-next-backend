package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class Ali1688HistoricalOrderProviderWiringContractTest {

    @Test
    void fakeProviderIsDefaultWhenOpenApiSwitchIsMissing() {
        new ApplicationContextRunner()
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
                        "nuono.procurement.ali1688.historical-order.open-api.enabled=true",
                        "nuono.procurement.ali1688.historical-order.open-api.app-key=5890829",
                        "nuono.procurement.ali1688.historical-order.open-api.app-secret=test-secret",
                        "nuono.procurement.ali1688.historical-order.open-api.token-cipher-secret=test-token-secret"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RestTemplateBuilder.class, RestTemplateBuilder::new)
                .withBean(Ali1688HistoricalOrderMapper.class, () -> mock(Ali1688HistoricalOrderMapper.class))
                .withUserConfiguration(ProviderConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(Ali1688HistoricalOrderProvider.class);
                    assertThat(context).hasSingleBean(HttpAli1688HistoricalOrderProvider.class);
                    assertThat(context).doesNotHaveBean(FakeAli1688HistoricalOrderProvider.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(Ali1688HistoricalOrderOpenApiProperties.class)
    @Import({
            FakeAli1688HistoricalOrderProvider.class,
            HttpAli1688HistoricalOrderProvider.class,
            Ali1688OpenApiSigner.class,
            Ali1688TokenCipher.class
    })
    static class ProviderConfig {
    }
}
