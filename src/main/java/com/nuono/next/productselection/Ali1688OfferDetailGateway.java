package com.nuono.next.productselection;

import java.util.List;

@FunctionalInterface
public interface Ali1688OfferDetailGateway {

    Ali1688OfferDetailCompletionResult enrich(
            List<Ali1688PluginSubmissionNormalizer.NormalizedCandidate> candidates
    );

    static Ali1688OfferDetailGateway disabled() {
        return candidates -> Ali1688OfferDetailCompletionResult.notAttempted(
                "Known-offer detail enrichment is disabled."
        );
    }
}
