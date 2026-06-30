package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ProductNoonCatalogContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ProductNoonCatalogContentService service = new ProductNoonCatalogContentService(null);

    @Test
    void parseCatalogContentMapsFollowSellTitleAndImages() throws Exception {
        String json = "{"
                + "\"product\":{"
                + "\"sku\":\"N53437240A\","
                + "\"product_title\":\"AirPods Pro 2 Wireless Earbuds\","
                + "\"brand\":\"Apple\","
                + "\"long_description\":\"Wireless earbuds with active noise cancellation.\","
                + "\"feature_bullets\":[\"USB-C charging\",\"Transparency mode\"],"
                + "\"image_keys\":["
                + "\"pnsku/N53437240A/45/_/1711111111/main\","
                + "\"p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b\","
                + "\"https://example.com/already-ready.jpg\""
                + "]"
                + "}"
                + "}";

        JsonNode root = objectMapper.readTree(json);
        ProductNoonCatalogContentService.CatalogContent content = service.parseCatalogContent(root, "en");

        assertEquals("N53437240A", content.getCatalogSku());
        assertEquals("Apple", content.getBrand());
        assertEquals("AirPods Pro 2 Wireless Earbuds", content.getTitleEn());
        assertEquals("Wireless earbuds with active noise cancellation.", content.getDescriptionEn());
        assertEquals(2, content.getHighlightsEn().size());
        assertEquals("https://f.nooncdn.com/p/pnsku/N53437240A/45/_/1711111111/main.jpg", content.getImages().get(0));
        assertEquals(
                "https://f.nooncdn.com/p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b.jpg",
                content.getImages().get(1)
        );
        assertTrue(content.hasUsableContent());
    }
}
