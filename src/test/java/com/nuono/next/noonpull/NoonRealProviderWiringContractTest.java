package com.nuono.next.noonpull;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class NoonRealProviderWiringContractTest {

    @Test
    void realProviderBeansShouldRemainAbsentWhenSwitchIsMissing() {
        new ApplicationContextRunner()
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(StoreSyncMapper.class, () -> mock(StoreSyncMapper.class))
                .withBean(NoonPullGatewaySessionFactory.class, () -> new NoopGatewaySessionFactory())
                .withUserConfiguration(RealProviderConfig.class)
                .run((context) -> {
                    assertThat(context).doesNotHaveBean(NoonProductInterfaceSmokeProvider.class);
                    assertThat(context).doesNotHaveBean(NoonSalesReportSmokeProvider.class);
                    assertThat(context).doesNotHaveBean(NoonSalesPageQueryProvider.class);
                    assertThat(context).doesNotHaveBean(NoonOrderReportSmokeProvider.class);
                });
    }

    @Test
    void realProviderBeansShouldBeCreatedOnlyWhenSwitchIsExplicitlyEnabled() {
        new ApplicationContextRunner()
                .withPropertyValues("nuono.noon.pull.real-provider.enabled=true")
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(StoreSyncMapper.class, () -> mock(StoreSyncMapper.class))
                .withBean(NoonPullGatewaySessionFactory.class, () -> new NoopGatewaySessionFactory())
                .withUserConfiguration(RealProviderConfig.class)
                .run((context) -> {
                    assertThat(context).hasSingleBean(NoonProductInterfaceSmokeProvider.class);
                    assertThat(context).hasSingleBean(NoonSalesReportSmokeProvider.class);
                    assertThat(context).doesNotHaveBean(NoonSalesPageQueryProvider.class);
                    assertThat(context).hasSingleBean(NoonOrderReportSmokeProvider.class);
                });
    }

    @Test
    void salesPageQueryRealProviderShouldRequireItsOwnExplicitSwitch() {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "nuono.noon.pull.real-provider.enabled=true",
                        "nuono.noon.pull.real-provider.sales-page-query.enabled=true"
                )
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(StoreSyncMapper.class, () -> mock(StoreSyncMapper.class))
                .withBean(NoonPullGatewaySessionFactory.class, () -> new NoopGatewaySessionFactory())
                .withUserConfiguration(RealProviderConfig.class)
                .run((context) -> assertThat(context).hasSingleBean(NoonSalesPageQueryProvider.class));
    }

    @Configuration
    @Import({
            NoonPullStoreBindingResolver.class,
            RealNoonProductInterfaceSmokeProvider.class,
            RealNoonSalesReportSmokeProvider.class,
            RealNoonSalesPageQueryProvider.class,
            RealNoonOrderReportSmokeProvider.class
    })
    static class RealProviderConfig {
    }

    private static class NoopGatewaySessionFactory implements NoonPullGatewaySessionFactory {
        @Override
        public NoonPullGatewaySession login(NoonPullStoreBinding binding) {
            return new NoonPullGatewaySession() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode postJson(
                        String url,
                        com.fasterxml.jackson.databind.JsonNode body,
                        boolean withProject,
                        Map<String, String> extraHeaders
                ) {
                    return new ObjectMapper().createObjectNode();
                }

                @Override
                public byte[] getBytes(String url, boolean withProject, Map<String, String> extraHeaders) {
                    return new byte[0];
                }
            };
        }
    }
}
