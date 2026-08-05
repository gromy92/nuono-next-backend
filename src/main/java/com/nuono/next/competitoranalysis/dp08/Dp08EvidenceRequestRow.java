package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDate;

/** One immutable sealed reference crossed with one bounded task fact date. */
public final class Dp08EvidenceRequestRow {
    private final String scopeKey;
    private final LocalDate factDate;
    private final long watchProductId;
    private final Long competitorProductId;
    private final String noonProductCode;

    public Dp08EvidenceRequestRow(
            String scopeKey, LocalDate factDate, long watchProductId,
            Long competitorProductId, String noonProductCode
    ) {
        this.scopeKey = scopeKey;
        this.factDate = factDate;
        this.watchProductId = watchProductId;
        this.competitorProductId = competitorProductId;
        this.noonProductCode = noonProductCode;
    }

    public String getScopeKey() { return scopeKey; }
    public LocalDate getFactDate() { return factDate; }
    public long getWatchProductId() { return watchProductId; }
    public Long getCompetitorProductId() { return competitorProductId; }
    public String getNoonProductCode() { return noonProductCode; }
}
