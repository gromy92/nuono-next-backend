package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
class ProductSnapshotGroupFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductSnapshotGroupFetcher fetcher;
    private NoonSession session;

    @BeforeEach
    void setUp() {
        fetcher = new ProductSnapshotGroupFetcher(
                objectMapper,
                productNoonAdapter,
                new ProductSnapshotSectionBuilder(objectMapper)
        );
        session = noonSession("STR245027-NAE");
    }

    @Test
    void shouldFetchGroupCurrentDetailParentAttributesAndCandidateGroups() throws Exception {
        JsonNode groupCurrent = objectMapper.readTree("{\"sku_group\":\"GRP-1\"}");
        JsonNode groupDetail = objectMapper.readTree("{\"GRP-1\":{"
                + "\"axes\":[{\"axis_code\":\"colour_name\"}],"
                + "\"zsku_parents\":[{\"sku_parent\":\"PARENT-1\"}]"
                + "}}");
        JsonNode groupParentAttributes = objectMapper.readTree("{\"PARENT-1\":{\"attributes\":{\"common\":{\"colour_name\":\"Red\"}}}}");
        JsonNode groupList = objectMapper.readTree("[{\"zsku_group\":\"GRP-1\"}]");
        when(productNoonAdapter.getJson(
                any(NoonSession.class),
                eq(NoonProductGateway.GROUP_CURRENT_URL_PREFIX + "PARENT-1"),
                eq(true)
        )).thenReturn(groupCurrent);
        when(productNoonAdapter.postJson(any(NoonSession.class), any(String.class), any(JsonNode.class), eq(true)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(1);
                    if (NoonProductGateway.GROUP_DETAIL_URL.equals(url)) {
                        return groupDetail;
                    }
                    if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                        return groupParentAttributes;
                    }
                    if (NoonProductGateway.GROUP_LIST_URL.equals(url)) {
                        return groupList;
                    }
                    throw new IllegalArgumentException("unexpected url " + url);
                });
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();

        ProductSnapshotGroupFetchResult result = fetcher.fetch(
                session,
                "PARENT-1",
                "home_decor-tray",
                "Acme",
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        );

        assertEquals("GRP-1", result.getSkuGroup());
        assertEquals(groupCurrent, result.getGroupCurrentNode());
        assertEquals(groupDetail, result.getGroupDetailNode());
        assertEquals(groupParentAttributes, result.getGroupParentAttributesNode());
        assertEquals(groupList, result.getGroupListNode());
        assertEquals(List.of("group.current", "group.detail", "group.parentAttributes", "group.list"), stageNames);
        assertEquals(List.of(), warnings);
        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.GROUP_DETAIL_URL),
                argThat(body -> "GRP-1".equals(body.path("zskuGroup").path(0).asText())),
                eq(true)
        );
        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.ZSKU_RETRIEVE_URL),
                argThat(body -> "PARENT-1".equals(body.path("skuParents").path(0).asText())
                        && "colour_name".equals(body.path("attributeCodes").path(0).asText())),
                eq(true)
        );
        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.GROUP_LIST_URL),
                argThat(body -> "home_decor-tray".equals(body.path("fulltype").asText())
                        && "Acme".equals(body.path("brand").asText())),
                eq(true)
        );
    }

    @Test
    void shouldWarnAndSkipDependentGroupCallsWhenCurrentGroupFails() {
        when(productNoonAdapter.getJson(
                any(NoonSession.class),
                eq(NoonProductGateway.GROUP_CURRENT_URL_PREFIX + "PARENT-1"),
                eq(true)
        )).thenThrow(new IllegalStateException("Noon unavailable"));
        when(productNoonAdapter.userMessage(any(RuntimeException.class))).thenReturn("请求 Noon 失败");
        List<String> warnings = new ArrayList<>();
        List<String> stageNames = new ArrayList<>();

        ProductSnapshotGroupFetchResult result = fetcher.fetch(
                session,
                "PARENT-1",
                null,
                null,
                warnings,
                (stageName, startedAt) -> stageNames.add(stageName)
        );

        assertFalse(result.getGroupCurrentNode().isObject());
        assertEquals(null, result.getSkuGroup());
        assertEquals(List.of("读取当前 group 失败：请求 Noon 失败"), warnings);
        assertEquals(List.of("group.current"), stageNames);
        verify(productNoonAdapter, never()).postJson(
                any(NoonSession.class),
                any(String.class),
                any(JsonNode.class),
                eq(true)
        );
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
            return (NoonSession) constructor.newInstance(gateway, "tester", "password", null, "PRJ108065", storeCode);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法创建测试 NoonSession", exception);
        }
    }
}
