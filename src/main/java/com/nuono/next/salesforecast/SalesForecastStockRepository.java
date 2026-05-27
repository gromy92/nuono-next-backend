package com.nuono.next.salesforecast;

import java.util.List;

public interface SalesForecastStockRepository {

    List<SalesForecastStockSnapshot> listCurrentStock(SalesForecastQuery query);
}
