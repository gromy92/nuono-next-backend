package com.nuono.next.datapull.persistence;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.DataPullScheduleTaskBatchMapper;
import com.nuono.next.infrastructure.mapper.DataPullTaskCompactionMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Fixed-call batch enqueue Adapter with the existing never-started compaction semantics. */
@Component
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public final class MyBatisDataPullTaskBatchStore {
    private final DataPullScheduleTaskBatchMapper mapper;
    private final DataPullTaskCompactionMapper compaction;

    public MyBatisDataPullTaskBatchStore(
            DataPullScheduleTaskBatchMapper mapper,
            DataPullTaskCompactionMapper compaction
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.compaction = Objects.requireNonNull(compaction, "compaction");
    }

    public List<Long> allocateIds(int count) {
        DataPullTaskIdBlock block = new DataPullTaskIdBlock(count);
        if (mapper.allocateTaskIdBlock(block) != 1) {
            throw new IllegalStateException("task id block allocation must affect one row");
        }
        long first = block.firstId();
        List<Long> result = new ArrayList<>(count);
        for (int offset = 0; offset < count; offset++) result.add(first + offset);
        return List.copyOf(result);
    }

    public List<DataPullTask> enqueue(List<DataPullTaskBatchProposal> values) {
        List<DataPullTaskBatchProposal> proposals = requireProposals(values);
        if (proposals.isEmpty()) return List.of();
        OperationCode operation = proposals.get(0).getTask().getOperationCode();
        Map<String, List<DataPullTask>> existing = lockCompactionCandidates(
                operation, proposals
        );
        List<DataPullTask> requested = new ArrayList<>(proposals.size());
        List<DataPullTask> superseded = new ArrayList<>();
        for (DataPullTaskBatchProposal proposal : proposals) {
            DataPullTask task = proposal.getTask();
            if (proposal.getCatchUpMode() == null) {
                requested.add(task);
                continue;
            }
            DataPullTaskCompaction.Resolution resolution = DataPullTaskCompaction.resolve(
                    task,
                    existing.getOrDefault(task.getScopeKey(), List.of()),
                    proposal.getCatchUpMode()
            );
            requested.add(resolution.getReplacement());
            superseded.addAll(resolution.getSuperseded());
        }
        if (!requested.isEmpty()) mapper.insertTasks(requested);
        Map<String, DataPullTask> stored = byStableKey(mapper.listByStableKeys(requested));
        List<DataPullTask> result = new ArrayList<>(requested.size());
        for (DataPullTask task : requested) {
            DataPullTask durable = stored.get(DataPullTaskContract.stableKey(task));
            if (durable == null) {
                throw new IllegalStateException("batch enqueue did not resolve its stable task key");
            }
            DataPullTaskContract.requirePersistedScopeSnapshot(durable);
            requireSameImmutablePayload(durable, task);
            result.add(durable);
        }
        List<DataPullTask> distinctSuperseded = distinctTasks(superseded);
        if (!distinctSuperseded.isEmpty()
                && compaction.supersedeStrictlyNeverStartedBatch(
                        distinctSuperseded, requested.get(0).getCreatedAt()
                ) != distinctSuperseded.size()) {
            throw new IllegalStateException("never-started batch supersede lost its CAS");
        }
        return List.copyOf(result);
    }

    private Map<String, List<DataPullTask>> lockCompactionCandidates(
            OperationCode operation,
            List<DataPullTaskBatchProposal> proposals
    ) {
        List<String> scopes = new ArrayList<>();
        for (DataPullTaskBatchProposal proposal : proposals) {
            if (proposal.getCatchUpMode() != null) scopes.add(proposal.getTask().getScopeKey());
        }
        if (scopes.isEmpty()) return Map.of();
        if (compaction.lockCompactionAnchor() == null) {
            throw new IllegalStateException("DP task compaction anchor is not initialized");
        }
        Map<String, List<DataPullTask>> result = new HashMap<>();
        List<DataPullTask> locked = List.copyOf(compaction.lockStrictlyNeverStartedBatch(
                operation, List.copyOf(new TreeSet<>(scopes))
        ));
        if (locked.size() > 64) {
            throw new IllegalStateException("never-started compaction cohort exceeds 64");
        }
        for (DataPullTask task : locked) {
            result.computeIfAbsent(task.getScopeKey(), ignored -> new ArrayList<>()).add(task);
        }
        return result;
    }

    private static List<DataPullTaskBatchProposal> requireProposals(
            List<DataPullTaskBatchProposal> values
    ) {
        List<DataPullTaskBatchProposal> proposals = List.copyOf(
                Objects.requireNonNull(values, "proposals")
        );
        if (proposals.size() > 64) throw new IllegalArgumentException("task batch exceeds 64");
        OperationCode operation = null;
        Set<String> stableKeys = new HashSet<>();
        Set<Long> ids = new HashSet<>();
        for (DataPullTaskBatchProposal proposal : proposals) {
            DataPullTask task = Objects.requireNonNull(proposal, "proposal").getTask();
            DataPullTaskContract.requireEnqueueable(task);
            if (operation == null) operation = task.getOperationCode();
            if (operation != task.getOperationCode()
                    || !stableKeys.add(DataPullTaskContract.stableKey(task))
                    || !ids.add(task.getId())) {
                throw new IllegalArgumentException("task batch is mixed or duplicate");
            }
        }
        return proposals;
    }

    private static Map<String, DataPullTask> byStableKey(List<DataPullTask> values) {
        Map<String, DataPullTask> result = new LinkedHashMap<>();
        for (DataPullTask task : List.copyOf(values)) {
            if (result.put(DataPullTaskContract.stableKey(task), task) != null) {
                throw new IllegalStateException("batch enqueue returned duplicate stable keys");
            }
        }
        return result;
    }

    private static List<DataPullTask> distinctTasks(List<DataPullTask> values) {
        Map<Long, DataPullTask> result = new LinkedHashMap<>();
        for (DataPullTask task : values) result.put(task.getId(), task);
        return List.copyOf(result.values());
    }

    private static void requireSameImmutablePayload(
            DataPullTask stored,
            DataPullTask requested
    ) {
        DataPullTaskContract.requireSameImmutablePayload(stored, requested);
    }
}
