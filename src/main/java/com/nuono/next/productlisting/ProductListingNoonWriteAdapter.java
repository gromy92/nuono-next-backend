package com.nuono.next.productlisting;

import java.util.List;

public interface ProductListingNoonWriteAdapter {

    ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request);

    ProductListingNoonWriteResult continueAfterCreate(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode
    );

    ProductListingNoonWriteStepResult verifyReadBack(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode,
            List<String> expectedImageValues
    );
}
