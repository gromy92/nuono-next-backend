package com.nuono.next.system.task;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.OperationalTaskMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local-db")
public class MyBatisOperationalTaskRepository implements OperationalTaskRepository {
    private final OperationalTaskMapper mapper;

    public MyBatisOperationalTaskRepository(OperationalTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        mapper.nextId(command);
        return command.getAllocatedId();
    }

    @Override
    public void insert(OperationalTask task) {
        mapper.insert(task);
    }

    @Override
    public OperationalTask selectById(Long taskId) {
        return mapper.selectById(taskId);
    }

    @Override
    public OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey) {
        return mapper.selectActiveByNaturalKey(taskType, naturalKey);
    }

    @Override
    public OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey) {
        return mapper.selectLatestByNaturalKey(taskType, naturalKey);
    }

    @Override
    public OperationalTask selectLatestByNaturalKeyAndBatchKey(
            String taskType,
            String naturalKey,
            String batchKey
    ) {
        return mapper.selectLatestByNaturalKeyAndBatchKey(taskType, naturalKey, batchKey);
    }

    @Override
    public void update(OperationalTask task) {
        mapper.update(task);
    }

    @Override
    public boolean claimQueued(Long taskId, String message, LocalDateTime startedAt) {
        return mapper.claimQueued(taskId, message, startedAt) == 1;
    }

    @Override
    public boolean checkpointRunning(
            Long taskId,
            String payloadJson,
            int progressPercent,
            String message,
            LocalDateTime updatedAt
    ) {
        return mapper.checkpointRunning(taskId, payloadJson, progressPercent, message, updatedAt) == 1;
    }

    @Override
    public boolean requeueRunning(
            Long taskId,
            String payloadJson,
            int progressPercent,
            String errorCode,
            String message,
            LocalDateTime updatedAt
    ) {
        return mapper.requeueRunning(
                taskId,
                payloadJson,
                progressPercent,
                errorCode,
                message,
                updatedAt
        ) == 1;
    }

    @Override
    public boolean failStaleRunning(
            Long taskId,
            LocalDateTime staleBefore,
            String errorCode,
            String message,
            LocalDateTime finishedAt
    ) {
        return mapper.failStaleRunning(taskId, staleBefore, errorCode, message, finishedAt) == 1;
    }

    @Override
    public boolean failStaleQueued(
            Long taskId,
            LocalDateTime staleBefore,
            String errorCode,
            String message,
            LocalDateTime finishedAt
    ) {
        return mapper.failStaleQueued(taskId, staleBefore, errorCode, message, finishedAt) == 1;
    }

    @Override
    public List<OperationalTask> listActiveByTaskType(String taskType, int limit) {
        return mapper.listActiveByTaskType(taskType, limit);
    }

    @Override
    public List<OperationalTask> listActiveByTaskTypeAfterId(String taskType, Long afterTaskId, int limit) {
        return mapper.listActiveByTaskTypeAfterId(taskType, afterTaskId, limit);
    }

    @Override
    public List<OperationalTask> listRecent(String taskType, int limit) {
        return mapper.listRecent(taskType, limit);
    }
}
