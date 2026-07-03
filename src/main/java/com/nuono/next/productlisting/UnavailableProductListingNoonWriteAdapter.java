package com.nuono.next.productlisting;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnavailableProductListingNoonWriteAdapter implements ProductListingNoonWriteAdapter {

    @Override
    public ProductListingNoonWriteResult execute(ProductListingNoonWriteRequest request) {
        return ProductListingNoonWriteResult.failed(
                "configuration",
                "noon_write_adapter_unavailable",
                "Product listing Noon write adapter is not available.",
                List.of()
        );
    }

    @Override
    public ProductListingNoonWriteResult continueAfterCreate(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode
    ) {
        return ProductListingNoonWriteResult.failed(
                "configuration",
                "noon_write_adapter_unavailable",
                "Product listing Noon write adapter is not available.",
                List.of()
        );
    }

    @Override
    public ProductListingNoonWriteStepResult verifyReadBack(
            ProductListingNoonWriteRequest request,
            String skuParent,
            String pskuCode,
            List<String> expectedImageValues
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("verify_noon_readback");
        step.setStatus("failed");
        step.setExternalReference("skuParent=" + (skuParent == null ? "" : skuParent)
                + ";pskuCode=" + (pskuCode == null ? "" : pskuCode)
                + ";readBackAttempts=0");
        step.setFailureCode("noon_write_adapter_unavailable");
        step.setFailureMessage("Product listing Noon write adapter is not available.");
        return step;
    }
}
