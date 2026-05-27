package com.nuono.next.salesforecast;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesForecastStockRepository implements SalesForecastStockRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesForecastStockRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SalesForecastStockSnapshot> listCurrentStock(SalesForecastQuery query) {
        return mapper.selectSalesForecastCurrentStock(query);
    }
}
