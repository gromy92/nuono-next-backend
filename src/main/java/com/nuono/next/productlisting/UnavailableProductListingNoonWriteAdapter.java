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
                "Product listing Noon write adapter is not configured.",
                List.of()
        );
    }
}
