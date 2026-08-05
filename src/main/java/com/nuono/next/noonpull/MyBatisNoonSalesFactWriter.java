package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Override
    @Transactional
    public void upsertAll(List<NoonSalesDailyFact> facts) {
        NoonSalesFactWriter.super.upsertAll(facts);
    }

}
