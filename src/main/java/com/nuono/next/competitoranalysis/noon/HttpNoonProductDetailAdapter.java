package com.nuono.next.competitoranalysis.noon;

import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class HttpNoonProductDetailAdapter implements NoonProductDetailAdapter {
    private final NoonFrontendSearchAdapter searchAdapter;

    @Autowired
    public HttpNoonProductDetailAdapter(NoonFrontendSearchAdapter searchAdapter) {
        this.searchAdapter = searchAdapter;
    }

    @Override
    public NoonProductDetail fetch(NoonProductDetailRequest request) {
        String code = NoonProductCodeSupport.normalize(request == null ? null : request.getNoonProductCode());
        if (!StringUtils.hasText(code)) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 商品详情缺少有效商品码。",
                    null,
                    null,
                    null
            );
        }
        NoonSearchPage page = searchByProductCode(request, code);
        NoonSearchResult result = findExactResult(page, code);
        if (result == null) {
            throw new NoonSearchProviderException(
                    "PARSE_FAILED",
                    "Noon 商品详情 SKU 查询未返回精确商品码 " + code + "。",
                    page == null ? null : page.getProviderHttpStatus(),
                    page == null ? null : page.getSourceUrl(),
                    null
            );
        }
        return toDetail(request, page, result, code);
    }

    private NoonSearchPage searchByProductCode(NoonProductDetailRequest request, String code) {
        try {
            return searchAdapter.search(NoonSearchRequest.builder()
                    .siteCode(searchSiteCode(request))
                    .locale(searchLocale(request))
                    .keyword(code)
                    .limit(20)
                    .build());
        } catch (NoonSearchProviderException exception) {
            throw new NoonSearchProviderException(
                    exception.getErrorCode(),
                    "Noon 商品详情 SKU 查询失败：" + shrink(exception.getMessage(), 180),
                    exception.getProviderHttpStatus(),
                    exception.getSourceUrl(),
                    exception.getResponseHash()
            );
        } catch (RuntimeException exception) {
            throw new NoonSearchProviderException(
                    "PROVIDER_UNAVAILABLE",
                    "Noon 商品详情 SKU 查询暂不可用：" + shrink(exception.getMessage(), 180),
                    null,
                    null,
                    null
            );
        }
    }

    private NoonSearchResult findExactResult(NoonSearchPage page, String code) {
        if (page == null || page.getResults() == null) {
            return null;
        }
        for (NoonSearchResult result : page.getResults()) {
            String resultCode = NoonProductCodeSupport.normalize(result == null ? null : result.getNoonProductCode());
            if (code.equals(resultCode)) {
                return result;
            }
        }
        return null;
    }

    private NoonProductDetail toDetail(
            NoonProductDetailRequest request,
            NoonSearchPage page,
            NoonSearchResult result,
            String code
    ) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType(NoonProductCodeSupport.codeType(code).orElse(result.getCodeType()));
        detail.setDetailUrl(firstNonBlank(result.getCanonicalUrl(), request == null ? null : request.getCanonicalUrl()));
        detail.setTitleEn(trim(result.getTitle()));
        detail.setBrand(trim(result.getBrand()));
        detail.setPriceAmount(result.getPriceAmount());
        detail.setCurrencyCode(trim(result.getCurrencyCode()));
        detail.setRating(result.getRating());
        detail.setReviewCount(result.getReviewCount());
        detail.setMainImageUrlRaw(trim(result.getImageUrl()));
        detail.setMainImageUrlNormalized(trim(result.getImageUrl()));
        detail.setMainImageAssetKey(extractAssetKey(result.getImageUrl()));
        detail.setRawDetailJson(trim(result.getRawResultJson()));
        detail.setProviderHttpStatus(page == null ? null : page.getProviderHttpStatus());
        detail.setCapturedAt(page == null || page.getCapturedAt() == null ? LocalDateTime.now() : page.getCapturedAt());
        return detail;
    }

    private String searchSiteCode(NoonProductDetailRequest request) {
        String canonicalUrl = request == null ? null : request.getCanonicalUrl();
        String inferred = inferSiteCode(canonicalUrl);
        return firstNonBlank(inferred, request == null ? null : request.getSiteCode());
    }

    private String searchLocale(NoonProductDetailRequest request) {
        String canonicalUrl = request == null ? null : request.getCanonicalUrl();
        String inferred = inferLocale(canonicalUrl);
        return firstNonBlank(inferred, request == null ? null : request.getLocale());
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

    private String shrink(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.replaceAll("\\s+", " ").trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
