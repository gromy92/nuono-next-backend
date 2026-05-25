package com.nuono.next.logisticsquote;

public class LogisticsQuoteComparisonQuery {

    private final String country;
    private final String transportMode;
    private final String serviceScope;
    private final String cargoCategoryName;
    private final String billingUnit;

    public LogisticsQuoteComparisonQuery(
            String country,
            String transportMode,
            String serviceScope,
            String cargoCategoryName,
            String billingUnit
    ) {
        this.country = country;
        this.transportMode = transportMode;
        this.serviceScope = serviceScope;
        this.cargoCategoryName = cargoCategoryName;
        this.billingUnit = billingUnit;
    }

    public String getBillingUnit() {
        return billingUnit;
    }

    public boolean matches(LogisticsServiceLineFact serviceLine, LogisticsCargoCategoryFact category) {
        return equals(country, serviceLine.getCountry())
                && equals(transportMode, serviceLine.getTransportMode())
                && equals(serviceScope, serviceLine.getServiceScope())
                && equals(cargoCategoryName, category.getCategoryName())
                && category.isComparable();
    }

    private boolean equals(String expected, String actual) {
        return expected == null || expected.equals(actual);
    }
}
