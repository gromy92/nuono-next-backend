package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductPublishOfferWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ProductNoonAdapter productNoonAdapter;

    private ProductPublishOfferWriter writer;

    @BeforeEach
    void setUp() {
        writer = new ProductPublishOfferWriter(objectMapper, productNoonAdapter);
    }

    @Test
    void shouldDefaultOfferPayloadToNoWarrantyWhenWarrantyWasNotRead() {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", "STR245027-NAE");
        siteOffer.put("site", "AE");
        siteOffer.put("price", "48.00");

        ObjectNode body = writer.buildOfferUpsertBody("PSKU-001", siteOffer);
        JsonNode offer = body.path("pskus").get(0);

        assertEquals("PSKU-001", offer.path("pskuCode").asText());
        assertEquals("ae", offer.path("country").asText());
        assertEquals("manual", offer.path("pricingMethod").asText());
        assertEquals(48.0, offer.path("price").asDouble());
        assertEquals(0, offer.path("idWarranty").asInt());
        assertTrue(offer.path("pricingRule").isNull());
        assertTrue(offer.path("priceEngineMin").isNull());
        assertTrue(offer.path("priceEngineMax").isNull());
    }

    @Test
    void shouldDefaultMissingSaleWindowToTenYearsWhenPublishingSalePrice() {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("salePrice", "39.20");

        Map<String, String> saleWindow = writer.saleWindowForPublish(siteOffer);

        LocalDate saleStart = LocalDate.parse(saleWindow.get("saleStart"));
        LocalDate saleEnd = LocalDate.parse(saleWindow.get("saleEnd"));
        assertTrue(!saleStart.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(1)));
        assertTrue(!saleStart.isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(1)));
        assertEquals(saleStart.plusYears(10), saleEnd);
    }

    @Test
    void shouldPublishOfferWithLocaleHeaderAndStockWarning() {
        Map<String, Object> siteOffer = new LinkedHashMap<>();
        siteOffer.put("storeCode", "STR245027-KSA");
        siteOffer.put("price", "48.00");
        siteOffer.put("salePrice", "39.20");
        siteOffer.put("fbnStock", 12);
        List<String> warnings = new ArrayList<>();

        writer.publishOffer(null, "PSKU-001", siteOffer, warnings);

        ArgumentCaptor<ObjectNode> bodyCaptor = ArgumentCaptor.forClass(ObjectNode.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(productNoonAdapter).postWriteJson(
                nullable(NoonSession.class),
                eq(NoonProductGateway.OFFER_UPSERT_URL),
                bodyCaptor.capture(),
                eq(false),
                headersCaptor.capture()
        );
        assertEquals("sa", bodyCaptor.getValue().path("pskus").get(0).path("country").asText());
        assertEquals("en-SA", headersCaptor.getValue().get("X-Locale"));
        assertTrue(warnings.contains("当前页面展示的是库存汇总，本轮发布不会直接改 Noon 仓库库存。"));
    }
}
