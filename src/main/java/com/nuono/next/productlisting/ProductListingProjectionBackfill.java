package com.nuono.next.productlisting;

public interface ProductListingProjectionBackfill {

    void backfillDraftListing(
            ProductListingDraftRecord record,
            ProductListingDraftCommand draft
    );

    boolean backfillSuccessfulListing(
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
            public boolean backfillSuccessfulListing(
                    ProductListingTaskRecord task,
                    ProductListingDraftCommand draft,
                    ProductListingNoonWriteResult result
            ) {
                return false;
            }
        };
    }
}
