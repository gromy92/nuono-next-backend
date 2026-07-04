package com.nuono.next.productkeyword;

import java.util.regex.Pattern;

public class ProductKeywordMatcher {
    private static final Pattern CJK_PATTERN = Pattern.compile("[\\p{IsHan}]");

    private final ProductKeywordNormalizer normalizer;

    public ProductKeywordMatcher(ProductKeywordNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public boolean matches(String keyword, String title) {
        String keywordNorm = normalizer.normalize(keyword);
        String titleNorm = normalizer.normalize(title);
        if (keywordNorm.isEmpty() || titleNorm.isEmpty()) {
            return false;
        }
        if (containsCjk(keywordNorm)) {
            return countCjk(keywordNorm) >= 2 && titleNorm.contains(keywordNorm);
        }
        return hasTokenBoundaryMatch(keywordNorm, titleNorm);
    }

    private boolean hasTokenBoundaryMatch(String keywordNorm, String titleNorm) {
        Pattern pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(keywordNorm) + "(?![\\p{L}\\p{N}])"
        );
        return pattern.matcher(titleNorm).find();
    }

    private boolean containsCjk(String value) {
        return CJK_PATTERN.matcher(value).find();
    }

    private int countCjk(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); ) {
            int codePoint = value.codePointAt(index);
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                count++;
            }
            index += Character.charCount(codePoint);
        }
        return count;
    }
}
