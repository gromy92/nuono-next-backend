package com.nuono.next.competitoranalysis.noon;

import java.util.Comparator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Compatibility adapter for the legacy scheduled-detail execution path.
 *
 * <p>The competitor workflow no longer calls a product-detail endpoint. A target
 * that was not observed in today's ranking scan is resolved by an exact consumer
 * search and only list-card fields are returned.</p>
 */
@Component
@Profile("local-db")
public class HttpNoonProductDetailAdapter implements NoonProductDetailAdapter {
    private static final int EXACT_SEARCH_LIMIT = 20;

    private final NoonFrontendSearchAdapter searchAdapter;

    public HttpNoonProductDetailAdapter(
            NoonFrontendSearchAdapter searchAdapter
    ) {
        this.searchAdapter = searchAdapter;
    }

    @Override
    public NoonProductDetail fetch(NoonProductDetailRequest request) {
        String code = NoonProductCodeSupport.normalize(
                request == null ? null : request.getNoonProductCode()
        );
        if (!StringUtils.hasText(code)
                || NoonProductCodeSupport.codeType(code).isEmpty()) {
            throw providerFailure(
                    "INVALID_NOON_PRODUCT_CODE",
                    "Noon 列表补拉缺少有效商品码。",
                    null
            );
        }
        NoonSearchPage page = exactSearch(request, code, locale(request));
        NoonSearchResult result = exactResult(page, code);
        if (!StringUtils.hasText(result.getTitleEn())
                || !StringUtils.hasText(result.getTitleAr())) {
            NoonSearchPage alternatePage = exactSearch(
                    request,
                    code,
                    alternateLocale(locale(request), siteCode(request))
            );
            NoonSearchResult alternate = exactResultOrNull(
                    alternatePage,
                    code
            );
            mergeMissingListFields(result, alternate);
            NoonSearchEvidenceSupport.merge(page, alternatePage);
        }
        validateCurrency(request, page, result);
        return toListObservation(page, result, code);
    }

    private NoonSearchPage exactSearch(
            NoonProductDetailRequest request,
            String code,
            String searchLocale
    ) {
        return searchAdapter.search(
                NoonSearchRequest.builder()
                        .siteCode(siteCode(request))
                        .locale(searchLocale)
                        .keyword(code)
                        .limit(EXACT_SEARCH_LIMIT)
                        .build()
        );
    }

    private NoonSearchResult exactResult(
            NoonSearchPage page,
            String code
    ) {
        NoonSearchResult result = exactResultOrNull(page, code);
        if (result == null) {
            throw providerFailure(
                    "LIST_PRODUCT_NOT_FOUND",
                    "Noon 前台列表没有返回完全匹配的商品码。",
                    page
            );
        }
        return result;
    }

    private NoonSearchResult exactResultOrNull(
            NoonSearchPage page,
            String code
    ) {
        if (page == null) {
            return null;
        }
        return page.getResults().stream()
                .filter(item -> code.equals(NoonProductCodeSupport.normalize(
                        item == null ? null : item.getNoonProductCode()
                )))
                .min(Comparator
                        .comparing(NoonSearchResult::isSponsored)
                        .thenComparing(
                                HttpNoonProductDetailAdapter::rankPosition
                        )
                        .thenComparing(
                                HttpNoonProductDetailAdapter::resultPosition
                        ))
                .orElse(null);
    }

    private void mergeMissingListFields(
            NoonSearchResult target,
            NoonSearchResult alternate
    ) {
        if (target == null || alternate == null) {
            return;
        }
        if (!StringUtils.hasText(target.getTitleEn())) {
            target.setTitleEn(trim(alternate.getTitleEn()));
        }
        if (!StringUtils.hasText(target.getTitleAr())) {
            target.setTitleAr(trim(alternate.getTitleAr()));
        }
        if (!StringUtils.hasText(target.getTagsJson())) {
            target.setTagsJson(trim(alternate.getTagsJson()));
        }
    }

    private void validateCurrency(
            NoonProductDetailRequest request,
            NoonSearchPage page,
            NoonSearchResult result
    ) {
        String expected = expectedCurrency(siteCode(request));
        String actual = trim(result == null ? null : result.getCurrencyCode());
        if (!StringUtils.hasText(expected)) {
            return;
        }
        if (!StringUtils.hasText(actual)) {
            result.setCurrencyCode(expected);
            return;
        }
        if (!expected.equalsIgnoreCase(actual)) {
            throw providerFailure(
                    "LIST_SITE_CURRENCY_MISMATCH",
                    "Noon 前台列表币种与当前站点不一致。",
                    page
            );
        }
    }

    private NoonProductDetail toListObservation(
            NoonSearchPage page,
            NoonSearchResult result,
            String code
    ) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType(firstNonBlank(
                result.getCodeType(),
                NoonProductCodeSupport.codeType(code).orElse(null)
        ));
        detail.setDetailUrl(trim(result.getCanonicalUrl()));
        detail.setTitleEn(trim(firstNonBlank(
                result.getTitleEn(),
                result.getTitle()
        )));
        detail.setTitleAr(trim(result.getTitleAr()));
        detail.setPriceAmount(result.getPriceAmount());
        detail.setCurrencyCode(trim(result.getCurrencyCode()));
        detail.setMainImageUrlRaw(trim(result.getImageUrl()));
        detail.setMainImageUrlNormalized(trim(result.getImageUrl()));
        detail.setMainImageAssetKey(extractAssetKey(result.getImageUrl()));
        detail.setBadgesJson(trim(result.getTagsJson()));
        detail.setSnapshotHash(trim(page.getResponseHash()));
        detail.setProviderHttpStatus(page.getProviderHttpStatus());
        detail.setProviderSourceUrl(trim(page.getSourceUrl()));
        detail.setParserVersion(trim(page.getParserVersion()));
        detail.setAcquisitionMode("EXACT_SEARCH");
        detail.setCapturedAt(page.getCapturedAt());
        return detail;
    }

    private NoonSearchProviderException providerFailure(
            String errorCode,
            String message,
            NoonSearchPage page
    ) {
        return new NoonSearchProviderException(
                errorCode,
                message,
                page == null ? null : page.getProviderHttpStatus(),
                page == null ? null : page.getSourceUrl(),
                page == null ? null : page.getResponseHash()
        );
    }

    private String siteCode(NoonProductDetailRequest request) {
        return trim(request == null ? null : request.getSiteCode());
    }

    private String locale(NoonProductDetailRequest request) {
        return trim(request == null ? null : request.getLocale());
    }

    private String alternateLocale(String locale, String siteCode) {
        String language = StringUtils.hasText(locale)
                && locale.toLowerCase().contains("ar")
                ? "en"
                : "ar";
        String site = trim(siteCode);
        return StringUtils.hasText(site)
                ? language + "-" + site.toUpperCase()
                : language;
    }

    private String expectedCurrency(String siteCode) {
        String site = trim(siteCode);
        if ("SA".equalsIgnoreCase(site)) {
            return "SAR";
        }
        if ("AE".equalsIgnoreCase(site)) {
            return "AED";
        }
        if ("EG".equalsIgnoreCase(site)) {
            return "EGP";
        }
        return null;
    }

    private String extractAssetKey(String imageUrl) {
        String value = trim(imageUrl);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int hash = value.indexOf('#');
        if (hash >= 0) {
            value = value.substring(0, hash);
        }
        int slash = value.lastIndexOf('/');
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private static int rankPosition(NoonSearchResult result) {
        Integer value = result == null ? null : result.getRankPosition();
        return value == null || value < 1 ? Integer.MAX_VALUE : value;
    }

    private static int resultPosition(NoonSearchResult result) {
        Integer value = result == null ? null : result.getPosition();
        return value == null || value < 1 ? Integer.MAX_VALUE : value;
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
}
