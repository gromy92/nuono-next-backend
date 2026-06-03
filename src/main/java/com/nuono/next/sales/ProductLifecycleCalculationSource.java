package com.nuono.next.sales;

import java.time.LocalDate;
import java.util.List;

public interface ProductLifecycleCalculationSource {

    default List<ProductLifecycleCalculationScope> listScheduledScopes(LocalDate anchorDate) {
        return List.of();
    }

    default List<LocalDate> listHistoricalAnchorDates(ProductLifecycleCalculationScope scope) {
        return List.of(scope.getAnchorDate());
    }

    default List<LocalDate> listHistoricalAnchorDates(
            ProductLifecycleCalculationScope scope,
            ProductLifecycleStateQuery query
    ) {
        return listHistoricalAnchorDates(scope);
    }

    List<ProductLifecycleStateQuery> listProductScopes(ProductLifecycleCalculationScope scope);

    ProductLifecycleListingSignals loadListingSignals(ProductLifecycleStateQuery query, LocalDate analysisDate);

    List<DailySalesFact> loadFacts(ProductLifecycleStateQuery query, LocalDate from, LocalDate to);

    List<SalesActivityWindowRecord> loadActivityWindows(ProductLifecycleStateQuery query, LocalDate from, LocalDate to);

    boolean isStockoutDistorted(ProductLifecycleStateQuery query, ProductLifecycleFeatureSnapshot features);
}
