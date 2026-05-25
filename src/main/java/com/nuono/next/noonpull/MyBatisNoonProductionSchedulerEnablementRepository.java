package com.nuono.next.noonpull;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonProductionSchedulerEnablementMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local-db")
public class MyBatisNoonProductionSchedulerEnablementRepository
        implements NoonProductionSchedulerEnablementRepository {
    private static final String SEQUENCE_NAME = "noon_production_scheduler_enablement";
    private static final long INITIAL_ID = 142000L;

    private final NoonProductionSchedulerEnablementMapper mapper;
    private final AtomicBoolean schemaEnsured = new AtomicBoolean(false);

    public MyBatisNoonProductionSchedulerEnablementRepository(NoonProductionSchedulerEnablementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public NoonProductionSchedulerEnablementRecord save(NoonProductionSchedulerEnablementRecord record) {
        ensureSchema();
        NoonProductionSchedulerEnablementRecord copy = record.copy();
        LocalDateTime now = LocalDateTime.now();
        if (copy.getId() == null) {
            copy.setId(nextId());
        }
        if (copy.getCreatedAt() == null) {
            copy.setCreatedAt(now);
        }
        copy.setUpdatedAt(now);
        mapper.insertRecord(copy);
        return copy.copy();
    }

    @Override
    public List<NoonProductionSchedulerEnablementRecord> listRecent(int limit) {
        ensureSchema();
        return mapper.selectRecent(Math.max(1, Math.min(limit, 100)));
    }

    private Long nextId() {
        IdSequenceCommand command = new IdSequenceCommand(SEQUENCE_NAME, INITIAL_ID);
        mapper.nextId(command);
        Long allocatedId = command.getAllocatedId();
        if (allocatedId == null || allocatedId <= 0) {
            throw new IllegalStateException("Noon scheduler enablement ID allocation failed.");
        }
        return allocatedId;
    }

    private void ensureSchema() {
        if (!schemaEnsured.compareAndSet(false, true)) {
            return;
        }
        mapper.ensureNoonPullIdSequence();
        mapper.ensureEnablementTable();
    }
}
