package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonSessionGateway;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSnapshotCoreFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductSnapshotCoreFetcher fetcher;
    private NoonSession session;

    @BeforeEach
    void setUp() {
        fetcher = new ProductSnapshotCoreFetcher(objectMapper, productNoonAdapter);
        session = noonSession("STR245027-NAE");
    }

    @Test
    void shouldFetchWhoamiProductSnapshotAndVariantInfo() throws Exception {
        JsonNode whoami = objectMapper.readTree("{\"email\":\"buyer@example.com\"}");
        JsonNode retrieveRoot = objectMapper.readTree("{\"PARENT-1\":{\"attributes\":{\"common\":{\"brand\":\"Acme\"}}}}");
        JsonNode variantInfo = objectMapper.readTree("{\"CHILD-1\":{\"partner_sku\":\"PARTNER-1\"}}");
        when(productNoonAdapter.getJson(
                any(NoonSession.class),
                eq(NoonProductGateway.WHOAMI_URL),
                eq(false)
        )).thenReturn(whoami);
        when(productNoonAdapter.postJson(any(NoonSession.class), any(String.class), any(JsonNode.class), eq(true)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(1);
                    if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                        return retrieveRoot;
                    }
                    if (NoonProductGateway.VARIANT_INFO_URL.equals(url)) {
                        return variantInfo;
                    }
                    throw new IllegalArgumentException("unexpected url " + url);
                });
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();

        ProductSnapshotCoreFetchResult result = fetcher.fetch(
                session,
                "PARENT-1",
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        );

        assertEquals(whoami, result.getWhoamiNode());
        assertEquals("Acme", result.getProductNode().path("attributes").path("common").path("brand").asText());
        assertEquals(variantInfo, result.getVariantInfoNode());
        assertEquals(List.of("whoami", "zsku.retrieve", "variant.info"), stageNames);
        assertEquals(List.of(), warnings);
        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.ZSKU_RETRIEVE_URL),
                argThat(body -> "PARENT-1".equals(body.path("skuParents").path(0).asText())
                        && body.path("attributeCodes").isArray()),
                eq(true)
        );
        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.VARIANT_INFO_URL),
                argThat(body -> "PARENT-1".equals(body.path("zskuParent").asText())),
                eq(true)
        );
    }

    @Test
    void shouldFailWhenZskuRetrieveDoesNotContainRequestedProduct() throws Exception {
        when(productNoonAdapter.getJson(
                any(NoonSession.class),
                eq(NoonProductGateway.WHOAMI_URL),
                eq(false)
        )).thenReturn(objectMapper.readTree("{}"));
        when(productNoonAdapter.postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.ZSKU_RETRIEVE_URL),
                any(JsonNode.class),
                eq(true)
        )).thenReturn(objectMapper.readTree("{}"));
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> fetcher.fetch(
                session,
                "PARENT-MISSING",
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        ));

        assertEquals("Noon 没有返回 skuParent=PARENT-MISSING 的商品快照。", exception.getMessage());
        assertEquals(List.of("whoami", "zsku.retrieve"), stageNames);
        verify(productNoonAdapter, never()).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.VARIANT_INFO_URL),
                any(JsonNode.class),
                eq(true)
        );
    }

    @Test
    void shouldWarnAndReturnMissingVariantInfoWhenVariantReadFails() throws Exception {
        when(productNoonAdapter.getJson(
                any(NoonSession.class),
                eq(NoonProductGateway.WHOAMI_URL),
                eq(false)
        )).thenReturn(objectMapper.readTree("{}"));
        when(productNoonAdapter.postJson(any(NoonSession.class), any(String.class), any(JsonNode.class), eq(true)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(1);
                    if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                        return objectMapper.readTree("{\"PARENT-1\":{\"attributes\":{\"common\":{}}}}");
                    }
                    throw new IllegalStateException("Noon unavailable");
                });
        when(productNoonAdapter.userMessage(any(RuntimeException.class))).thenReturn("请求 Noon 失败");
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();

        ProductSnapshotCoreFetchResult result = fetcher.fetch(
                session,
                "PARENT-1",
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        );

        assertEquals(true, result.getVariantInfoNode().isMissingNode());
        assertEquals(List.of("读取尺码与变体信息失败：请求 Noon 失败"), warnings);
        assertEquals(List.of("whoami", "zsku.retrieve", "variant.info"), stageNames);
    }

    private NoonSession noonSession(String storeCode) {
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
                    false,
                    "",
                    "",
                    0,
                    ""
            );
            Constructor<?> constructor = NoonSession.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (NoonSession) constructor.newInstance(gateway, 10001L, "tester", "password", null, "PRJ108065", storeCode);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法创建测试 NoonSession", exception);
        }
    }
}
