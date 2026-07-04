package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSiteOfferFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private StoreSyncMapper storeSyncMapper;

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductSiteOfferFetcher fetcher;

    private NoonSession session;

    @BeforeEach
    void setUp() {
        ProductProjectSiteResolver projectSiteResolver =
                new ProductProjectSiteResolver(objectMapper, storeSyncMapper, productNoonAdapter);
        fetcher = new ProductSiteOfferFetcher(objectMapper, productNoonAdapter, projectSiteResolver);
        session = noonSession("STR245027-NSA");
    }

    @Test
    void shouldFetchPricingAndStockForProjectSitesAndKeepReferenceNodes() throws Exception {
        ProductProjectSiteContext saSite = new ProductProjectSiteContext("STR245027-NSA", "SA", "ACTIVE");
        ProductProjectSiteContext aeSite = new ProductProjectSiteContext("STR245027-NAE", "AE", "PAUSED");
        JsonNode saPricing = objectMapper.readTree("{\"data\":[{"
                + "\"psku\":\"PARTNER-SKU-1\","
                + "\"offer_code\":\"OFF-SA\","
                + "\"price\":120,"
                + "\"sale_price\":99,"
                + "\"currency\":\"SAR\","
                + "\"final_price\":88,"
                + "\"price_source\":\"noon\","
                + "\"is_active\":true,"
                + "\"live_status\":true,"
                + "\"id_warranty\":12"
                + "}]}");
        JsonNode saStock = objectMapper.readTree("{\"data\":[{"
                + "\"psku_code\":\"PSKU-SA\","
                + "\"fbn_stock\":3,"
                + "\"supermall_stock\":2,"
                + "\"fbp_stock\":1"
                + "}]}");
        JsonNode aePricing = objectMapper.readTree("{\"data\":[{"
                + "\"psku\":\"PARTNER-SKU-1\","
                + "\"offer_code\":\"OFF-AE\","
                + "\"price\":220,"
                + "\"sale_price\":199,"
                + "\"currency\":\"AED\","
                + "\"final_price\":188"
                + "}]}");
        JsonNode aeStock = objectMapper.readTree("{\"data\":[{"
                + "\"psku_code\":\"PSKU-AE\","
                + "\"fbn_stock\":13,"
                + "\"supermall_fbn_stock\":12,"
                + "\"fbp_stock\":11"
                + "}]}");
        when(productNoonAdapter.postJson(any(NoonSession.class), any(String.class), any(JsonNode.class), eq(true)))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(1);
                    JsonNode body = invocation.getArgument(2);
                    if (NoonProductGateway.PRICING_INFO_URL.equals(url)) {
                        String countryCode = body.path("psku_list").path(0).path("country_code").asText();
                        return "AE".equals(countryCode) ? aePricing : saPricing;
                    }
                    String storeCode = body.path("noon_store_code").asText();
                    return "STR245027-NAE".equals(storeCode) ? aeStock : saStock;
                });

        ProductSiteOfferFetchResult result = fetcher.loadSiteOffers(
                session,
                List.of(saSite, aeSite),
                "STR245027-NSA",
                "1001",
                "PARTNER-SKU-1",
                "PSKU-SA",
                new ArrayList<>()
        );

        assertSame(saPricing, result.getReferencePricingNode());
        assertSame(saStock, result.getReferenceStockNode());
        assertEquals(2, result.getSiteOffers().size());
        Map<String, Object> saOffer = result.getSiteOffers().get(0);
        assertEquals("STR245027-NSA", saOffer.get("storeCode"));
        assertEquals("SA", saOffer.get("site"));
        assertEquals("ACTIVE", saOffer.get("statusCode"));
        assertEquals(true, saOffer.get("reference"));
        assertEquals("OFF-SA", saOffer.get("offerCode"));
        assertEquals("PARTNER-SKU-1", saOffer.get("partnerSku"));
        assertEquals("SAR", saOffer.get("currency"));
        assertEquals(120L, saOffer.get("price"));
        assertEquals(99L, saOffer.get("salePrice"));
        assertEquals(88L, saOffer.get("finalPrice"));
        assertEquals("noon", saOffer.get("priceSource"));
        assertEquals(true, saOffer.get("isActive"));
        assertEquals(true, saOffer.get("liveStatus"));
        assertEquals(12L, saOffer.get("idWarranty"));
        assertEquals("PSKU-SA", saOffer.get("pskuCode"));
        assertEquals(3L, saOffer.get("fbnStock"));
        assertEquals(2L, saOffer.get("supermallStock"));
        assertEquals(1L, saOffer.get("fbpStock"));

        Map<String, Object> aeOffer = result.getSiteOffers().get(1);
        assertEquals("STR245027-NAE", aeOffer.get("storeCode"));
        assertEquals("AE", aeOffer.get("site"));
        assertEquals("PAUSED", aeOffer.get("statusCode"));
        assertEquals(false, aeOffer.get("reference"));
        assertEquals("OFF-AE", aeOffer.get("offerCode"));
        assertEquals("AED", aeOffer.get("currency"));
        assertEquals(13L, aeOffer.get("fbnStock"));
        assertEquals(12L, aeOffer.get("supermallStock"));

        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.PRICING_INFO_URL),
                argThat(body -> pricingBodyMatches(body, "PARTNER-SKU-1", "SA", "1001")),
                eq(true)
        );
        verify(productNoonAdapter).postJson(
                any(NoonSession.class),
                eq(NoonProductGateway.PRICING_INFO_URL),
                argThat(body -> pricingBodyMatches(body, "PARTNER-SKU-1", "AE", "1001")),
                eq(true)
        );
    }

    @Test
    void shouldSkipRemoteCallsWhenPricingAndStockKeysAreMissing() {
        ProductProjectSiteContext site = new ProductProjectSiteContext("STR245027-NAE", null, null);
        List<String> warnings = new ArrayList<>();

        ProductSiteOfferFetchResult result = fetcher.loadSiteOffers(
                session,
                List.of(site),
                "STR245027-NAE",
                "",
                null,
                " ",
                warnings
        );

        assertEquals(List.of(
                "当前商品没有返回 id_partner，价格读取已跳过。",
                "当前索引缺少 partnerSku，站点价格读取已跳过。",
                "当前索引缺少 pskuCode，站点库存摘要读取已跳过。"
        ), warnings);
        assertEquals(1, result.getSiteOffers().size());
        Map<String, Object> offer = result.getSiteOffers().get(0);
        assertEquals("STR245027-NAE", offer.get("storeCode"));
        assertEquals(true, offer.get("reference"));
        verify(productNoonAdapter, never()).postJson(any(NoonSession.class), any(String.class), any(JsonNode.class), eq(true));
    }

    @Test
    void shouldReuseBaselineSiteOffersAndCreateMissingSkeletons() {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        Map<String, Object> existingOffer = new LinkedHashMap<>();
        existingOffer.put("storeCode", "STR245027-NSA");
        existingOffer.put("site", "SA");
        existingOffer.put("finalPrice", "88.00");
        snapshot.setSiteOffers(List.of(existingOffer));
        List<ProductProjectSiteContext> projectSites = List.of(
                new ProductProjectSiteContext("STR245027-NSA", "SA", "ACTIVE"),
                new ProductProjectSiteContext("STR245027-NAE", "AE", "PAUSED")
        );

        ProductSiteOfferFetchResult result = fetcher.reuseSiteOffersFromSnapshot(
                snapshot,
                projectSites,
                "STR245027-NAE"
        );

        assertEquals(2, result.getSiteOffers().size());
        assertEquals("88.00", result.getSiteOffers().get(0).get("finalPrice"));
        assertFalse(result.getSiteOffers().get(0).containsKey("reference"));
        Map<String, Object> skeleton = result.getSiteOffers().get(1);
        assertEquals("STR245027-NAE", skeleton.get("storeCode"));
        assertEquals("AE", skeleton.get("site"));
        assertEquals("PAUSED", skeleton.get("statusCode"));
        assertEquals(true, skeleton.get("reference"));
    }

    private boolean pricingBodyMatches(JsonNode body, String expectedPsku, String expectedSite, String expectedIdPartner) {
        JsonNode item = body.path("psku_list").path(0);
        return expectedPsku.equals(item.path("psku").asText())
                && expectedSite.equals(item.path("country_code").asText())
                && expectedIdPartner.equals(item.path("id_partner").asText());
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
