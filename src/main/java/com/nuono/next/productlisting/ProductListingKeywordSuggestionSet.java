package com.nuono.next.productlisting;

import com.nuono.next.productkeyword.ProductKeywordNormalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

final class ProductListingKeywordSuggestionSet {
    private static final int MAX_PER_LOCALE = 6;

    private ProductListingKeywordSuggestionSet() {
    }

    static Map<String, Item> from(
            ProductListingKeywordSuggestionCommand command,
            String siteCode,
            Long draftId,
            ProductKeywordNormalizer normalizer
    ) {
        Map<String, Item> values = new LinkedHashMap<>();
        add(values, command == null ? List.of() : command.getEnglish(), "en-" + siteCode, draftId, normalizer);
        add(values, command == null ? List.of() : command.getArabic(), "ar-" + siteCode, draftId, normalizer);
        return values;
    }

    private static void add(
            Map<String, Item> target,
            List<String> source,
            String locale,
            Long draftId,
            ProductKeywordNormalizer normalizer
    ) {
        int accepted = 0;
        for (String value : source == null ? List.<String>of() : source) {
            String keyword = StringUtils.hasText(value) ? value.trim() : null;
            String keywordNorm = normalizer.normalize(keyword);
            if (!StringUtils.hasText(keywordNorm)) {
                continue;
            }
            Item suggestion = new Item(keyword, keywordNorm, locale);
            if (target.putIfAbsent(refKey(draftId, locale, keywordNorm), suggestion) == null
                    && ++accepted >= MAX_PER_LOCALE) {
                return;
            }
        }
    }

    static String refKey(Long draftId, String locale, String keywordNorm) {
        return draftId + ":" + locale + ":" + keywordNorm;
    }

    static final class Item {
        final String keyword;
        final String keywordNorm;
        final String locale;

        Item(String keyword, String keywordNorm, String locale) {
            this.keyword = keyword;
            this.keywordNorm = keywordNorm;
            this.locale = locale;
        }
    }
}
