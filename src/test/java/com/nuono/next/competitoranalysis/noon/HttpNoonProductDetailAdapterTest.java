package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class HttpNoonProductDetailAdapterTest {

    @Test
    void fetchesProductDetailByExactSkuSearchResult() {
        LocalDateTime capturedAt = LocalDateTime.parse("2026-06-11T07:31:20");
        HttpNoonProductDetailAdapter adapter = new HttpNoonProductDetailAdapter(request -> {
            assertEquals("ZF47007A9D75977AB9A83Z", request.getKeyword());
            assertEquals("SA", request.getSiteCode());
            assertEquals("en-SA", request.getLocale());
            assertEquals(20, request.getLimit());
            return page(capturedAt, List.of(
                    result("ZOTHER001", "Other product"),
                    result("ZF47007A9D75977AB9A83Z", "30 Pcs Wooden Black HB Pencils")
            ));
        });
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("AE");
        request.setLocale("en-AE");
        request.setNoonProductCode("zf47007a9d75977ab9a83z");
        request.setCanonicalUrl("https://www.noon.com/saudi-en/fallback/ZF47007A9D75977AB9A83Z/p/");

        NoonProductDetail detail = adapter.fetch(request);

        assertEquals("ZF47007A9D75977AB9A83Z", detail.getNoonProductCode());
        assertEquals("Z_CODE", detail.getCodeType());
        assertEquals("30 Pcs Wooden Black HB Pencils", detail.getTitleEn());
        assertEquals(new BigDecimal("32.95"), detail.getPriceAmount());
        assertEquals("SAR", detail.getCurrencyCode());
        assertEquals(new BigDecimal("4.40"), detail.getRating());
        assertEquals(47, detail.getReviewCount());
        assertEquals("https://www.noon.com/saudi-en/30-pcs/ZF47007A9D75977AB9A83Z/p/", detail.getDetailUrl());
        assertEquals("pzsku/ZF47007A9D75977AB9A83Z/45/main", detail.getMainImageAssetKey());
        assertEquals("{\"sku\":\"ZF47007A9D75977AB9A83Z\"}", detail.getRawDetailJson());
        assertEquals(200, detail.getProviderHttpStatus());
        assertEquals(capturedAt, detail.getCapturedAt());
    }

    @Test
    void failsWhenSkuSearchDoesNotReturnExactProductCode() {
        HttpNoonProductDetailAdapter adapter = new HttpNoonProductDetailAdapter(request ->
                page(LocalDateTime.parse("2026-06-11T07:31:20"), List.of(result("ZOTHER001", "Other product")))
        );
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("ZF47007A9D75977AB9A83Z");

        NoonSearchProviderException error = assertThrows(NoonSearchProviderException.class, () -> adapter.fetch(request));

        assertEquals("PARSE_FAILED", error.getErrorCode());
        assertEquals(200, error.getProviderHttpStatus());
    }

    private static NoonSearchPage page(LocalDateTime capturedAt, List<NoonSearchResult> results) {
        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl("http://123.60.15.70/nuono-noon-v3-gateway/search?q=ZF47007A9D75977AB9A83Z");
        page.setProviderHttpStatus(200);
        page.setCapturedAt(capturedAt);
        page.setResults(results);
        return page;
    }

    private static NoonSearchResult result(String code, String title) {
        NoonSearchResult result = new NoonSearchResult();
        result.setNoonProductCode(code);
        result.setCodeType("Z_CODE");
        result.setCanonicalUrl("https://www.noon.com/saudi-en/30-pcs/" + code + "/p/");
        result.setTitle(title);
        result.setBrand("EduPrint Hub");
        result.setImageUrl("https://f.nooncdn.com/p/pzsku/" + code + "/45/main.jpg");
        result.setPriceAmount(new BigDecimal("32.95"));
        result.setCurrencyCode("SAR");
        result.setRating(new BigDecimal("4.40"));
        result.setReviewCount(47);
        result.setRawResultJson("{\"sku\":\"" + code + "\"}");
        return result;
    }
}
