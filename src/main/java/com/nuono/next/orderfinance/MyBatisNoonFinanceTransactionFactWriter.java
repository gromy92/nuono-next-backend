package com.nuono.next.orderfinance;

import com.nuono.next.infrastructure.mapper.NoonFinanceTransactionMapper;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonFinanceTransactionFactWriter implements NoonFinanceTransactionFactWriter {
    private final NoonFinanceTransactionMapper mapper;

    public MyBatisNoonFinanceTransactionFactWriter(NoonFinanceTransactionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(NoonFinanceTransactionFact fact) {
        mapper.upsertFinanceTransactionFact(mapper.nextFinanceTransactionFactId(), fact);
    }
}
