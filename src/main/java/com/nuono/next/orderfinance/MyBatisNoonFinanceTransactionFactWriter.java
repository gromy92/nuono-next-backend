package com.nuono.next.orderfinance;

import com.nuono.next.infrastructure.mapper.NoonFinanceTransactionMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonFinanceTransactionFactWriter implements NoonFinanceTransactionFactWriter {
    private final NoonFinanceTransactionMapper mapper;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public MyBatisNoonFinanceTransactionFactWriter(NoonFinanceTransactionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsert(NoonFinanceTransactionFact fact) {
        ensureSchema();
        mapper.upsertFinanceTransactionFact(mapper.nextFinanceTransactionFactId(), fact);
    }

    private void ensureSchema() {
        if (schemaEnsured.get()) {
            return;
        }
        synchronized (this) {
            if (schemaEnsured.get()) {
                return;
            }
            mapper.ensureIdSequenceTable();
            mapper.ensureFactSequence();
            mapper.ensureFactTable();
            mapper.ensureFactNaturalUniqueKey();
            schemaEnsured.set(true);
        }
    }
}
