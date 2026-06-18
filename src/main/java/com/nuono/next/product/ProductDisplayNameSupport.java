package com.nuono.next.product;

import org.springframework.util.StringUtils;

public final class ProductDisplayNameSupport {

    private ProductDisplayNameSupport() {
    }

    public static String localChineseName(
            String titleCnCache,
            String draftTitleCn,
            String draftTitleZh,
            String baselineTitleCn,
            String baselineTitleZh
    ) {
        return firstNonBlank(titleCnCache, draftTitleCn, draftTitleZh, baselineTitleCn, baselineTitleZh);
    }

    public static String displayTitle(String titleCn, String titleEn, String productKey) {
        return firstNonBlank(titleCn, titleEn, productKey);
    }

    public static String firstNonBlank(String... values) {
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
}
