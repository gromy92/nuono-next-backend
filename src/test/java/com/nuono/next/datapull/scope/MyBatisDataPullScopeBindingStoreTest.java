package com.nuono.next.datapull.scope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScopeBindingMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MyBatisDataPullScopeBindingStoreTest {
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 8, 2, 0, 0);

    @Test
    void payloadChangeClosesTheOldEpochAndCreatesOneNewOpenEpoch() {
        FakeMapper mapper = new FakeMapper(T0.plusHours(6));
        MyBatisDataPullScopeBindingStore store = new MyBatisDataPullScopeBindingStore(mapper);
        DataPullScopeBindingCandidate first = candidate("scope-a", "{\"keyword\":\"paper\"}", T0);
        DataPullScopeBindingEpoch original = store.reconcileCurrent(
                OperationCode.DP08A, List.of(first)
        ).get(0);
        DataPullScopeBindingCandidate changed = candidate(
                "scope-a", "{\"keyword\":\"paper roll\"}", T0.plusHours(3)
        );

        DataPullScopeBindingEpoch current = store.reconcileCurrent(
                OperationCode.DP08A, List.of(changed)
        ).get(0);

        assertThat(mapper.rows.get(original.getBindingId()).getEffectiveUntilUtc())
                .isEqualTo(T0.plusHours(3));
        assertThat(current.getPayload()).contains("paper roll");
        assertThat(mapper.open(OperationCode.DP08A)).containsExactly(current);
    }

    @Test
    void aScopeMissingFromTheCompleteCohortIsClosedNotDeleted() {
        FakeMapper mapper = new FakeMapper(T0.plusHours(1));
        MyBatisDataPullScopeBindingStore store = new MyBatisDataPullScopeBindingStore(mapper);
        DataPullScopeBindingEpoch binding = store.reconcileCurrent(
                OperationCode.DP08A,
                List.of(candidate("scope-a", "{}", T0))
        ).get(0);

        mapper.now = T0.plusHours(2);
        store.reconcileCurrent(OperationCode.DP08A, List.of());

        assertThat(mapper.rows.get(binding.getBindingId()).getEffectiveUntilUtc())
                .isEqualTo(T0.plusHours(2));
        assertThat(mapper.open(OperationCode.DP08A)).isEmpty();
    }

    @Test
    void staleSourceTimestampUsesTheDatabaseObservationAsTheNewEpochBoundary() {
        FakeMapper mapper = new FakeMapper(T0.plusHours(2));
        MyBatisDataPullScopeBindingStore store = new MyBatisDataPullScopeBindingStore(mapper);
        DataPullScopeBindingEpoch original = store.reconcileCurrent(
                OperationCode.DP08A,
                List.of(candidate("scope-a", "{\"v\":1}", T0.plusHours(1)))
        ).get(0);

        DataPullScopeBindingEpoch replacement = store.reconcileCurrent(
                OperationCode.DP08A,
                List.of(candidate("scope-a", "{\"v\":2}", T0))
        ).get(0);

        assertThat(mapper.rows.get(original.getBindingId()).getEffectiveUntilUtc())
                .isEqualTo(T0.plusHours(2));
        assertThat(replacement.getEffectiveFromUtc()).isEqualTo(T0.plusHours(2));
    }

    @Test
    void twoDifferentPayloadsInTheSameDatabaseMillisecondFailWithoutZeroLengthEpoch() {
        FakeMapper mapper = new FakeMapper(T0.plusHours(1));
        MyBatisDataPullScopeBindingStore store = new MyBatisDataPullScopeBindingStore(mapper);
        DataPullScopeBindingEpoch original = store.reconcileCurrent(
                OperationCode.DP08A,
                List.of(candidate("scope-a", "{\"v\":1}", T0.plusHours(1)))
        ).get(0);

        assertThatThrownBy(() -> store.reconcileCurrent(
                OperationCode.DP08A,
                List.of(candidate("scope-a", "{\"v\":2}", T0))
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NON_MONOTONIC");
        assertThat(mapper.rows.get(original.getBindingId()).getEffectiveUntilUtc()).isNull();
    }

    private static DataPullScopeBindingCandidate candidate(
            String scopeKey,
            String payload,
            LocalDateTime effectiveFromUtc
    ) {
        return new DataPullScopeBindingCandidate(
                OperationCode.DP08A, scopeKey, "DP08_KEYWORD_V1", payload, effectiveFromUtc
        );
    }

    private static final class FakeMapper implements DataPullScopeBindingMapper {
        private final Map<String, DataPullScopeBindingEpoch> rows = new LinkedHashMap<>();
        private LocalDateTime now;

        private FakeMapper(LocalDateTime now) { this.now = now; }
        @Override public String lockActiveOperation(OperationCode operation) { return operation.name(); }
        @Override public LocalDateTime selectDatabaseNowUtc() { return now; }
        @Override public List<DataPullScopeBindingEpoch> lockOpenBindings(OperationCode operation) {
            return open(operation);
        }
        @Override public List<DataPullScopeBindingEpoch> lockOpenBindingsByScopeKeys(
                OperationCode operation, List<String> scopeKeys
        ) {
            List<DataPullScopeBindingEpoch> result = new ArrayList<>();
            for (DataPullScopeBindingEpoch row : open(operation)) {
                if (scopeKeys.contains(row.getScopeKey())) result.add(row);
            }
            return result;
        }
        @Override public List<DataPullScopeBindingEpoch> lockLatestBindingsByScopeKeys(
                OperationCode operation, List<String> scopeKeys
        ) {
            Map<String, DataPullScopeBindingEpoch> latest = new LinkedHashMap<>();
            for (DataPullScopeBindingEpoch row : rows.values()) {
                if (row.getOperationCode() != operation || !scopeKeys.contains(row.getScopeKey())) {
                    continue;
                }
                DataPullScopeBindingEpoch previous = latest.get(row.getScopeKey());
                if (previous == null || row.getEffectiveFromUtc().isAfter(previous.getEffectiveFromUtc())) {
                    latest.put(row.getScopeKey(), row);
                }
            }
            return new ArrayList<>(latest.values());
        }
        @Override public List<DataPullScopeBindingEpoch> lockMissingOpenBindingsAfter(
                OperationCode operation, long epochNo, String afterScopeKey, int limit
        ) {
            List<DataPullScopeBindingEpoch> result = new ArrayList<>();
            for (DataPullScopeBindingEpoch row : open(operation)) {
                if (afterScopeKey == null || row.getScopeKey().compareTo(afterScopeKey) > 0) {
                    result.add(row);
                }
                if (result.size() == limit) break;
            }
            return result;
        }
        private List<DataPullScopeBindingEpoch> open(OperationCode operation) {
            List<DataPullScopeBindingEpoch> result = new ArrayList<>();
            for (DataPullScopeBindingEpoch row : rows.values()) {
                if (row.getOperationCode() == operation && row.getEffectiveUntilUtc() == null) {
                    result.add(row);
                }
            }
            return result;
        }
        @Override public int insertOpenBinding(DataPullScopeBindingEpoch binding) {
            return rows.putIfAbsent(binding.getBindingId(), binding) == null ? 1 : 0;
        }
        @Override public int insertOpenBindings(List<DataPullScopeBindingEpoch> bindings) {
            int inserted = 0;
            for (DataPullScopeBindingEpoch binding : bindings) {
                inserted += insertOpenBinding(binding);
            }
            return inserted;
        }
        @Override public int closeBindings(
                OperationCode operation, List<ScheduleBindingCloseCommand> commands
        ) {
            int changed = 0;
            for (ScheduleBindingCloseCommand command : commands) {
                DataPullScopeBindingEpoch row = rows.get(command.getBindingId());
                if (row != null && row.getOperationCode() == operation) {
                    changed += closeBinding(
                            command.getBindingId(), command.getPayloadSha256(),
                            command.getEffectiveUntilUtc(), command.getEffectiveUntilUtc()
                    );
                }
            }
            return changed;
        }
        @Override public int closeBinding(
                String bindingId, String payloadSha256,
                LocalDateTime effectiveUntilUtc, LocalDateTime updatedAtUtc
        ) {
            DataPullScopeBindingEpoch row = rows.get(bindingId);
            if (row == null || row.getEffectiveUntilUtc() != null
                    || !row.getPayloadSha256().equals(payloadSha256)
                    || !effectiveUntilUtc.isAfter(row.getEffectiveFromUtc())) {
                return 0;
            }
            row.setEffectiveUntilUtc(effectiveUntilUtc);
            row.setUpdatedAtUtc(updatedAtUtc);
            return 1;
        }
        @Override public DataPullScopeBindingEpoch selectById(String bindingId) {
            return rows.get(bindingId);
        }
    }
}
