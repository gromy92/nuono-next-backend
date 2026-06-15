package com.nuono.next.competitoranalysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompetitorWatchProductListItemViewTest {

    @Test
    void parsesActiveKeywordsWithMonitoredCounts() {
        CompetitorWatchProductListRow row = new CompetitorWatchProductListRow();
        row.setActiveKeywordSummary("Qili\t3||HB pencils\t12");

        CompetitorWatchProductListItemView view = CompetitorWatchProductListItemView.fromRow(row);

        assertThat(view.getActiveKeywords()).containsExactly("Qili", "HB pencils");
        assertThat(view.getActiveKeywordStats())
                .extracting(CompetitorKeywordCountView::getKeyword, CompetitorKeywordCountView::getMonitoredCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Qili", 3),
                        org.assertj.core.groups.Tuple.tuple("HB pencils", 12)
                );
    }

    @Test
    void keepsCompatibilityWithLegacyKeywordOnlySummary() {
        CompetitorWatchProductListRow row = new CompetitorWatchProductListRow();
        row.setActiveKeywordSummary("Qili||HB pencils");

        CompetitorWatchProductListItemView view = CompetitorWatchProductListItemView.fromRow(row);

        assertThat(view.getActiveKeywords()).containsExactly("Qili", "HB pencils");
        assertThat(view.getActiveKeywordStats())
                .extracting(CompetitorKeywordCountView::getKeyword, CompetitorKeywordCountView::getMonitoredCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Qili", 0),
                        org.assertj.core.groups.Tuple.tuple("HB pencils", 0)
                );
    }
}
