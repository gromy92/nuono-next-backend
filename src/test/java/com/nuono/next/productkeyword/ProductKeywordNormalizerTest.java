package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductKeywordNormalizerTest {
    private final ProductKeywordNormalizer normalizer = new ProductKeywordNormalizer();

    @Test
    void normalizesWhitespaceLatinCasePunctuationAndArabicMarks() {
        assertThat(normalizer.normalize("  Qili--Milk   Bottle  ")).isEqualTo("qili milk bottle");
        assertThat(normalizer.normalize("دَبــدوب")).isEqualTo("دبدوب");
    }

    @Test
    void blankKeywordNormalizesToEmptyString() {
        assertThat(normalizer.normalize("   ")).isEqualTo("");
    }
}
