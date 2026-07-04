package com.nuono.next.productkeyword;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductKeywordViewsTest {

    @Test
    void keywordViewSeparatesTitleTypesFromCompetitorAndAdsEvidence() {
        ProductKeywordRecord record = new ProductKeywordRecord();
        record.setId(300001L);
        record.setOwnerUserId(99L);
        record.setStoreCode("STR108065-NAE");
        record.setSiteCode("AE");
        record.setPartnerSku("NUONO-DECOR-81403829");
        record.setKeyword("artificial flowers");
        record.setKeywordNorm("artificial flowers");
        record.setLocale("en");
        record.setStatus("ACTIVE");
        record.setIntentTagsJson("[\"CORE\",\"TRENDING\",\"TITLE_TARGET\",\"COMPETITOR_TRACK\",\"ADS_QUERY\",\"NEGATIVE_CANDIDATE\",\"CATEGORY\",\"BRAND\",\"LONG_TAIL\"]");

        ProductKeywordViews.KeywordItemView view = ProductKeywordViews.keyword(record);

        assertThat(view.getTitleTypes()).containsExactly("CORE", "TRENDING");
        assertThat(view.getTitleUsageStates()).containsExactly("TITLE_TARGET");
        assertThat(view.getCompetitorEvidence()).isTrue();
        assertThat(view.getAdsEvidence()).isTrue();
        assertThat(view.getNegativeCandidate()).isTrue();
    }
}
