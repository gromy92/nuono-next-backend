package com.nuono.next.datapull.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.nuono.next.infrastructure.mapper.SnapshotEffectiveItemMapper;
import com.nuono.next.infrastructure.mapper.SnapshotEffectiveItemMapper.EffectiveItemInsert;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnapshotEffectiveItemStoreTest {
    private static final SnapshotPayloadCodec<String> CODEC = new SnapshotPayloadCodec<>() {
        @Override public String encode(String item) { return item; }
        @Override public String decode(String payload) { return payload; }
    };

    @Test
    void repeatedUnsafeGenerationsStayOneLayerAndLaterCleanGenerationResets() {
        FakeEffectiveMapper mapper = new FakeEffectiveMapper();
        SnapshotEffectiveItemStore<String> store = new SnapshotEffectiveItemStore<>(mapper, CODEC);
        long currentTask = 4001L;
        materialize(store, currentTask, identities("BASE", 45));

        for (int generation = 1; generation <= 12; generation++) {
            long nextTask = currentTask + 1L;
            materialize(store, nextTask, List.of("ID-00:UPDATE-" + generation));
            String cursor = null;
            int slices = 0;
            while (true) {
                SnapshotCarryForwardResult result = store.carry(
                        nextTask, currentTask, SnapshotCarryMode.FULL, cursor, 20
                );
                slices++;
                if (result.isComplete()) break;
                assertThat(result.getMaterializedItemCount()).isBetween(1, 20);
                cursor = result.getLastStableIdentity();
            }
            assertThat(slices).isLessThanOrEqualTo(4);
            assertThat(mapper.size(nextTask)).isEqualTo(45);
            mapper.retainOnly(nextTask);
            currentTask = nextTask;
        }

        long cleanTask = currentTask + 1L;
        materialize(store, cleanTask, List.of("ID-00:CLEAN", "ID-01:CLEAN"));
        assertThat(mapper.size(cleanTask)).isEqualTo(2);
        assertThat(mapper.taskCount()).isEqualTo(2);
    }

    @Test
    void carryModeIgnoresOrdinaryDuplicateCountButNarrowsKnownBusinessFailure() {
        SnapshotCarryModeResolver resolver = new SnapshotCarryModeResolver();
        CompleteSnapshot<String> duplicate = snapshot(5001L, 1L, 1, 0L);
        SnapshotApplyProgressRow clean = progress(duplicate, 1L, 0L, 1L);
        assertThat(resolver.resolve(duplicate, clean)).isEqualTo(SnapshotCarryMode.NONE);

        SnapshotApplyProgressRow presenceOnly = progress(duplicate, 1L, 0L, 0L);
        assertThat(resolver.resolve(duplicate, presenceOnly))
                .isEqualTo(SnapshotCarryMode.TARGETED);

        SnapshotApplyProgressRow unidentified = progress(duplicate, 1L, 1L, 0L);
        assertThat(resolver.resolve(duplicate, unidentified))
                .isEqualTo(SnapshotCarryMode.FULL);
    }

    private void materialize(
            SnapshotEffectiveItemStore<String> store,
            long taskId,
            List<String> values
    ) {
        for (int offset = 0; offset < values.size(); offset += 20) {
            int end = Math.min(values.size(), offset + 20);
            List<SnapshotApplyItem<String>> items = new ArrayList<>();
            for (int index = offset; index < end; index++) {
                items.add(item(taskId, index, values.get(index)));
            }
            assertThat(store.materialize(taskId, items)).isEqualTo(items.size());
        }
    }

    private SnapshotApplyItem<String> item(long taskId, int ordinal, String encoded) {
        String identity = encoded.substring(0, encoded.indexOf(':'));
        SnapshotStageItemRow row = new SnapshotStageItemRow();
        row.setTaskId(taskId);
        row.setPageNo(1);
        row.setItemOrdinal(ordinal);
        row.setStableIdentity(identity);
        row.setContentFingerprint("a".repeat(64));
        row.setPayload(encoded);
        row.setValidatedIdentityCandidate(true);
        row.setAbsenceReconciliationSafe(true);
        return new SnapshotApplyItem<>(row, encoded);
    }

    private List<String> identities(String value, int count) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(String.format("ID-%02d:%s", index, value));
        }
        return result;
    }

    private SnapshotApplyProgressRow progress(
            CompleteSnapshot<?> snapshot,
            long prepared,
            long absenceUnsafe,
            long effective
    ) {
        SnapshotApplyProgressRow row = new SnapshotApplyProgressRow();
        row.setTaskId(snapshot.getTaskId());
        row.setPreparedItemCount(prepared);
        row.setAbsenceUnsafeItemCount(absenceUnsafe);
        row.setEffectiveItemCount(effective);
        return row;
    }

    private CompleteSnapshot<String> snapshot(
            long taskId,
            long applied,
            int skippedIdentity,
            long businessSkipped
    ) {
        DataPullTask task = DataPullTask.queued(
                taskId, OperationCode.DP04, "NOON_PARTNER_PRODUCT_LIST", 307L, 8001L,
                "PRJ108065", null, "PRJ108065", "STR108065-NSA", "SA", "scope-1",
                LocalDateTime.of(2026, 8, 3, 3, 0), "snapshot:2026-08-03",
                "SNAPSHOT_APPLY", LocalDateTime.of(2026, 8, 3, 3, 0)
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(1L);
        task.setLeaseOwner("worker");
        task.setLeaseUntil(LocalDateTime.of(2026, 8, 3, 3, 10));
        long source = applied + skippedIdentity + businessSkipped;
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "generation-" + taskId,
                LocalDateTime.of(2026, 8, 3, 3, 0),
                source
        );
        return CompleteSnapshot.from(task, SnapshotStageProof.completeMetadata(
                1, applied, skippedIdentity, businessSkipped, source, authority
        ));
    }

    private static final class FakeEffectiveMapper implements SnapshotEffectiveItemMapper {
        private final Map<Long, Map<String, EffectiveItemInsert>> generations =
                new LinkedHashMap<>();

        @Override
        public int insertEffectiveItem(long taskId, EffectiveItemInsert item) {
            return generations.computeIfAbsent(taskId, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(item.getStableIdentity(), item) == null ? 1 : 0;
        }

        @Override
        public List<SnapshotStageItemRow> selectFullCarryChunk(
                long sourceTaskId,
                long targetTaskId,
                String afterStableIdentity,
                int limit
        ) {
            Map<String, EffectiveItemInsert> target = generations.computeIfAbsent(
                    targetTaskId, ignored -> new LinkedHashMap<>()
            );
            return generations.getOrDefault(sourceTaskId, Map.of()).values().stream()
                    .filter(item -> afterStableIdentity == null
                            || item.getStableIdentity().compareTo(afterStableIdentity) > 0)
                    .filter(item -> !target.containsKey(item.getStableIdentity()))
                    .sorted(Comparator.comparing(EffectiveItemInsert::getStableIdentity))
                    .limit(limit)
                    .map(item -> row(targetTaskId, item))
                    .collect(java.util.stream.Collectors.toList());
        }

        @Override
        public List<SnapshotStageItemRow> selectTargetedCarryChunk(
                long sourceTaskId,
                long targetTaskId,
                String afterStableIdentity,
                int limit
        ) {
            return List.of();
        }

        int size(long taskId) {
            return generations.getOrDefault(taskId, Map.of()).size();
        }

        int taskCount() { return generations.size(); }

        void retainOnly(long taskId) {
            generations.keySet().removeIf(id -> id != taskId);
        }

        private SnapshotStageItemRow row(long targetTaskId, EffectiveItemInsert item) {
            SnapshotStageItemRow row = new SnapshotStageItemRow();
            row.setTaskId(targetTaskId);
            row.setPageNo(item.getPageNo());
            row.setItemOrdinal(item.getItemOrdinal());
            row.setStableIdentity(item.getStableIdentity());
            row.setContentFingerprint(item.getContentFingerprint());
            row.setPayload(item.getValuePayload());
            row.setValidatedIdentityCandidate(true);
            row.setAbsenceReconciliationSafe(true);
            return row;
        }
    }
}
