package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonOrderFactWriter implements NoonOrderFactWriter {
    private final NoonOrderFactMapper mapper;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public MyBatisNoonOrderFactWriter(NoonOrderFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsertLine(NoonOrderLineFact fact) {
        ensureSchema();
        Long id = mapper.nextOrderLineFactId();
        mapper.upsertOrderLineFact(id, fact);
        mapper.markProductSiteOfferLogisticsHistoryByOrderLineFact(fact);
    }

    private void ensureSchema() {
        if (!schemaEnsured.compareAndSet(false, true)) {
            return;
        }
        mapper.ensureNoonOrderIdSequence();
        mapper.ensureOrderLineFactSequence();
        mapper.ensureNoonOrderLineFactTable();
    }
}
