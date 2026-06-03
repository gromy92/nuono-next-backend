package com.nuono.next.productanalysis;

import java.util.List;

public class ProductLifecycleAnalysisOverviewView {

    private final ProductLifecycleAnalysisSummaryView summary;
    private final List<ProductLifecycleAnalysisRowView> rows;

    public ProductLifecycleAnalysisOverviewView(
            ProductLifecycleAnalysisSummaryView summary,
            List<ProductLifecycleAnalysisRowView> rows
    ) {
        this.summary = summary;
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static ProductLifecycleAnalysisOverviewView empty(String storeCode, String siteCode) {
        return new ProductLifecycleAnalysisOverviewView(
                ProductLifecycleAnalysisSummaryView.empty(storeCode, siteCode),
                List.of()
        );
    }

    public ProductLifecycleAnalysisSummaryView getSummary() {
        return summary;
    }

    public List<ProductLifecycleAnalysisRowView> getRows() {
        return rows;
    }
}
