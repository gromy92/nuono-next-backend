package com.nuono.next.competitoranalysis;

import org.springframework.stereotype.Component;

@Component
public class NoopCompetitorKeywordRefreshRunner implements CompetitorKeywordRefreshRunner {
    @Override
    public CompetitorKeywordRefreshOutcome refresh(CompetitorKeywordRefreshContext context) {
        return CompetitorKeywordRefreshOutcome.success(0);
    }
}
