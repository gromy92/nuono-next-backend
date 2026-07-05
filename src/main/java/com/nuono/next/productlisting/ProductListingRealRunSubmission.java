package com.nuono.next.productlisting;

public class ProductListingRealRunSubmission {

    private final ProductListingDraftView draft;
    private final ProductListingTaskView dryRun;
    private final ProductListingTaskView realRun;

    public ProductListingRealRunSubmission(
            ProductListingDraftView draft,
            ProductListingTaskView dryRun,
            ProductListingTaskView realRun
    ) {
        this.draft = draft;
        this.dryRun = dryRun;
        this.realRun = realRun;
    }

    public ProductListingDraftView getDraft() {
        return draft;
    }

    public ProductListingTaskView getDryRun() {
        return dryRun;
    }

    public ProductListingTaskView getRealRun() {
        return realRun;
    }
}
