package com.nuono.next.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class ProductLifecycleCorrectedFeatureSnapshot {

    private static final BigDecimal ZERO_SALES = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

    private final ProductLifecycleFeatureSnapshot rawSnapshot;
    private final BigDecimal correctedRecent7Sales;
    private final BigDecimal correctedRecent15Sales;
    private final BigDecimal correctedPrevious15Sales;
    private final BigDecimal correctedRecent30Sales;
    private final BigDecimal correctedPrevious30Sales;
    private final BigDecimal correctedRecent60Sales;
    private final BigDecimal correctedHistoricalT38ToT8Sales;
    private final BigDecimal correctedSalesGrowth15;
    private final BigDecimal correctedSalesGrowth30;
    private final List<String> appliedFactorNames;
    private final String evidenceJson;

    public ProductLifecycleCorrectedFeatureSnapshot(
            ProductLifecycleFeatureSnapshot rawSnapshot,
            BigDecimal correctedRecent15Sales,
            BigDecimal correctedPrevious15Sales,
            BigDecimal correctedRecent30Sales,
            BigDecimal correctedRecent60Sales,
            BigDecimal correctedSalesGrowth15,
            List<String> appliedFactorNames,
            String evidenceJson
    ) {
        this(
                rawSnapshot,
                ZERO_SALES,
                correctedRecent15Sales,
                correctedPrevious15Sales,
                correctedRecent30Sales,
                ZERO_SALES,
                correctedRecent60Sales,
                ZERO_SALES,
                correctedSalesGrowth15,
                null,
                appliedFactorNames,
                evidenceJson
        );
    }

    public ProductLifecycleCorrectedFeatureSnapshot(
            ProductLifecycleFeatureSnapshot rawSnapshot,
            BigDecimal correctedRecent7Sales,
            BigDecimal correctedRecent15Sales,
            BigDecimal correctedPrevious15Sales,
            BigDecimal correctedRecent30Sales,
            BigDecimal correctedPrevious30Sales,
            BigDecimal correctedRecent60Sales,
            BigDecimal correctedHistoricalT38ToT8Sales,
            BigDecimal correctedSalesGrowth15,
            BigDecimal correctedSalesGrowth30,
            List<String> appliedFactorNames,
            String evidenceJson
    ) {
        this.rawSnapshot = rawSnapshot;
        this.correctedRecent7Sales = correctedRecent7Sales;
        this.correctedRecent15Sales = correctedRecent15Sales;
        this.correctedPrevious15Sales = correctedPrevious15Sales;
        this.correctedRecent30Sales = correctedRecent30Sales;
        this.correctedPrevious30Sales = correctedPrevious30Sales;
        this.correctedRecent60Sales = correctedRecent60Sales;
        this.correctedHistoricalT38ToT8Sales = correctedHistoricalT38ToT8Sales;
        this.correctedSalesGrowth15 = correctedSalesGrowth15;
        this.correctedSalesGrowth30 = correctedSalesGrowth30;
        this.appliedFactorNames = appliedFactorNames == null ? List.of() : List.copyOf(appliedFactorNames);
        this.evidenceJson = evidenceJson;
    }

    public ProductLifecycleFeatureSnapshot getRawSnapshot() {
        return rawSnapshot;
    }

    public BigDecimal getCorrectedRecent7Sales() {
        return correctedRecent7Sales;
    }

    public BigDecimal getCorrectedRecent15Sales() {
        return correctedRecent15Sales;
    }

    public BigDecimal getCorrectedPrevious15Sales() {
        return correctedPrevious15Sales;
    }

    public BigDecimal getCorrectedRecent30Sales() {
        return correctedRecent30Sales;
    }

    public BigDecimal getCorrectedPrevious30Sales() {
        return correctedPrevious30Sales;
    }

    public BigDecimal getCorrectedRecent60Sales() {
        return correctedRecent60Sales;
    }

    public BigDecimal getCorrectedHistoricalT38ToT8Sales() {
        return correctedHistoricalT38ToT8Sales;
    }

    public BigDecimal getCorrectedSalesGrowth15() {
        return correctedSalesGrowth15;
    }

    public BigDecimal getCorrectedSalesGrowth30() {
        return correctedSalesGrowth30;
    }

    public List<String> getAppliedFactorNames() {
        return appliedFactorNames;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}
