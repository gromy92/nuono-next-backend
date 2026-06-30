package com.nuono.next.product;

import com.nuono.next.productselection.NoonImageUrlNormalizer;
import org.springframework.util.StringUtils;

public final class ProductImageUrlSupport {

    private ProductImageUrlSupport() {
    }

    public static String normalize(String imageUrl) {
        return NoonImageUrlNormalizer.normalize(imageUrl);
    }

    public static String firstNonBlankNormalized(String... imageUrls) {
        if (imageUrls == null) {
            return null;
        }
        for (String imageUrl : imageUrls) {
            if (StringUtils.hasText(imageUrl)) {
                return normalize(imageUrl);
            }
        }
        return null;
    }
}
