package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NoonProductDetailPageParserTest {

    @Test
    void parsesProductDetailFromEmbeddedJsonPayload() {
        NoonProductDetailPageParser parser = new NoonProductDetailPageParser(new ObjectMapper());
        String json = "{"
                + "\"props\":{"
                + "\"pageProps\":{"
                + "\"product\":{"
                + "\"sku\":\"ZCOMP001\","
                + "\"name_en\":\"Detail title\","
                + "\"name_ar\":\"عنوان\","
                + "\"brand\":{\"name\":\"Detail brand\"},"
                + "\"seller\":{\"name\":\"Detail seller\"},"
                + "\"price\":{\"amount\":\"12.34\",\"currency\":\"SAR\"},"
                + "\"rating\":{\"value\":\"4.6\",\"count\":321},"
                + "\"image_keys\":[\"detail/image-key\"],"
                + "\"availability_status\":\"IN_STOCK\","
                + "\"badges\":[{\"text\":\"Best seller\"}]"
                + "}"
                + "}"
                + "}"
                + "}";

        NoonProductDetail detail = parser.parse(
                json,
                "https://www.noon.com/saudi-en/sample/ZCOMP001/p/",
                200
        );

        assertEquals("ZCOMP001", detail.getNoonProductCode());
        assertEquals("Z_CODE", detail.getCodeType());
        assertEquals("Detail title", detail.getTitleEn());
        assertEquals("عنوان", detail.getTitleAr());
        assertEquals("Detail brand", detail.getBrand());
        assertEquals("Detail seller", detail.getSellerName());
        assertEquals(0, new BigDecimal("12.34").compareTo(detail.getPriceAmount()));
        assertEquals("SAR", detail.getCurrencyCode());
        assertEquals(0, new BigDecimal("4.6").compareTo(detail.getRating()));
        assertEquals(321, detail.getReviewCount());
        assertEquals("https://f.nooncdn.com/p/detail/image-key.jpg", detail.getMainImageUrlNormalized());
        assertEquals("IN_STOCK", detail.getAvailabilityStatus());
    }
}
