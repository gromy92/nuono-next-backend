package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    @Test
    void recordsCapturedAtAcrossShanghaiBusinessMidnight() {
        assertCapturedAt("2026-07-26T15:59:59Z", "2026-07-26T23:59:59");
        assertCapturedAt("2026-07-26T16:00:00Z", "2026-07-27T00:00:00");
    }

    private static void assertCapturedAt(String instant, String expected) {
        NoonProductDetailPageParser parser = new NoonProductDetailPageParser(
                new ObjectMapper(),
                Clock.fixed(Instant.parse(instant), ZoneOffset.UTC)
        );
        NoonProductDetail detail = parser.parse(
                "{\"sku\":\"ZCOMP001\",\"name\":\"Detail title\"}",
                "https://www.noon.com/saudi-en/sample/ZCOMP001/p/",
                200
        );
        assertEquals(LocalDateTime.parse(expected), detail.getCapturedAt());
    }
}
