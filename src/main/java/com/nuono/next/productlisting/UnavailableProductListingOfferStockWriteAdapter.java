package com.nuono.next.productlisting;

import com.nuono.next.noonpull.NoonPullGatewaySession;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class UnavailableProductListingOfferStockWriteAdapter implements ProductListingOfferStockWriteAdapter {

    @Override
    public ProductListingNoonWriteStepResult writeOfferStock(
            ProductListingOfferStockWriteRequest request,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("upsert_offer");
        step.setStatus("skipped");
        step.setFailureCode("noon_offer_stock_adapter_unavailable");
        step.setFailureMessage("Offer, sale price, warehouse, stock, and active-state fields were not written to Noon; "
                + "no ProductListingOfferStockWriteAdapter implementation is configured.");
        return step;
    }
}
