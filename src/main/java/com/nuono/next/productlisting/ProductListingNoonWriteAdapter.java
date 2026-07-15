package com.nuono.next.productlisting;

import java.util.List;

public interface ProductListingNoonWriteAdapter {

    ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request);

    ProductListingNoonWriteResult continueAfterCreate(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode
    );

    default ProductListingNoonWriteStepResult resolveCreateReference(ProductListingNoonWriteRequest request) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("resolve_create_reference");
        step.setStatus("failed");
        step.setFailureCode("noon_create_reference_lookup_unavailable");
        step.setFailureMessage("Noon create reference lookup is unavailable.");
        return step;
    }

    ProductListingNoonWriteStepResult verifyReadBack(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode,
            List<String> expectedImageValues
    );
}
