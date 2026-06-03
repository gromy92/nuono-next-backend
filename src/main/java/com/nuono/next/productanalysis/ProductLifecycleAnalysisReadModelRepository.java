package com.nuono.next.productanalysis;

import java.util.List;

public interface ProductLifecycleAnalysisReadModelRepository {

    default Long findDataOwnerUserId(String storeCode, String siteCode) {
        return null;
    }

    ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query);

    List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query);
}
