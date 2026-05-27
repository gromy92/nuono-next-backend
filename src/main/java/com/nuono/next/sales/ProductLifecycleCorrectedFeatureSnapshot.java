package com.nuono.next.sales;

import java.math.BigDecimal;
import java.util.List;

public class ProductLifecycleCorrectedFeatureSnapshot {

    private final ProductLifecycleFeatureSnapshot rawSnapshot;
    private final BigDecimal correctedRecent15Sales;
    private final BigDecimal correctedPrevious15Sales;
    private final BigDecimal correctedRecent30Sales;
    private final BigDecimal correctedRecent60Sales;
    private final BigDecimal correctedSalesGrowth15;
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
        this.rawSnapshot = rawSnapshot;
        this.correctedRecent15Sales = correctedRecent15Sales;
        this.correctedPrevious15Sales = correctedPrevious15Sales;
        this.correctedRecent30Sales = correctedRecent30Sales;
        this.correctedRecent60Sales = correctedRecent60Sales;
        this.correctedSalesGrowth15 = correctedSalesGrowth15;
        this.appliedFactorNames = appliedFactorNames == null ? List.of() : List.copyOf(appliedFactorNames);
        this.evidenceJson = evidenceJson;
    }

    public ProductLifecycleFeatureSnapshot getRawSnapshot() {
        return rawSnapshot;
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

    public BigDecimal getCorrectedRecent60Sales() {
        return correctedRecent60Sales;
    }

    public BigDecimal getCorrectedSalesGrowth15() {
        return correctedSalesGrowth15;
    }

    public List<String> getAppliedFactorNames() {
        return appliedFactorNames;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}
