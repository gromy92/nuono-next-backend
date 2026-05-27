package com.nuono.next.sales;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProductLifecycleStateService {

    private final ProductLifecycleStateRepository repository;

    public ProductLifecycleStateService(ProductLifecycleStateRepository repository) {
        this.repository = repository;
    }

    public ProductLifecycleStateOverview getOverview(ProductLifecycleStateQuery query) {
        ProductLifecycleCurrentState currentState = repository.findCurrentState(query);
        List<ProductLifecycleHistoryRecord> history = repository.listHistory(query);
        List<ProductLifecycleJobRecord> jobs = repository.listJobs(new ProductLifecycleJobQuery(
                query.getOwnerUserId(),
                query.getStoreCode(),
                query.getSiteCode()
        ));
        return new ProductLifecycleStateOverview(currentState, history, jobs);
    }

    public void saveCurrentState(ProductLifecycleCurrentState state) {
        repository.saveCurrentState(state);
    }

    public void recordHistory(ProductLifecycleHistoryRecord historyRecord) {
        repository.saveHistory(historyRecord);
    }

    public void recordJob(ProductLifecycleJobRecord job) {
        repository.saveJob(job);
    }
}
