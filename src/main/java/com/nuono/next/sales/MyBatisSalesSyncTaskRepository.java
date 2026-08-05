package com.nuono.next.sales;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.infrastructure.mapper.SalesSyncTaskMapper;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.LEGACY)
public class MyBatisSalesSyncTaskRepository implements SalesSyncTaskRepository {

    private final SalesSyncTaskMapper taskMapper;

    public MyBatisSalesSyncTaskRepository(SalesSyncTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
        Long taskId = taskMapper.nextTaskId();
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
