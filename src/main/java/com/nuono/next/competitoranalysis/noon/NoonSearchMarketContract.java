package com.nuono.next.competitoranalysis.noon;

import java.util.Locale;
import org.springframework.util.StringUtils;

final class NoonSearchMarketContract {
    private NoonSearchMarketContract() {
    }

    static NoonSearchPage apply(
            NoonSearchPage page,
            NoonSearchRequest request
    ) {
        if (page == null) {
            return null;
        }
        boolean arabic = locale(request).contains("ar");
        String expectedCurrency = currency(request);
        for (NoonSearchResult result : page.getResults()) {
            if (result == null) {
                continue;
            }
            assignLocalizedTitle(result, arabic);
            validateCurrency(page, result, expectedCurrency);
        }
        return page;
    }

    private static void assignLocalizedTitle(
            NoonSearchResult result,
            boolean arabic
    ) {
        if (!StringUtils.hasText(result.getTitle())) {
            return;
        }
        if (arabic && !StringUtils.hasText(result.getTitleAr())) {
            result.setTitleAr(result.getTitle().trim());
        } else if (!arabic
                && !StringUtils.hasText(result.getTitleEn())) {
            result.setTitleEn(result.getTitle().trim());
        }
    }

    private static void validateCurrency(
            NoonSearchPage page,
            NoonSearchResult result,
            String expected
    ) {
        if (!StringUtils.hasText(result.getCurrencyCode())) {
            result.setCurrencyCode(expected);
            return;
        }
        if (!expected.equalsIgnoreCase(
                result.getCurrencyCode().trim()
        )) {
            throw new NoonSearchProviderException(
                    "LIST_SITE_CURRENCY_MISMATCH",
                    "Noon 前台列表币种与当前站点不一致。",
                    page.getProviderHttpStatus(),
                    page.getSourceUrl(),
                    page.getResponseHash()
            );
        }
    }

    private static String currency(NoonSearchRequest request) {
        String site = site(request);
        if ("AE".equals(site) || "UAE".equals(site)) {
            return "AED";
        }
        if ("EG".equals(site)
                || "EGY".equals(site)
                || "EGYPT".equals(site)) {
            return "EGP";
        }
        return "SAR";
    }

    private static String locale(NoonSearchRequest request) {
        String value = request == null ? null : request.getLocale();
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String site(NoonSearchRequest request) {
        String value = request == null ? null : request.getSiteCode();
        return value == null
                ? ""
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
