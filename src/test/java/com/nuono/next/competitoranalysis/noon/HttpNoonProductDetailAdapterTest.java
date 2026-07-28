package com.nuono.next.competitoranalysis.noon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class HttpNoonProductDetailAdapterTest {

    @Test
    void fetchesProductDetailFromConsumerFrontendDetailCapability() {
        LocalDateTime capturedAt = LocalDateTime.parse("2026-06-11T07:31:20");
        NoonPublicProductDetailAdapter publicDetailAdapter = request -> {
            assertEquals("ZF47007A9D75977AB9A83Z", request.getNoonProductCode());
            assertEquals("SA", request.getSiteCode());
            assertEquals("en-SA", request.getLocale());
            return detailResult(ProductPublicDetailSyncStatus.SUCCEEDED, capturedAt);
        };
        HttpNoonProductDetailAdapter adapter = new HttpNoonProductDetailAdapter(publicDetailAdapter);
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
    void preservesConsumerFrontendFailureWithoutSearchFallback() {
        NoonPublicProductDetailResult failed = new NoonPublicProductDetailResult();
        failed.setStatus(ProductPublicDetailSyncStatus.FAILED);
        failed.setFailureCode("BLOCKED_BY_RISK_CONTROL");
        failed.setFailureMessage("Noon 前台详情被风控阻断。");
        failed.setProviderHttpStatus(403);
        failed.setProviderSourceUrl("https://www.noon.com/_vs/catalog/detail");
        failed.setProviderResponseHash("response-hash");
        HttpNoonProductDetailAdapter adapter = new HttpNoonProductDetailAdapter(request -> failed);
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("ZF47007A9D75977AB9A83Z");

        NoonSearchProviderException error = assertThrows(NoonSearchProviderException.class, () -> adapter.fetch(request));

        assertEquals("BLOCKED_BY_RISK_CONTROL", error.getErrorCode());
        assertEquals(403, error.getProviderHttpStatus());
        assertEquals("https://www.noon.com/_vs/catalog/detail", error.getSourceUrl());
        assertEquals("response-hash", error.getResponseHash());
    }

    @Test
    void rejectsSearchOnlyPartialResultAsProductDetail() {
        NoonPublicProductDetailResult partial = detailResult(
                ProductPublicDetailSyncStatus.PARTIAL,
                LocalDateTime.parse("2026-06-11T07:31:20")
        );
        partial.setFailureCode("PARTIAL_DETAIL");
        partial.setFailureMessage("exact search 只返回基础公开字段");
        partial.setProviderParserVersion("noon-catalog-search-v3");
        HttpNoonProductDetailAdapter adapter = new HttpNoonProductDetailAdapter(request -> partial);
        NoonProductDetailRequest request = new NoonProductDetailRequest();
        request.setSiteCode("SA");
        request.setLocale("en-SA");
        request.setNoonProductCode("ZF47007A9D75977AB9A83Z");

        NoonSearchProviderException error = assertThrows(NoonSearchProviderException.class, () -> adapter.fetch(request));

        assertEquals("DETAIL_SOURCE_NOT_PRODUCT_DETAIL", error.getErrorCode());
    }

    private static NoonPublicProductDetailResult detailResult(
            ProductPublicDetailSyncStatus status,
            LocalDateTime capturedAt
    ) {
        NoonPublicProductDetailResult result = new NoonPublicProductDetailResult();
        result.setStatus(status);
        result.setNoonProductCode("ZF47007A9D75977AB9A83Z");
        result.setCodeType("Z_CODE");
        result.setTitleEn("30 Pcs Wooden Black HB Pencils");
        result.setBrand("EduPrint Hub");
        result.setPriceAmount(new BigDecimal("32.95"));
        result.setCurrencyCode("SAR");
        result.setRating(new BigDecimal("4.40"));
        result.setReviewCount(47);
        result.setAvailabilityText("IN_STOCK");
        result.setMainImageUrl("https://f.nooncdn.com/p/pzsku/ZF47007A9D75977AB9A83Z/45/main.jpg");
        result.setDetailUrl("https://www.noon.com/saudi-en/30-pcs/ZF47007A9D75977AB9A83Z/p/");
        result.setRawPayloadJson("{\"sku\":\"ZF47007A9D75977AB9A83Z\"}");
        result.setProviderHttpStatus(200);
        result.setProviderParserVersion("noon-frontend-catalog-detail-v1");
        result.setFetchedAt(capturedAt);
        return result;
    }
}
