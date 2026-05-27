package com.nuono.next.sales;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProductLifecycleFeatureSnapshot {

    private final ProductLifecycleStateQuery query;
    private final LocalDate analysisDate;
    private final ProductLifecycleFeatureWindow recent7;
    private final ProductLifecycleFeatureWindow recent15;
    private final ProductLifecycleFeatureWindow previous15;
    private final ProductLifecycleFeatureWindow recent30;
    private final ProductLifecycleFeatureWindow previous30;
    private final ProductLifecycleFeatureWindow recent60;
    private final BigDecimal salesGrowth15;
    private final BigDecimal pvGrowth15;
    private final BigDecimal salesVolatility30;
    private final int activeSalesDays30;
    private final List<String> qualityReasons;
    private final String evidenceJson;

    public ProductLifecycleFeatureSnapshot(
            ProductLifecycleStateQuery query,
            LocalDate analysisDate,
            ProductLifecycleFeatureWindow recent7,
            ProductLifecycleFeatureWindow recent15,
            ProductLifecycleFeatureWindow previous15,
            ProductLifecycleFeatureWindow recent30,
            ProductLifecycleFeatureWindow previous30,
            ProductLifecycleFeatureWindow recent60,
            BigDecimal salesGrowth15,
            BigDecimal pvGrowth15,
            BigDecimal salesVolatility30,
            int activeSalesDays30,
            List<String> qualityReasons,
            String evidenceJson
    ) {
        this.query = query;
        this.analysisDate = analysisDate;
        this.recent7 = recent7;
        this.recent15 = recent15;
        this.previous15 = previous15;
        this.recent30 = recent30;
        this.previous30 = previous30;
        this.recent60 = recent60;
        this.salesGrowth15 = salesGrowth15;
        this.pvGrowth15 = pvGrowth15;
        this.salesVolatility30 = salesVolatility30;
        this.activeSalesDays30 = activeSalesDays30;
        this.qualityReasons = qualityReasons == null ? List.of() : List.copyOf(qualityReasons);
        this.evidenceJson = evidenceJson;
    }

    public ProductLifecycleStateQuery getQuery() {
        return query;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public ProductLifecycleFeatureWindow getRecent7() {
        return recent7;
    }

    public ProductLifecycleFeatureWindow getRecent15() {
        return recent15;
    }

    public ProductLifecycleFeatureWindow getPrevious15() {
        return previous15;
    }

    public ProductLifecycleFeatureWindow getRecent30() {
        return recent30;
    }

    public ProductLifecycleFeatureWindow getPrevious30() {
        return previous30;
    }

    public ProductLifecycleFeatureWindow getRecent60() {
        return recent60;
    }

    public BigDecimal getSalesGrowth15() {
        return salesGrowth15;
    }

    public BigDecimal getPvGrowth15() {
        return pvGrowth15;
    }

    public BigDecimal getSalesVolatility30() {
        return salesVolatility30;
    }

    public int getActiveSalesDays30() {
        return activeSalesDays30;
    }

    public List<String> getQualityReasons() {
        return qualityReasons;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }
}
