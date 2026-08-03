package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductDeletePreflightFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductDeletePreflightFetcher fetcher;
    private NoonSession session;

    @BeforeEach
    void setUp() {
        ProductSnapshotSectionBuilder sectionBuilder = new ProductSnapshotSectionBuilder(objectMapper);
        fetcher = new ProductDeletePreflightFetcher(
                new ProductSnapshotCoreFetcher(objectMapper, productNoonAdapter),
                sectionBuilder
        );
        session = noonSession("STR108065-NSA");
    }

    @Test
    void shouldCaptureOnlyDeleteIdentityMappingAndPresenceWithoutPricingOrStock() throws Exception {
        when(productNoonAdapter.getJson(
                any(NoonSession.class),
                eq(NoonProductGateway.WHOAMI_URL),
                eq(false)
        )).thenReturn(objectMapper.readTree("{\"email\":\"operator@example.test\"}"));
        when(productNoonAdapter.postJson(
                any(NoonSession.class),
                any(String.class),
                any(JsonNode.class),
                anyBoolean()
        )).thenAnswer(invocation -> {
            String url = invocation.getArgument(1);
            if (NoonProductGateway.ZSKU_RETRIEVE_URL.equals(url)) {
                return objectMapper.readTree(
                        "{\"ZPARENT\":{\"attributes\":{\"common\":{\"id_partner\":\"108065\"}},"
                                + "\"variants\":[{\"sku\":\"N12345678A\",\"partner_sku\":\"PAPERSAYSB214\"}]}}"
                );
            }
            if (NoonProductGateway.VARIANT_INFO_URL.equals(url)) {
                return objectMapper.readTree(
                        "{\"N12345678A\":{\"partner_sku\":\"PAPERSAYSB214\","
                                + "\"psku_code\":\"PSKU-214\"}}"
                );
            }
            if (NoonProductGateway.PRICING_INFO_URL.equals(url)) {
                throw new IllegalStateException("HTTP 307 Location: https://login.noon.partners/");
            }
            throw new IllegalArgumentException("unexpected url " + url);
        });
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STR108065-NSA");
        command.setSkuParent("ZPARENT");
        command.setPartnerSku("PAPERSAYSB214");
        command.setPskuCode("PSKU-214");

        ProductMasterSnapshotView snapshot = fetcher.fetch(session, command);

        assertTrue(snapshot.isReady());
        assertEquals("product-delete-preflight", snapshot.getMode());
        assertEquals("PSKU-214", snapshot.getIdentity().get("pskuCode"));
        assertEquals("N12345678A", snapshot.getIdentity().get("childSku"));
        assertEquals(1, snapshot.getVariants().size());
        assertTrue(snapshot.getContent().isEmpty());
        assertTrue(snapshot.getPricing().isEmpty());
        assertTrue(snapshot.getStock().isEmpty());
        assertTrue(snapshot.getSiteOffers().isEmpty());
        verify(productNoonAdapter, never()).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.PRICING_INFO_URL),
                any(JsonNode.class),
                anyBoolean()
        );
        verify(productNoonAdapter, never()).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.STOCK_INFO_URL),
                any(JsonNode.class),
                anyBoolean()
        );
    }

    private NoonSession noonSession(String storeCode) {
        try {
            NoonSessionGateway gateway = new NoonSessionGateway(
                    objectMapper,
                    storeSyncMapper,
                    0L,
                    true,
                    "",
                    "",
                    "",
                    "",
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
            Constructor<?> constructor = legacyNoonSessionConstructor();
            constructor.setAccessible(true);
            return (NoonSession) constructor.newInstance(
                    gateway,
                    307L,
                    "operator@example.test",
                    "password",
                    null,
                    "PRJ108065",
                    storeCode
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
