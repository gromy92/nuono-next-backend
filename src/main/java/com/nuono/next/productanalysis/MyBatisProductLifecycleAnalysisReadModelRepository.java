package com.nuono.next.productanalysis;

import com.nuono.next.infrastructure.mapper.ProductLifecycleAnalysisMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisProductLifecycleAnalysisReadModelRepository implements ProductLifecycleAnalysisReadModelRepository {

    private final ProductLifecycleAnalysisMapper mapper;

    public MyBatisProductLifecycleAnalysisReadModelRepository(ProductLifecycleAnalysisMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ProductLifecycleAnalysisSummaryView getSummary(ProductLifecycleAnalysisQuery query) {
        return mapper.selectSummary(query);
    }

    @Override
    public List<ProductLifecycleAnalysisRowView> listRows(ProductLifecycleAnalysisQuery query) {
        return mapper.selectRows(query);
    }
}
