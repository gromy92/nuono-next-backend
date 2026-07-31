package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductSiteOfferFailureEvidenceTest {

    @Test
    void pricingFailuresRemainVisibleToTheBatchRiskGuard() {
        ObjectMapper objectMapper = new ObjectMapper();
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        IllegalStateException rateLimited = new IllegalStateException("HTTP 429 too many requests");
        when(adapter.postJson(any(), anyString(), any(), anyBoolean())).thenThrow(rateLimited);
        when(adapter.userMessage(rateLimited)).thenReturn("HTTP 429 too many requests");
        ProductProjectSiteResolver resolver =
                new ProductProjectSiteResolver(objectMapper, storeSyncMapper, adapter);
        ProductSiteOfferFetcher fetcher =
                new ProductSiteOfferFetcher(objectMapper, adapter, resolver);
        List<String> warnings = new ArrayList<>();

        fetcher.loadSiteOffers(
                noonSession(objectMapper, storeSyncMapper),
                List.of(new ProductProjectSiteContext("STR108065-NSA", "SA", "ACTIVE")),
                "STR108065-NSA",
                "PARTNER-ID",
                "PARTNER-SKU",
                "PSKU-CODE",
                warnings
        );

        assertThat(warnings)
                .hasSize(2)
                .allMatch(warning -> warning.contains("HTTP 429 too many requests"));
    }

    private NoonSession noonSession(ObjectMapper objectMapper, StoreSyncMapper storeSyncMapper) {
        try {
            NoonSessionGateway gateway = new NoonSessionGateway(
                    objectMapper,
                    storeSyncMapper,
                    false,
                    0L,
                    true,
                    "",
                    "",
                    "",
                    "",
                    false,
                    false,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    false,
                    "",
                    "",
                    0,
                    ""
            );
            Constructor<?> constructor = legacyNoonSessionConstructor();
            constructor.setAccessible(true);
            return (NoonSession) constructor.newInstance(
                    gateway,
                    307L,
                    "tester",
                    "password",
                    null,
                    "PRJ108065",
                    "STR108065-NSA"
            );
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法创建测试 NoonSession", exception);
        }
    }

    private Constructor<?> legacyNoonSessionConstructor() {
        for (Constructor<?> constructor : NoonSession.class.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 7) {
                return constructor;
            }
        }
        throw new IllegalStateException("未找到测试 NoonSession 构造器");
    }
}
