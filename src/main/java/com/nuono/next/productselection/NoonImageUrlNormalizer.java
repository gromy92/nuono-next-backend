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
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        String suffix = "";
        int splitIndex = firstQueryOrHashIndex(value);
        if (splitIndex >= 0) {
            suffix = value.substring(splitIndex);
            value = value.substring(0, splitIndex);
        }

        value = normalizeNoonCdnPath(value);

        if (isNoonProductImage(value) && !hasImageExtension(value)) {
            value = value + ".jpg";
        }
        return encodeNoonPathSeparators(value) + suffix;
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

    private static String normalizeNoonCdnPath(String value) {
        String normalized = normalizeNoonCdnPath(value, "https://f.nooncdn.com/");
        if (!normalized.equals(value)) {
            return normalized;
        }
        normalized = normalizeNoonCdnPath(value, "http://f.nooncdn.com/");
        if (!normalized.equals(value)) {
            return normalized;
        }
        return isNoonProductImagePath(value) ? "https://f.nooncdn.com/p/" + stripLeadingSlash(value) : value;
    }

    private static String normalizeNoonCdnPath(String value, String cdnPrefix) {
        if (!value.toLowerCase(Locale.ROOT).startsWith(cdnPrefix)) {
            return value;
        }
        String path = value.substring(cdnPrefix.length());
        String strippedPath = stripLeadingSlash(path);
        if (strippedPath.toLowerCase(Locale.ROOT).startsWith("p/")) {
            String nestedProductPath = strippedPath.substring("p/".length());
            return isNoonProductImagePath(nestedProductPath) ? cdnPrefix + "p/" + nestedProductPath : value;
        }
        return isNoonProductImagePath(strippedPath) ? cdnPrefix + "p/" + strippedPath : value;
    }

    private static String stripLeadingSlash(String value) {
        return value.replaceFirst("^/+", "");
    }

    private static boolean isNoonProductImage(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://f.nooncdn.com/p/")) {
            return isNoonProductImagePath(value.substring("https://f.nooncdn.com/p/".length()));
        }
        if (lower.startsWith("http://f.nooncdn.com/p/")) {
            return isNoonProductImagePath(value.substring("http://f.nooncdn.com/p/".length()));
        }
        return false;
    }

    private static boolean isNoonProductImagePath(String value) {
        String lower = stripLeadingSlash(value).toLowerCase(Locale.ROOT);
        return lower.startsWith("pzsku/")
                || lower.startsWith("pnsku/")
                || lower.contains("|pzsku/")
                || lower.contains("|pnsku/")
                || lower.contains("%7cpzsku/")
                || lower.contains("%7cpnsku/");
    }

    private static String encodeNoonPathSeparators(String value) {
        return isNoonProductImage(value) ? value.replace("|", "%7C") : value;
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
