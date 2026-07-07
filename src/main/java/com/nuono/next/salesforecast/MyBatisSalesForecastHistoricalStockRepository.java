package com.nuono.next.salesforecast;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesForecastHistoricalStockRepository implements SalesForecastHistoricalStockRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesForecastHistoricalStockRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SalesForecastHistoricalStockSnapshot> listHistoricalStock(
            SalesForecastQuery query,
            LocalDate dateFrom,
            LocalDate dateTo,
            List<String> partnerSkus
    ) {
        if (query == null || dateFrom == null || dateTo == null || partnerSkus == null || partnerSkus.isEmpty()) {
            return List.of();
        }
        return mapper.selectSalesForecastHistoricalStock(query, dateFrom, dateTo, partnerSkus);
    }
}
