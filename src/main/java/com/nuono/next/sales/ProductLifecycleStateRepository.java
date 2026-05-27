package com.nuono.next.sales;

import java.util.List;

public interface ProductLifecycleStateRepository {

    default Long nextJobId() {
        return null;
    }

    void saveCurrentState(ProductLifecycleCurrentState state);

    void saveHistory(ProductLifecycleHistoryRecord historyRecord);

    void saveJob(ProductLifecycleJobRecord job);

    ProductLifecycleCurrentState findCurrentState(ProductLifecycleStateQuery query);

    List<ProductLifecycleHistoryRecord> listHistory(ProductLifecycleStateQuery query);

    List<ProductLifecycleJobRecord> listJobs(ProductLifecycleJobQuery query);
}
