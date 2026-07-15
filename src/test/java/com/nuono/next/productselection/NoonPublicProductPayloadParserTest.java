package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class NoonPublicProductPayloadParserTest {

    @Test
    void parseJsonDoesNotDoublePrefixProductScopedNoonImageKey() {
        NoonPublicProductPayloadParser parser = new NoonPublicProductPayloadParser(
                new ProductSelectionSourceCollectionHtmlParser(),
                new ObjectMapper()
        );
        String json = "{"
                + "\"product_title\":\"Noon item\","
                + "\"sku\":\"ZTEST\","
                + "\"image_keys\":["
                + "\"p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b\""
                + "]"
                + "}";

        NoonPublicProductSnapshot snapshot = parser.parseJson(json);

        assertEquals(
                "https://f.nooncdn.com/p/pzsku/Z92550AC9ECB3A39E5B7AZ/45/1768272072/75cf5a38-3af3-4055-ae03-df18f1d3912b.jpg",
                snapshot.imageUrls.get(0)
        );
    }

    @Test
    void parsesNoonBreadcrumbsAsCompetitorCategoryLinks() {
        NoonPublicProductPayloadParser parser = new NoonPublicProductPayloadParser(
                new ProductSelectionSourceCollectionHtmlParser(),
                new ObjectMapper()
        );
        String json = "{\"product\":{"
                + "\"product_title\":\"Kitchen tool\","
                + "\"sku\":\"ZTEST\","
                + "\"breadcrumbs\":["
                + "{\"code\":\"\",\"name\":\"Home\"},"
                + "{\"code\":\"home-and-kitchen\",\"name\":\"Home & Kitchen\"},"
                + "{\"code\":\"home-and-kitchen/kitchen-and-dining\",\"name\":\"Kitchen & Dining\"},"
                + "{\"code\":\"home-and-kitchen/kitchen-and-dining/kitchen-utensils-and-gadgets\",\"name\":\"Kitchen Utensils & Gadgets\"}"
                + "]}}";

        NoonPublicProductSnapshot snapshot = parser.parseJson(
                json,
                "https://www.noon.com/saudi-en/kitchen-tool/ZTEST/p/"
        );

        assertEquals(1, snapshot.categoryLinks.size());
        assertEquals(
                "Home & Kitchen > Kitchen & Dining > Kitchen Utensils & Gadgets",
                snapshot.categoryLinks.get(0).getPath()
        );
        assertEquals(
                "https://www.noon.com/saudi-en/home-and-kitchen/kitchen-and-dining/kitchen-utensils-and-gadgets/",
                snapshot.categoryLinks.get(0).getUrl()
        );
    }
}
