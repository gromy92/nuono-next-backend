package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.NoonOrderFactMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    }

    @Override
    @Transactional
    public void upsertLines(List<NoonOrderLineFact> facts) {
        NoonOrderFactWriter.super.upsertLines(facts);
    }
}
