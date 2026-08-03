package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonSalesFactWriter implements NoonSalesFactWriter {
    private final NoonSalesFactMapper mapper;

    public MyBatisNoonSalesFactWriter(NoonSalesFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(NoonSalesDailyFact fact) {
        Long id = mapper.nextDailySalesFactId();
        mapper.upsertDailySalesFact(id, fact);
    }

}
