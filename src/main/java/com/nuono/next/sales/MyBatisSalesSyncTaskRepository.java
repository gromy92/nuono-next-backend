package com.nuono.next.sales;

import com.nuono.next.infrastructure.mapper.SalesDataMapper;
import com.nuono.next.infrastructure.mapper.SalesSyncTaskMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MyBatisSalesSyncTaskRepository implements SalesSyncTaskRepository {

    private final SalesDataMapper salesDataMapper;
    private final SalesSyncTaskMapper taskMapper;

    public MyBatisSalesSyncTaskRepository(SalesDataMapper salesDataMapper, SalesSyncTaskMapper taskMapper) {
        this.salesDataMapper = salesDataMapper;
        this.taskMapper = taskMapper;
    }

    @Override
    public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
        Long taskId = salesDataMapper.nextSalesSyncTaskId();
        taskMapper.insert(taskId, command);
        return requireTask(taskId);
    }

    @Override
    public boolean claimRunning(Long taskId) {
        return taskMapper.claimRunning(taskId) == 1;
    }

    @Override
    public SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result) {
        taskMapper.markSucceeded(taskId, result);
        return requireTask(taskId);
    }

    @Override
    public SalesSyncTaskRecord markFailed(Long taskId, String failureReason) {
        taskMapper.markFailed(taskId, failureReason);
        return requireTask(taskId);
    }

    @Override
    public SalesSyncTaskRecord markWaitingForAuthorization(Long taskId, Long recoveryId) {
        taskMapper.markWaitingAuthorization(taskId, recoveryId);
        return requireTask(taskId);
    }

    @Override
    public java.util.List<SalesSyncTaskRecord> listQueued(int limit) {
        return taskMapper.selectQueuedIds(limit).stream()
                .map(this::requireTask)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public SalesSyncTaskRecord findById(Long taskId) {
        return requireTask(taskId);
    }

    private SalesSyncTaskRecord requireTask(Long taskId) {
        SalesSyncTaskRecord task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalStateException("销量同步任务不存在：" + taskId);
        }
        return task;
    }
}
