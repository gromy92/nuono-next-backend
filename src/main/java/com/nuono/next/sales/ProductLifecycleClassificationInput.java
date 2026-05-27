package com.nuono.next.sales;

import java.time.LocalDate;

public class ProductLifecycleClassificationInput {

    private final ProductLifecycleStateQuery query;
    private final LocalDate analysisDate;
    private final ProductLifecycleListingDateResolution listingDateResolution;
    private final ProductLifecycleFeatureSnapshot featureSnapshot;
    private final ProductLifecycleCorrectedFeatureSnapshot correctedFeatureSnapshot;

    public ProductLifecycleClassificationInput(
            ProductLifecycleStateQuery query,
            LocalDate analysisDate,
            ProductLifecycleListingDateResolution listingDateResolution,
            ProductLifecycleFeatureSnapshot featureSnapshot,
            ProductLifecycleCorrectedFeatureSnapshot correctedFeatureSnapshot
    ) {
        this.query = query;
        this.analysisDate = analysisDate;
        this.listingDateResolution = listingDateResolution;
        this.featureSnapshot = featureSnapshot;
        this.correctedFeatureSnapshot = correctedFeatureSnapshot;
    }

    public ProductLifecycleStateQuery getQuery() {
        return query;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public ProductLifecycleListingDateResolution getListingDateResolution() {
        return listingDateResolution;
    }

    public ProductLifecycleFeatureSnapshot getFeatureSnapshot() {
        return featureSnapshot;
    }

    public ProductLifecycleCorrectedFeatureSnapshot getCorrectedFeatureSnapshot() {
        return correctedFeatureSnapshot;
    }
}
