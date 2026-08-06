package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.competitoranalysis.noon.NoonProductCodeSupport;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import java.util.Comparator;
import org.springframework.util.StringUtils;

/** Exact-code selection and list-only localized field merge. */
final class Dp08ListResultSupport {
    private Dp08ListResultSupport() {
    }

    static NoonSearchResult exact(NoonSearchPage page, String expectedCode) {
        String code = NoonProductCodeSupport.normalize(expectedCode);
        if (page == null || code == null) {
            return null;
        }
        return page.getResults().stream()
                .filter(item -> code.equals(NoonProductCodeSupport.normalize(
                        item == null ? null : item.getNoonProductCode()
                )))
                .min(Comparator
                        .comparing(NoonSearchResult::isSponsored)
                        .thenComparing(Dp08ListResultSupport::rank)
                        .thenComparing(Dp08ListResultSupport::position))
                .orElse(null);
    }

    static NoonProductDetail toDetail(
            Dp08ListTarget target,
            NoonSearchPage page,
            NoonSearchResult result
    ) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(target.getNoonProductCode());
        detail.setCodeType(NoonProductCodeSupport.codeType(target.getNoonProductCode()).orElse(null));
        detail.setDetailUrl(trim(result.getCanonicalUrl()));
        detail.setTitleEn(trim(first(result.getTitleEn(), result.getTitle())));
        detail.setTitleAr(trim(result.getTitleAr()));
        detail.setPriceAmount(result.getPriceAmount());
        detail.setCurrencyCode(trim(result.getCurrencyCode()));
        detail.setMainImageUrlRaw(trim(result.getImageUrl()));
        detail.setMainImageUrlNormalized(trim(result.getImageUrl()));
        detail.setBadgesJson(trim(result.getTagsJson()));
        detail.setSnapshotHash(trim(page.getResponseHash()));
        detail.setProviderHttpStatus(page.getProviderHttpStatus());
        detail.setProviderSourceUrl(trim(page.getSourceUrl()));
        detail.setParserVersion(trim(page.getParserVersion()));
        detail.setAcquisitionMode("EXACT_SEARCH");
        detail.setCapturedAt(page.getCapturedAt());
        return detail;
    }

    static void mergeAlternate(NoonProductDetail target, NoonSearchPage page, NoonSearchResult result) {
        if (target == null || result == null) {
            return;
        }
        if (!StringUtils.hasText(target.getTitleEn())) {
            target.setTitleEn(trim(first(result.getTitleEn(), result.getTitle())));
        }
        if (!StringUtils.hasText(target.getTitleAr())) {
            target.setTitleAr(trim(result.getTitleAr()));
        }
        if (!StringUtils.hasText(target.getBadgesJson())) {
            target.setBadgesJson(trim(result.getTagsJson()));
        }
        if (page != null && page.getCapturedAt() != null
                && (target.getCapturedAt() == null || page.getCapturedAt().isAfter(target.getCapturedAt()))) {
            target.setCapturedAt(page.getCapturedAt());
        }
    }

    static boolean hasCompleteTitles(NoonProductDetail detail) {
        return detail != null
                && StringUtils.hasText(detail.getTitleEn())
                && StringUtils.hasText(detail.getTitleAr());
    }

    static String missingLocale(NoonProductDetail detail, String siteCode) {
        String language = detail == null || !StringUtils.hasText(detail.getTitleEn()) ? "en" : "ar";
        return language + "-" + siteCode;
    }

    private static int rank(NoonSearchResult result) {
        Integer value = result == null ? null : result.getRankPosition();
        return value == null || value < 1 ? Integer.MAX_VALUE : value;
    }

    private static int position(NoonSearchResult result) {
        Integer value = result == null ? null : result.getPosition();
        return value == null || value < 1 ? Integer.MAX_VALUE : value;
    }

    private static String first(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
