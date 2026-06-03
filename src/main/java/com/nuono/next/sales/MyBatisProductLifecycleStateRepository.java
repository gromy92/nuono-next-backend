package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.ProductLifecycleStateMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisProductLifecycleStateRepository implements ProductLifecycleStateRepository {

    private final ProductLifecycleStateMapper mapper;

    public MyBatisProductLifecycleStateRepository(ProductLifecycleStateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextJobId() {
        return mapper.nextProductLifecycleJobId();
    }

    @Override
    public void resetScope(ProductLifecycleCalculationScope scope) {
        mapper.deleteHistoryByScope(scope);
        mapper.deleteCurrentStateByScope(scope);
    }

    @Override
    public void saveCurrentState(ProductLifecycleCurrentState state) {
        Long id = state.getId();
        if (id == null) {
            id = mapper.nextProductLifecycleCurrentStateId();
        }
        mapper.upsertCurrentState(id, state);
    }

    @Override
    public void saveHistory(ProductLifecycleHistoryRecord historyRecord) {
        Long id = historyRecord.getId();
        if (id == null) {
            id = mapper.nextProductLifecycleHistoryId();
        }
        mapper.insertHistory(id, historyRecord);
    }

    @Override
    public void saveJob(ProductLifecycleJobRecord job) {
        Long id = job.getId();
        if (id == null) {
            id = mapper.nextProductLifecycleJobId();
        }
        mapper.insertJob(id, job);
    }

    @Override
    public ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query) {
        return mapper.selectCurrentState(query);
    }

    @Override
    public List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query) {
        return mapper.selectHistory(query);
    }

    @Override
    public List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query) {
        return mapper.selectJobs(query);
    }
}
