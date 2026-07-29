package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HttpNoonProductDetailAdapterTest {

    @Test
    void resolvesAnExactCodeFromConsumerSearchUsingListFieldsOnly() {
        LocalDateTime capturedAt = LocalDateTime.parse(
                "2026-07-29T02:00:00"
        );
        NoonFrontendSearchAdapter searchAdapter = request -> {
            assertEquals(
                    "ZF47007A9D75977AB9A83Z",
                    request.getKeyword()
            );
            assertEquals(20, request.getLimit());
            return page(capturedAt, List.of(
                    result("NOTHER001", false, 1),
                    result("ZF47007A9D75977AB9A83Z", true, 1),
                    result("ZF47007A9D75977AB9A83Z", false, 2)
            ));
        };
        HttpNoonProductDetailAdapter adapter =
                new HttpNoonProductDetailAdapter(searchAdapter);
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("zf47007a9d75977ab9a83z");

        NoonProductDetail detail = adapter.fetch(request);

        assertEquals(
                "ZF47007A9D75977AB9A83Z",
                detail.getNoonProductCode()
        );
        assertEquals("English list title", detail.getTitleEn());
        assertEquals("عنوان القائمة", detail.getTitleAr());
        assertEquals(new BigDecimal("32.95"), detail.getPriceAmount());
        assertEquals("SAR", detail.getCurrencyCode());
        assertEquals(
                "https://f.nooncdn.com/p/list-main.jpg",
                detail.getMainImageUrlNormalized()
        );
        assertEquals("{\"badges\":[\"Best Seller\"]}", detail.getBadgesJson());
        assertNull(detail.getBrand());
        assertNull(detail.getRating());
        assertNull(detail.getReviewCount());
        assertNull(detail.getRawDetailJson());
        assertEquals(200, detail.getProviderHttpStatus());
        assertEquals(capturedAt, detail.getCapturedAt());
    }

    @Test
    void rejectsSearchResultsWithoutAnExactCodeMatch() {
        NoonFrontendSearchAdapter searchAdapter = request -> page(
                LocalDateTime.parse("2026-07-29T02:00:00"),
                List.of(result("NOTHER001", false, 1))
        );
        HttpNoonProductDetailAdapter adapter =
                new HttpNoonProductDetailAdapter(searchAdapter);
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("ZF47007A9D75977AB9A83Z");

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> adapter.fetch(request)
        );

        assertEquals("LIST_PRODUCT_NOT_FOUND", error.getErrorCode());
    }

    @Test
    void supplementsAMissingArabicTitleFromTheAlternateListLocale() {
        AtomicInteger requests = new AtomicInteger();
        NoonFrontendSearchAdapter searchAdapter = request -> {
            NoonSearchResult result = result(
                    "ZF47007A9D75977AB9A83Z",
                    false,
                    1
            );
            if (requests.getAndIncrement() == 0) {
                assertEquals("en-SA", request.getLocale());
                result.setTitleAr(null);
            } else {
                assertEquals("ar-SA", request.getLocale());
                result.setTitleEn(null);
            }
            return page(
                    LocalDateTime.parse("2026-07-29T02:00:00"),
                    List.of(result)
            );
        };
        HttpNoonProductDetailAdapter adapter =
                new HttpNoonProductDetailAdapter(searchAdapter);
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("ZF47007A9D75977AB9A83Z");

        NoonProductDetail detail = adapter.fetch(request);

        assertEquals(2, requests.get());
        assertEquals("English list title", detail.getTitleEn());
        assertEquals("عنوان القائمة", detail.getTitleAr());
        assertNotEquals(
                "list-response-hash",
                detail.getSnapshotHash()
        );
    }

    @Test
    void rejectsAListPriceFromAnotherSitesCurrency() {
        NoonFrontendSearchAdapter searchAdapter = request -> {
            NoonSearchResult result = result(
                    "ZF47007A9D75977AB9A83Z",
                    false,
                    1
            );
            result.setCurrencyCode("AED");
            return page(
                    LocalDateTime.parse("2026-07-29T02:00:00"),
                    List.of(result)
            );
        };
        HttpNoonProductDetailAdapter adapter =
                new HttpNoonProductDetailAdapter(searchAdapter);
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("ZF47007A9D75977AB9A83Z");

        NoonSearchProviderException error = assertThrows(
                NoonSearchProviderException.class,
                () -> adapter.fetch(request)
        );

        assertEquals(
                "LIST_SITE_CURRENCY_MISMATCH",
                error.getErrorCode()
        );
    }

    private static NoonSearchPage page(
            LocalDateTime capturedAt,
            List<NoonSearchResult> results
    ) {
        NoonSearchPage page = new NoonSearchPage();
        page.setSourceUrl("https://www.noon.com/search");
        page.setProviderHttpStatus(200);
        page.setResponseHash("list-response-hash");
        page.setCapturedAt(capturedAt);
        page.setResults(results);
        return page;
    }

    private static NoonSearchResult result(
            String code,
            boolean sponsored,
            int position
    ) {
        NoonSearchResult result = new NoonSearchResult();
        result.setNoonProductCode(code);
        result.setCodeType(code.startsWith("Z") ? "Z_CODE" : "N_CODE");
        result.setPosition(position);
        result.setRankPosition(position);
        result.setSponsored(sponsored);
        result.setCanonicalUrl(
                "https://www.noon.com/saudi-en/list/" + code + "/p/"
        );
        result.setTitle("English list title");
        result.setTitleEn("English list title");
        result.setTitleAr("عنوان القائمة");
        result.setPriceAmount(new BigDecimal("32.95"));
        result.setCurrencyCode("SAR");
        result.setImageUrl("https://f.nooncdn.com/p/list-main.jpg");
        result.setTagsJson("{\"badges\":[\"Best Seller\"]}");
        result.setBrand("must not be copied");
        result.setRating(new BigDecimal("4.5"));
        result.setReviewCount(99);
        result.setRawResultJson("{\"must\":\"not be copied\"}");
        return result;
    }
}
