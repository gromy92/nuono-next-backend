package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import org.springframework.stereotype.Service;

@Service
public class MyBatisNoonOrderFactWriter implements NoonOrderFactWriter {
    private final NoonOrderFactMapper mapper;

    public MyBatisNoonOrderFactWriter(NoonOrderFactMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void upsertLine(NoonOrderLineFact fact) {
        Long id = mapper.nextOrderLineFactId();
        mapper.upsertOrderLineFact(id, fact);
        mapper.markProductSiteOfferLogisticsHistoryByOrderLineFact(fact);
    }
}
