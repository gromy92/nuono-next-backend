package com.nuono.next.productlisting;

public interface ProductListingProjectionBackfill {

    void backfillDraftListing(
            ProductListingDraftRecord record,
            ProductListingDraftCommand draft
    );

    void backfillSuccessfulListing(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            ProductListingNoonWriteResult result
    );

    static ProductListingProjectionBackfill noop() {
        return new ProductListingProjectionBackfill() {
            @Override
            public void backfillDraftListing(
                    ProductListingDraftRecord record,
                    ProductListingDraftCommand draft
            ) {
            }

            @Override
            public void backfillSuccessfulListing(
                    ProductListingTaskRecord task,
                    ProductListingDraftCommand draft,
                    ProductListingNoonWriteResult result
            ) {
            }
        };
    }
}
