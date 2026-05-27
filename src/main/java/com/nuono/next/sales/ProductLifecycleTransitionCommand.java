package com.nuono.next.sales;

import java.time.LocalDate;

public class ProductLifecycleTransitionCommand {

    private final ProductLifecycleStateQuery query;
    private final ProductLifecycleResult candidate;
    private final LocalDate analysisDate;
    private final LocalDate listingDate;
    private final String listingDateSource;
    private final Long jobId;
    private final boolean stockoutDistorted;

    public ProductLifecycleTransitionCommand(
            ProductLifecycleStateQuery query,
            ProductLifecycleResult candidate,
            LocalDate analysisDate,
            LocalDate listingDate,
            String listingDateSource,
            Long jobId,
            boolean stockoutDistorted
    ) {
        this.query = query;
        this.candidate = candidate;
        this.analysisDate = analysisDate;
        this.listingDate = listingDate;
        this.listingDateSource = listingDateSource;
        this.jobId = jobId;
        this.stockoutDistorted = stockoutDistorted;
    }

    public ProductLifecycleStateQuery getQuery() {
        return query;
    }

    public ProductLifecycleResult getCandidate() {
        return candidate;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public LocalDate getListingDate() {
        return listingDate;
    }

    public String getListingDateSource() {
        return listingDateSource;
    }

    public Long getJobId() {
        return jobId;
    }

    public boolean isStockoutDistorted() {
        return stockoutDistorted;
    }
}
