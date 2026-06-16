package com.nuono.next.productselection;

import java.util.Locale;
import org.springframework.util.StringUtils;

public final class NoonImageUrlNormalizer {

    private NoonImageUrlNormalizer() {
    }

    public static String normalize(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }
        String value = imageUrl.trim();
        String suffix = "";
        int splitIndex = firstQueryOrHashIndex(value);
        if (splitIndex >= 0) {
            suffix = value.substring(splitIndex);
            value = value.substring(0, splitIndex);
        }

        value = replacePrefix(value, "https://f.nooncdn.com/pzsku/", "https://f.nooncdn.com/p/pzsku/");
        value = replacePrefix(value, "http://f.nooncdn.com/pzsku/", "http://f.nooncdn.com/p/pzsku/");

        if (isNoonPskuImage(value) && !hasImageExtension(value)) {
            value = value + ".jpg";
        }
        return value + suffix;
    }

    private static int firstQueryOrHashIndex(String value) {
        int queryIndex = value.indexOf('?');
        int hashIndex = value.indexOf('#');
        if (queryIndex < 0) {
            return hashIndex;
        }
        if (hashIndex < 0) {
            return queryIndex;
        }
        return Math.min(queryIndex, hashIndex);
    }

    private static String replacePrefix(String value, String oldPrefix, String newPrefix) {
        return value.startsWith(oldPrefix) ? newPrefix + value.substring(oldPrefix.length()) : value;
    }

    private static boolean isNoonPskuImage(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.startsWith("https://f.nooncdn.com/p/pzsku/")
                || lower.startsWith("http://f.nooncdn.com/p/pzsku/");
    }

    private static boolean hasImageExtension(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".webp")
                || lower.endsWith(".gif");
    }
}
