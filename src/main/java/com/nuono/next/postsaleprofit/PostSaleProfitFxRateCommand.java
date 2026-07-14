package com.nuono.next.postsaleprofit;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PostSaleProfitFxRateCommand {
    private final Long ownerUserId;
    private final String siteCode;
    private final String currency;
    private final BigDecimal rateToCny;
    private final LocalDate effectiveFrom;
    private final LocalDate effectiveTo;
    private final String sourceLabel;

    public PostSaleProfitFxRateCommand(
            Long ownerUserId,
            String siteCode,
            String currency,
            BigDecimal rateToCny,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String sourceLabel
    ) {
        this.ownerUserId = ownerUserId;
        this.siteCode = siteCode;
        this.currency = currency;
        this.rateToCny = rateToCny;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.sourceLabel = sourceLabel;
    }

    public Long getOwnerUserId() { return ownerUserId; }
    public String getSiteCode() { return siteCode; }
    public String getCurrency() { return currency; }
    public BigDecimal getRateToCny() { return rateToCny; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getSourceLabel() { return sourceLabel; }
}
