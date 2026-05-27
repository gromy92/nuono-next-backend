package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnavailableProductLifecycleCalculationSource implements ProductLifecycleCalculationSource {

    @Override
    public List<ProductLifecycleStateQuery> listProductScopes(ProductLifecycleCalculationScope scope) {
        return List.of();
    }

    @Override
    public ProductLifecycleListingSignals loadListingSignals(ProductLifecycleStateQuery query, LocalDate analysisDate) {
        return new ProductLifecycleListingSignals(query, null, null, null, null, analysisDate, 0, 0, 0, 0);
    }

    @Override
    public List<DailySalesFact> loadFacts(ProductLifecycleStateQuery query, LocalDate from, LocalDate to) {
        return List.of();
    }

    @Override
    public List<SalesActivityWindowRecord> loadActivityWindows(
            ProductLifecycleStateQuery query,
            LocalDate from,
            LocalDate to
    ) {
        return List.of();
    }

    @Override
    public boolean isStockoutDistorted(ProductLifecycleStateQuery query, ProductLifecycleFeatureSnapshot features) {
        return false;
    }
}
