package com.nuono.next.competitoranalysis;

import org.springframework.stereotype.Component;

@Component
public class NoopCompetitorKeywordRefreshRunner implements CompetitorKeywordRefreshRunner {
    @Override
    public CompetitorKeywordRefreshOutcome refresh(CompetitorKeywordRow keyword) {
        return CompetitorKeywordRefreshOutcome.success(0);
    }
}
