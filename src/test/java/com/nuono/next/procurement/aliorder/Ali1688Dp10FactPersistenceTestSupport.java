package com.nuono.next.procurement.aliorder;

import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentWriter;

/** Builds the package-private production fact writer for exact-path MySQL tests. */
public final class Ali1688Dp10FactPersistenceTestSupport {
    private Ali1688Dp10FactPersistenceTestSupport() {
    }

    public static Ali1688Dp10FactSegmentWriter productionWriter(
            Ali1688HistoricalOrderMapper mapper,
            Ali1688Dp10FactLookupMapper factLookupMapper
    ) {
        return new Ali1688HistoricalOrderFactPersistence(mapper, factLookupMapper);
    }
}
