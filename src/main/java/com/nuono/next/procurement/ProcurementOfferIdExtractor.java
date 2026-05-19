package com.nuono.next.procurement;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

final class ProcurementOfferIdExtractor {

    private static final Pattern OFFER_ID_PATH_PATTERN = Pattern.compile("/offer/(\\d+)\\.html");
    private static final Pattern OFFER_ID_QUERY_PATTERN = Pattern.compile("(?:offerId|offer_id|id)=(\\d+)");

    private ProcurementOfferIdExtractor() {
    }

    static String extract(String candidateUrl) {
        if (!StringUtils.hasText(candidateUrl)) {
            return null;
        }
        Matcher pathMatcher = OFFER_ID_PATH_PATTERN.matcher(candidateUrl);
        if (pathMatcher.find()) {
            return pathMatcher.group(1);
        }
        Matcher queryMatcher = OFFER_ID_QUERY_PATTERN.matcher(candidateUrl);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        return null;
    }
}
