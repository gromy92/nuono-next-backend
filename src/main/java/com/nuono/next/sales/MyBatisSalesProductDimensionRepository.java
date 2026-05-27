package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesProductDimensionRepository implements SalesProductDimensionRepository {

    private final SalesDataMapper mapper;

    public MyBatisSalesProductDimensionRepository(SalesDataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<SalesProductDimensionSnapshot> list(SalesFactQuery query) {
        return mapper.selectSalesProductDimensions(query);
    }
}
