package com.nuono.next.datapull.checkpoint;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScopeProgressMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/** Production adapter for fenced, monotonic scope progress. */
public final class MyBatisDataPullScopeProgressStore implements DataPullScopeProgressStore {

    private final DataPullScopeProgressMapper mapper;

    public MyBatisDataPullScopeProgressStore(DataPullScopeProgressMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public DataPullScopeProgress getOrCreate(
            OperationCode operationCode,
            String scopeKey,
            LocalDateTime nowUtc
    ) {
        DataPullScopeProgress initial = DataPullScopeProgress.initial(
                operationCode,
                scopeKey,
                nowUtc
        );
        int changed = mapper.insertIfAbsent(initial);
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException("scope progress initialization affected invalid rows: " + changed);
        }
        return requireProgress(operationCode, scopeKey);
    }

    @Override
    public Optional<DataPullScopeProgress> commitCompletedWindow(
            DataPullScopeProgressCommit commit
    ) {
        DataPullScopeProgressCommit nonNull = Objects.requireNonNull(commit, "commit");
        int changed = mapper.commitCompletedWindow(nonNull);
        if (changed < 0 || changed > 1) {
            throw new IllegalStateException("scope progress commit affected invalid rows: " + changed);
        }
        if (changed == 0) {
            return Optional.empty();
        }
        return Optional.of(requireProgress(nonNull.getOperationCode(), nonNull.getScopeKey()));
    }

    private DataPullScopeProgress requireProgress(OperationCode operationCode, String scopeKey) {
        DataPullScopeProgress progress = mapper.select(operationCode, scopeKey);
        if (progress == null) {
            throw new IllegalStateException("scope progress disappeared after persistence");
        }
        progress.validate();
        return progress;
    }
}
