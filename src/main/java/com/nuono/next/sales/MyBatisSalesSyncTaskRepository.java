package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesSyncTaskRepository implements SalesSyncTaskRepository {

    private final SalesDataMapper salesDataMapper;

    public MyBatisSalesSyncTaskRepository(SalesDataMapper salesDataMapper) {
        this.salesDataMapper = salesDataMapper;
    }

    @Override
    public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
        Long taskId = salesDataMapper.nextSalesSyncTaskId();
        salesDataMapper.insertSalesSyncTask(taskId, command);
        return requireTask(taskId);
    }

    @Override
    public SalesSyncTaskRecord markRunning(Long taskId) {
        salesDataMapper.updateSalesSyncTaskRunning(taskId);
        return requireTask(taskId);
    }

    @Override
    public SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result) {
        salesDataMapper.updateSalesSyncTaskSucceeded(taskId, result);
        return requireTask(taskId);
    }

    @Override
    public SalesSyncTaskRecord markFailed(Long taskId, String failureReason) {
        salesDataMapper.updateSalesSyncTaskFailed(taskId, failureReason);
        return requireTask(taskId);
    }

    @Override
    public SalesSyncTaskRecord findById(Long taskId) {
        return requireTask(taskId);
    }

    private SalesSyncTaskRecord requireTask(Long taskId) {
        SalesSyncTaskRecord task = salesDataMapper.selectSalesSyncTaskById(taskId);
        if (task == null) {
            throw new IllegalStateException("销量同步任务不存在：" + taskId);
        }
        return task;
    }
}
