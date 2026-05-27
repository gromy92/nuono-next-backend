package com.nuono.next.sales;

import java.time.LocalDate;

public class ProductLifecycleListingDateResolution {

    private final LocalDate listingDate;
    private final String source;
    private final String confidence;
    private final boolean historicalOldProduct;
    private final boolean eligibleForNewInitialization;
    private final String evidenceJson;

    public ProductLifecycleListingDateResolution(
            LocalDate listingDate,
            String source,
            String confidence,
            boolean historicalOldProduct,
            boolean eligibleForNewInitialization,
            String evidenceJson
    ) {
        this.listingDate = listingDate;
        this.source = source;
        this.confidence = confidence;
        this.historicalOldProduct = historicalOldProduct;
        this.eligibleForNewInitialization = eligibleForNewInitialization;
        this.evidenceJson = evidenceJson;
    }

    public LocalDate getListingDate() {
        return listingDate;
    }

    public String getSource() {
        return source;
    }

    public String getConfidence() {
        return confidence;
    }

    public boolean isHistoricalOldProduct() {
        return historicalOldProduct;
    }

    public boolean isEligibleForNewInitialization() {
        return eligibleForNewInitialization;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}
