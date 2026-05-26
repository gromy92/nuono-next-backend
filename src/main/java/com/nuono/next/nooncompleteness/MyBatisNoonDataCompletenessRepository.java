package com.nuono.next.nooncompleteness;

import com.nuono.next.infrastructure.mapper.IdSequenceCommand;
import com.nuono.next.infrastructure.mapper.NoonDataCompletenessMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local-db")
public class MyBatisNoonDataCompletenessRepository implements NoonDataCompletenessRepository {
    private final NoonDataCompletenessMapper mapper;

    public MyBatisNoonDataCompletenessRepository(NoonDataCompletenessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Long nextId(String sequenceName, Long initialValue) {
        IdSequenceCommand command = new IdSequenceCommand(sequenceName, initialValue);
        mapper.nextId(command);
        return command.getAllocatedId();
    }

    @Override
    public void insertCompleteness(NoonDataCompletenessRecord record) {
        mapper.insertCompleteness(record);
    }

    @Override
    public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
        return mapper.listCompleteness(query);
    }

    @Override
    public void insertGapWindow(NoonDataGapWindowRecord record) {
        mapper.insertGapWindow(record);
    }

    @Override
    public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
        return mapper.listGapWindows(query);
    }

    @Override
    public void deleteHistoryBackfillGaps(
            Long completenessId,
            NoonDataCategory category,
            LocalDateTime updatedAt
    ) {
        mapper.deleteHistoryBackfillGaps(completenessId, category, updatedAt);
    }
}
