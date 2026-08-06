package com.nuono.next.competitoranalysis.dp08;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.competitoranalysis.noon.NoonFrontendSearchAdapter;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderOutcomeType;
import com.nuono.next.noon.NoonRequestPacingException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoonDp08SearchProviderPacingTest {

    @Test
    void carriesLocalPacingHintIntoRuntimeOutcome() {
        NoonFrontendSearchAdapter adapter = request -> {
            throw new NoonRequestPacingException(Duration.ofMillis(710));
        };
        NoonDp08SearchProvider provider = new NoonDp08SearchProvider(adapter);
        Dp08KeywordScope scope = new Dp08KeywordScope(
                307L,
                10L,
                20L,
                30L,
                "STR108065-NSA",
                "SA",
                "sticky notes",
                "en-SA",
                "scope",
                List.of(new Dp08TrackedProduct(
                        Dp08TrackedProduct.SubjectType.SELF, null, "N700001"
                ))
        );

        ProviderOutcome<NoonSearchPage> outcome = provider.fetchRankPage(scope, 1);

        assertEquals(ProviderOutcomeType.TRANSIENT, outcome.getType());
        assertEquals("DP08_LOCAL_PACING", outcome.getSanitizedCode());
        assertEquals(Duration.ofMillis(710), outcome.getRetryAfter());
    }
}
