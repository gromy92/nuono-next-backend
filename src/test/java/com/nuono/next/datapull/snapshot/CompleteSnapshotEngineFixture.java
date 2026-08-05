package com.nuono.next.datapull.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.AdvanceResult;
import com.nuono.next.datapull.runtime.BackoffPolicy;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.datapull.runtime.ProviderWaitTransition;
import com.nuono.next.datapull.runtime.TaskState;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared deterministic fixture for the focused complete-snapshot engine tests. */
final class CompleteSnapshotEngineFixture {
    static final SnapshotCheckpointCodec CODEC = new SnapshotCheckpointCodec();

    private CompleteSnapshotEngineFixture() {
    }

    static CompleteSnapshotEngine<Item> engine(
            OperationCode operationCode,
            SnapshotPageProvider<Item> provider,
            InMemorySnapshotStageStore<Item> stageStore,
            RecordingWriter writer
    ) {
        return new CompleteSnapshotEngine<>(
                operationCode,
                provider,
                stageStore,
                writer,
                CODEC,
                new ProviderWaitTransition(new BackoffPolicy(
                        Duration.ofMinutes(1), Duration.ofHours(1), 0.0d
                ))
        );
    }

    static InMemorySnapshotStageStore<Item> stageStore() {
        return new InMemorySnapshotStageStore<>(new SnapshotItemDescriptor<Item>() {
            @Override
            public String stableIdentity(Item item) {
                return item.identity;
            }

            @Override
            public String stableContentFingerprint(Item item) {
                return item.identity + ":" + item.value;
            }
        });
    }

    static DataPullTask task(
            long taskId,
            OperationCode operationCode,
            long fenceEpoch,
            String checkpoint
    ) {
        DataPullTask task = DataPullTask.queued(
                taskId,
                operationCode,
                "noon-partner",
                307L,
                108065L,
                "PRJ108065",
                "egress-cn-1",
                "PRJ108065",
                "STR108065-NSA",
                "SA",
                "STR108065-NSA:SA",
                LocalDateTime.of(2026, 8, 2, 3, 0),
                operationCode.name() + ":complete-snapshot:2026-08-02",
                "FETCH_PAGE",
                LocalDateTime.of(2026, 8, 2, 2, 59)
        );
        task.setState(TaskState.RUNNING);
        task.setFenceEpoch(fenceEpoch);
        task.setAttempt(1);
        task.setCheckpoint(checkpoint);
        task.setLeaseOwner("snapshot-test-worker");
        task.setLeaseUntil(LocalDateTime.of(2026, 8, 2, 3, 5));
        return task;
    }

    static void continueAsNextClaim(DataPullTask task, AdvanceResult result) {
        task.setCheckpoint(result.getCheckpoint());
        task.setFenceEpoch(task.getFenceEpoch() + 1L);
        task.setAttempt(task.getAttempt() + 1);
        task.setState(TaskState.RUNNING);
    }

    @SafeVarargs
    static SnapshotPage<Item> page(
            int pageNo,
            Integer nextPage,
            Boolean lastPage,
            Integer totalPages,
            Item... items
    ) {
        int pages = totalPages == null ? 1 : totalPages;
        long declaredCount = items.length == 0
                ? 0L
                : Math.multiplyExact((long) pages, items.length);
        SnapshotCollectionAuthority authority = SnapshotCollectionAuthority.fromProviderToken(
                SnapshotCollectionAuthority.Kind.PAGED_GENERATION,
                "fixture-generation:" + pages + ":" + declaredCount,
                LocalDateTime.of(2026, 8, 2, 2, 58),
                declaredCount
        );
        return new SnapshotPage<>(
                pageNo, nextPage, lastPage, totalPages, List.of(items),
                authority, items.length, 0
        );
    }

    static Item item(String identity, String value) {
        return new Item(identity, value);
    }

    static List<String> values(List<Item> items) {
        List<String> result = new ArrayList<>();
        for (Item item : items) {
            result.add(item.identity + ":" + item.value);
        }
        return result;
    }

    static void assertScopeSnapshot(SnapshotPageRequest request) {
        assertEquals(307L, request.getOwnerUserId());
        assertEquals(108065L, request.getLogicalStoreId());
        assertEquals("PRJ108065", request.getAccountKey());
        assertEquals("egress-cn-1", request.getEgressKey());
        assertEquals("PRJ108065", request.getProjectCode());
        assertEquals("STR108065-NSA", request.getStoreCode());
        assertEquals("SA", request.getSiteCode());
        assertEquals("STR108065-NSA:SA", request.getScopeKey());
    }

    static void assertScopeSnapshot(CompleteSnapshot<Item> snapshot) {
        assertEquals(307L, snapshot.getOwnerUserId());
        assertEquals(108065L, snapshot.getLogicalStoreId());
        assertEquals("PRJ108065", snapshot.getAccountKey());
        assertEquals("egress-cn-1", snapshot.getEgressKey());
        assertEquals("PRJ108065", snapshot.getProjectCode());
        assertEquals("STR108065-NSA", snapshot.getStoreCode());
        assertEquals("SA", snapshot.getSiteCode());
        assertEquals("STR108065-NSA:SA", snapshot.getScopeKey());
        assertEquals("snapshot-test-worker", snapshot.getLeaseOwner());
    }

    static final class ScriptedProvider implements SnapshotPageProvider<Item> {
        final Map<Integer, Deque<ProviderOutcome<SnapshotPage<Item>>>> outcomes =
                new LinkedHashMap<>();
        final List<Integer> calls = new ArrayList<>();
        SnapshotPageRequest lastRequest;

        @SafeVarargs
        final void add(
                int pageNo,
                ProviderOutcome<SnapshotPage<Item>>... pageOutcomes
        ) {
            outcomes.put(pageNo, new ArrayDeque<>(List.of(pageOutcomes)));
        }

        @Override
        public ProviderOutcome<SnapshotPage<Item>> fetchPage(SnapshotPageRequest request) {
            lastRequest = request;
            calls.add(request.getPageNo());
            Deque<ProviderOutcome<SnapshotPage<Item>>> pageOutcomes =
                    outcomes.get(request.getPageNo());
            if (pageOutcomes == null || pageOutcomes.isEmpty()) {
                return ProviderOutcome.contractError("UNSCRIPTED_PAGE");
            }
            return pageOutcomes.removeFirst();
        }
    }

    static final class RecordingWriter implements CompleteSnapshotWriter<Item> {
        final Map<Long, CompleteSnapshot<Item>> applied = new LinkedHashMap<>();
        int replaceCalls;

        @Override
        public ReplaceResult replace(CompleteSnapshot<Item> snapshot) {
            replaceCalls++;
            if (applied.containsKey(snapshot.getTaskId())) {
                return ReplaceResult.ALREADY_APPLIED;
            }
            applied.put(snapshot.getTaskId(), snapshot);
            return ReplaceResult.APPLIED;
        }

        int appliedCount() {
            return applied.size();
        }

        CompleteSnapshot<Item> snapshot(long taskId) {
            return applied.get(taskId);
        }
    }

    static final class Item {
        final String identity;
        final String value;

        Item(String identity, String value) {
            this.identity = identity;
            this.value = value;
        }
    }
}
