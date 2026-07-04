package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductKeywordMatcherTest {
    private final ProductKeywordMatcher matcher = new ProductKeywordMatcher(new ProductKeywordNormalizer());

    @Test
    void latinAndArabicUseTokenBoundaries() {
        assertThat(matcher.matches("milk", "milk bottle for kids")).isTrue();
        assertThat(matcher.matches("ilk", "milk bottle for kids")).isFalse();
        assertThat(matcher.matches("دبدوب", "لعبة دبدوب ناعم")).isTrue();
    }

    @Test
    void cjkRequiresAtLeastTwoCharactersForSubstringMatch() {
        assertThat(matcher.matches("奶瓶", "婴儿奶瓶套装")).isTrue();
        assertThat(matcher.matches("奶", "婴儿奶瓶套装")).isFalse();
    }
}
