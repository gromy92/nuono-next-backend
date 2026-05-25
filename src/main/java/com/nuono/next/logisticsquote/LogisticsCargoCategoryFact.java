package com.nuono.next.logisticsquote;

public class LogisticsCargoCategoryFact {

    private final String naturalKey;
    private final String forwarderCode;
    private final String serviceLineKey;
    private final String categoryCode;
    private final String categoryName;
    private final String sourceCategoryName;
    private final String productExamples;
    private final String keywords;
    private final String electricType;
    private final String sensitiveTags;
    private final String packingPolicy;
    private final boolean manualConfirmRequired;
    private final String status;
    private final LogisticsQuoteFactSourceLineage sourceLineage;

    public LogisticsCargoCategoryFact(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String categoryCode,
            String categoryName,
            String sourceCategoryName,
            String productExamples,
            String keywords,
            String electricType,
            String sensitiveTags,
            String packingPolicy,
            boolean manualConfirmRequired,
            String status,
            LogisticsQuoteFactSourceLineage sourceLineage
    ) {
        this.naturalKey = naturalKey;
        this.forwarderCode = forwarderCode;
        this.serviceLineKey = serviceLineKey;
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.sourceCategoryName = sourceCategoryName;
        this.productExamples = productExamples;
        this.keywords = keywords;
        this.electricType = electricType;
        this.sensitiveTags = sensitiveTags;
        this.packingPolicy = packingPolicy;
        this.manualConfirmRequired = manualConfirmRequired;
        this.status = status;
        this.sourceLineage = sourceLineage;
    }

    public String getNaturalKey() {
        return naturalKey;
    }

    public String getForwarderCode() {
        return forwarderCode;
    }

    public String getServiceLineKey() {
        return serviceLineKey;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getSourceCategoryName() {
        return sourceCategoryName;
    }

    public String getProductExamples() {
        return productExamples;
    }

    public String getKeywords() {
        return keywords;
    }

    public String getElectricType() {
        return electricType;
    }

    public String getSensitiveTags() {
        return sensitiveTags;
    }

    public String getPackingPolicy() {
        return packingPolicy;
    }

    public boolean isManualConfirmRequired() {
        return manualConfirmRequired;
    }

    public boolean isComparable() {
        return !manualConfirmRequired && LogisticsQuoteFactStatus.ACTIVE.value().equals(status);
    }

    public String getStatus() {
        return status;
    }

    public LogisticsQuoteFactSourceLineage getSourceLineage() {
        return sourceLineage;
    }
}
