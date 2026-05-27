package com.nuono.next.sales;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UnavailableLegacySalesBackfillRowProvider implements LegacySalesBackfillRowProvider {

    @Override
    public List<LegacySalesBackfillRow> fetch(LegacySalesBackfillCommand command) {
        throw new IllegalStateException("Legacy sales backfill row provider is not configured.");
    }
}
