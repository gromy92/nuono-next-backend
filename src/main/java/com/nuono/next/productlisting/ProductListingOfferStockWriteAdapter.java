package com.nuono.next.productlisting;

import com.nuono.next.noonpull.NoonPullGatewaySession;
import java.util.Map;

public interface ProductListingOfferStockWriteAdapter {

    ProductListingNoonWriteStepResult writeOfferStock(
            ProductListingOfferStockWriteRequest request,
            NoonPullGatewaySession session,
            ProductListingRealWriteProperties.Endpoints endpoints,
            Map<String, String> headers
    );
}
