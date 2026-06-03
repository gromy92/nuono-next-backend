package com.nuono.next.productanalysis;

import java.util.List;

public interface ProductLifecycleAnalysisReadModelRepository {

    ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query);

    List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query);
}
