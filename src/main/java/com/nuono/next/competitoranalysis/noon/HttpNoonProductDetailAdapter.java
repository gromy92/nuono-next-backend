package com.nuono.next.competitoranalysis.noon;

import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailAdapter;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailRequest;
import com.nuono.next.productpublicdetail.noon.NoonPublicProductDetailResult;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class HttpNoonProductDetailAdapter implements NoonProductDetailAdapter {
    private static final String FRONTEND_CATALOG_DETAIL_PARSER_VERSION = "noon-frontend-catalog-detail-v1";

    private final NoonPublicProductDetailAdapter publicDetailAdapter;

    @Autowired
    public HttpNoonProductDetailAdapter(NoonPublicProductDetailAdapter publicDetailAdapter) {
        this.publicDetailAdapter = publicDetailAdapter;
    }

    @Override
    public NoonProductDetail fetch(NoonProductDetailRequest request) {
        String code = NoonProductCodeSupport.normalize(request == null ? null : request.getNoonProductCode());
        if (!StringUtils.hasText(code) || NoonProductCodeSupport.codeType(code).isEmpty()) {
            throw providerFailure("INVALID_NOON_PRODUCT_CODE", "Noon 商品详情缺少有效商品码。", null);
        }

        NoonPublicProductDetailResult result = publicDetailAdapter.fetch(
                NoonPublicProductDetailRequest.builder()
                        .siteCode(detailSiteCode(request))
                        .locale(detailLocale(request))
                        .noonProductCode(code)
                        .build()
        );
        requireUsableResult(result, code);
        return toDetail(result, code);
    }

    private void requireUsableResult(NoonPublicProductDetailResult result, String expectedCode) {
        if (result == null) {
            throw providerFailure("PROVIDER_UNAVAILABLE", "Noon 前台商品详情未返回结果。", null);
        }
        ProductPublicDetailSyncStatus status = result.getStatus();
        if (status != ProductPublicDetailSyncStatus.SUCCEEDED
                && status != ProductPublicDetailSyncStatus.PARTIAL) {
            throw providerFailure(
                    firstNonBlank(result.getFailureCode(), defaultFailureCode(status)),
                    firstNonBlank(result.getFailureMessage(), "Noon 前台商品详情抓取失败。"),
                    result
            );
        }
        if (status == ProductPublicDetailSyncStatus.PARTIAL
                && !FRONTEND_CATALOG_DETAIL_PARSER_VERSION.equals(result.getProviderParserVersion())) {
            throw providerFailure(
                    "DETAIL_SOURCE_NOT_PRODUCT_DETAIL",
                    "Noon 前台仅返回搜索基础字段，未写入竞品详情快照。",
                    result
            );
        }
        String actualCode = NoonProductCodeSupport.normalize(result.getNoonProductCode());
        if (!expectedCode.equals(actualCode)) {
            throw providerFailure("PARSE_FAILED", "Noon 前台商品详情返回的商品码不匹配。", result);
        }
    }

    private NoonProductDetail toDetail(NoonPublicProductDetailResult result, String code) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType(firstNonBlank(
                result.getCodeType(),
                NoonProductCodeSupport.codeType(code).orElse(null)
        ));
        detail.setDetailUrl(trim(result.getDetailUrl()));
        detail.setTitleEn(trim(result.getTitleEn()));
        detail.setTitleAr(trim(result.getTitleAr()));
        detail.setBrand(trim(result.getBrand()));
        detail.setPriceAmount(result.getPriceAmount());
        detail.setCurrencyCode(trim(result.getCurrencyCode()));
        detail.setRating(result.getRating());
        detail.setReviewCount(result.getReviewCount());
        detail.setMainImageUrlRaw(trim(result.getMainImageUrl()));
        detail.setMainImageUrlNormalized(trim(result.getMainImageUrl()));
        detail.setMainImageAssetKey(extractAssetKey(result.getMainImageUrl()));
        detail.setAvailabilityStatus(trim(result.getAvailabilityText()));
        detail.setSnapshotHash(trim(result.getProviderResponseHash()));
        detail.setRawDetailJson(trim(result.getRawPayloadJson()));
        detail.setProviderHttpStatus(result.getProviderHttpStatus());
        detail.setCapturedAt(result.getFetchedAt());
        return detail;
    }

    private NoonSearchProviderException providerFailure(
            String errorCode,
            String message,
            NoonPublicProductDetailResult result
    ) {
        return new NoonSearchProviderException(
                errorCode,
                message,
                result == null ? null : result.getProviderHttpStatus(),
                result == null ? null : result.getProviderSourceUrl(),
                result == null ? null : result.getProviderResponseHash()
        );
    }

    private String defaultFailureCode(ProductPublicDetailSyncStatus status) {
        return status == ProductPublicDetailSyncStatus.NOT_FOUND
                ? "PUBLIC_DETAIL_NOT_FOUND"
                : "PROVIDER_UNAVAILABLE";
    }

    private String detailSiteCode(NoonProductDetailRequest request) {
        return firstNonBlank(
                inferSiteCode(request == null ? null : request.getCanonicalUrl()),
                request == null ? null : request.getSiteCode()
        );
    }

    private String detailLocale(NoonProductDetailRequest request) {
        return firstNonBlank(
                inferLocale(request == null ? null : request.getCanonicalUrl()),
                request == null ? null : request.getLocale()
        );
    }

    private String inferSiteCode(String canonicalUrl) {
        String value = lower(canonicalUrl);
        if (value.contains("/uae-")) {
            return "AE";
        }
        if (value.contains("/egypt-") || value.contains("/egy-")) {
            return "EG";
        }
        if (value.contains("/saudi-") || value.contains("/ksa-")) {
            return "SA";
        }
        return null;
    }

    private String inferLocale(String canonicalUrl) {
        String value = lower(canonicalUrl);
        if (value.contains("/uae-ar")) {
            return "ar-AE";
        }
        if (value.contains("/uae-en")) {
            return "en-AE";
        }
        if (value.contains("/egypt-ar") || value.contains("/egy-ar")) {
            return "ar-EG";
        }
        if (value.contains("/egypt-en") || value.contains("/egy-en")) {
            return "en-EG";
        }
        if (value.contains("/saudi-ar") || value.contains("/ksa-ar")) {
            return "ar-SA";
        }
        if (value.contains("/saudi-en") || value.contains("/ksa-en")) {
            return "en-SA";
        }
        return null;
    }

    private String extractAssetKey(String imageUrl) {
        String value = trim(imageUrl);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int index = value.indexOf("/p/");
        if (index >= 0) {
            value = value.substring(index + 3);
        }
        value = value.replaceFirst("^/+", "");
        value = value.replaceFirst("\\?.*$", "");
        value = value.replaceFirst("\\.(jpg|jpeg|png|webp)$", "");
        return StringUtils.hasText(value) ? value : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
