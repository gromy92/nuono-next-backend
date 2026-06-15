package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonPullMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local-db")
public class MyBatisNoonPullRepository implements NoonPullRepository {
    private final NoonPullMapper mapper;

    public MyBatisNoonPullRepository(NoonPullMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        mapper.nextId(command);
        return command.getAllocatedId();
    }

    @Override
    public void insertPlan(NoonPullPlanRecord plan) {
        mapper.insertPlan(plan);
    }

    @Override
    public NoonPullPlanRecord selectPlan(Long planId) {
        return mapper.selectPlan(planId);
    }

    @Override
    public void updatePlan(NoonPullPlanRecord plan) {
        mapper.updatePlan(plan);
    }

    @Override
    public void insertTask(NoonPullTaskRecord task) {
        mapper.insertTask(task);
    }

    @Override
    public NoonPullTaskRecord selectTask(Long taskId) {
        return mapper.selectTask(taskId);
    }

    @Override
    public NoonPullTaskRecord selectActiveTaskByLockKey(String activeLockKey) {
        return mapper.selectActiveTaskByLockKey(activeLockKey);
    }

    @Override
    public NoonPullTaskRecord selectLatestTaskByLockKey(String activeLockKey) {
        return mapper.selectLatestTaskByLockKey(activeLockKey);
    }

    @Override
    public void updateTask(NoonPullTaskRecord task) {
        mapper.updateTask(task);
    }

    @Override
    public List<NoonPullPlanRecord> listPlans() {
        return mapper.listPlans();
    }

    @Override
    public List<NoonPullTaskRecord> listTasks() {
        return mapper.listTasks();
    }

    @Override
    public List<NoonPullTaskRecord> listActiveTasks() {
        return mapper.listActiveTasks();
    }
}
