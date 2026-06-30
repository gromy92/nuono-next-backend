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
}
