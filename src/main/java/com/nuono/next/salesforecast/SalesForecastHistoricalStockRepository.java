package com.nuono.next.salesforecast;

import java.time.LocalDate;
import java.util.List;

public interface SalesForecastHistoricalStockRepository {

    List<SalesForecastHistoricalStockSnapshot> listHistoricalStock(
            SalesForecastQuery query,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<String> partnerSkus
    );
}
