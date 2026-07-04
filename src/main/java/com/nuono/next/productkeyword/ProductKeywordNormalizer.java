package com.nuono.next.productkeyword;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.util.StringUtils;

public class ProductKeywordNormalizer {

    public String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .replace("\u0640", "")
                .replaceAll("[\\u064B-\\u065F\\u0670]", "")
                .replaceAll("[\\p{Punct}\\u2010-\\u2015]+", " ")
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
