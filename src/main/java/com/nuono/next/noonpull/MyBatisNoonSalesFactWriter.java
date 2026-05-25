package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonSalesFactMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonSalesFactWriter implements NoonSalesFactWriter {
    private final NoonSalesFactMapper mapper;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public MyBatisNoonSalesFactWriter(NoonSalesFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(NoonSalesDailyFact fact) {
        ensureSchema();
        Long id = mapper.nextDailySalesFactId();
        mapper.upsertDailySalesFact(id, fact);
    }

    private void ensureSchema() {
        if (!schemaEnsured.compareAndSet(false, true)) {
            return;
        }
        mapper.ensureSalesDataIdSequence();
        mapper.ensureDailySalesFactSequence();
        mapper.ensureDailySalesFactTable();
    }
}
