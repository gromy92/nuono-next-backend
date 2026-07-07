package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;

public class PostSaleProfitFxRateView {
    private final Long id;
    private final String siteCode;
    private final String currency;
    private final BigDecimal rateToCny;
    private final String effectiveFrom;
    private final String effectiveTo;
    private final String sourceLabel;

    public PostSaleProfitFxRateView(
            Long id,
            String siteCode,
            String currency,
            BigDecimal rateToCny,
            String effectiveFrom,
            String effectiveTo,
            String sourceLabel
    ) {
        this.id = id;
        this.siteCode = siteCode;
        this.currency = currency;
        this.rateToCny = rateToCny;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.sourceLabel = sourceLabel;
    }

    public Long getId() { return id; }
    public String getSiteCode() { return siteCode; }
    public String getCurrency() { return currency; }
    public BigDecimal getRateToCny() { return rateToCny; }
    public String getEffectiveFrom() { return effectiveFrom; }
    public String getEffectiveTo() { return effectiveTo; }
    public String getSourceLabel() { return sourceLabel; }
}
