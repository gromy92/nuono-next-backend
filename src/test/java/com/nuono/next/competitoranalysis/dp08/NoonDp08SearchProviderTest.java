package com.nuono.next.competitoranalysis.dp08;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonDp08SearchProviderTest {

    @Test
    void doesNotTurnAnIncompleteFirstListPageIntoBusinessNotFound() {
        NoonSearchPage incomplete = new NoonSearchPage();
        incomplete.setProviderPage(1);
        incomplete.setProviderLimit(20);
        incomplete.setTotalHits(25);
        incomplete.setTotalPages(2);
        incomplete.setResults(List.of());
        NoonDp08SearchProvider provider = provider(incomplete);

        ProviderOutcome<NoonSearchPage> outcome = provider.searchExact(target(), "en-SA");

        assertThat(outcome.getType()).isEqualTo(ProviderOutcomeType.CONTRACT_ERROR);
        assertThat(outcome.getSanitizedCode()).isEqualTo("DP08_LIST_NOT_FOUND_UNPROVEN");
    }

    @Test
    void acceptsEmptyListOnlyWhenTheResponseProvesTheWholeExactResultSetIsEmpty() {
        NoonSearchPage completeEmpty = new NoonSearchPage();
        completeEmpty.setProviderPage(1);
        completeEmpty.setProviderLimit(20);
        completeEmpty.setTotalHits(0);
        completeEmpty.setTotalPages(0);
        completeEmpty.setResults(List.of());
        NoonDp08SearchProvider provider = provider(completeEmpty);

        ProviderOutcome<NoonSearchPage> outcome = provider.searchExact(target(), "en-SA");

        assertThat(outcome.getType()).isEqualTo(ProviderOutcomeType.SUCCESS);
        assertThat(outcome.getValue()).isSameAs(completeEmpty);
    }

    private NoonDp08SearchProvider provider(NoonSearchPage page) {
        NoonFrontendSearchAdapter adapter = request -> page;
        return new NoonDp08SearchProvider(adapter);
    }

    private Dp08ListTarget target() {
        return new Dp08ListTarget(
                307L,
                10L,
                "STORE",
                "SA",
                "Z1234567890",
                "scope",
                LocalDate.of(2026, 8, 2),
                true,
                List.of(new Dp08ListTarget.Reference(20L, null))
        );
    }
}
