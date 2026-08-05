package com.nuono.next.datapull.advertising;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingGenerationFactMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingGenerationMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingHeadMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingIdMapper;
import com.nuono.next.infrastructure.mapper.Dp06AdvertisingStageMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Builds an invisible DP-06 generation in bounded chunks, then moves one O(1) read head. */
@Component
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class NoonAdvertisingFactWriter implements AdvertisingFactWriter {
    static final int CHUNK_SIZE = 200;
    private static final long BATCH_INITIAL = 200_000L;
    private static final long CAMPAIGN_INITIAL = 210_000L;
    private static final long QUERY_INITIAL = 220_000L;

    private final Dp06AdvertisingStageMapper stageMapper;
    private final Dp06AdvertisingGenerationMapper generationMapper;
    private final Dp06AdvertisingGenerationFactMapper factMapper;
    private final Dp06AdvertisingHeadMapper headMapper;
    private final Dp06AdvertisingIdMapper idMapper;
    private final AdvertisingFactChunkPreparer chunkPreparer =
            new AdvertisingFactChunkPreparer();
    private final AdvertisingGenerationGuard guard = new AdvertisingGenerationGuard();

    public NoonAdvertisingFactWriter(
            Dp06AdvertisingStageMapper stageMapper,
            Dp06AdvertisingGenerationMapper generationMapper,
            Dp06AdvertisingGenerationFactMapper factMapper,
            Dp06AdvertisingHeadMapper headMapper,
            Dp06AdvertisingIdMapper idMapper
    ) {
        this.stageMapper = Objects.requireNonNull(stageMapper, "stageMapper");
        this.generationMapper = Objects.requireNonNull(generationMapper, "generationMapper");
        this.factMapper = Objects.requireNonNull(factMapper, "factMapper");
        this.headMapper = Objects.requireNonNull(headMapper, "headMapper");
        this.idMapper = Objects.requireNonNull(idMapper, "idMapper");
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public ApplyResult applyComplete(AdvertisingApplyCommand command) {
        AdvertisingApplyCommand value = Objects.requireNonNull(command, "command");
        try {
            AdvertisingTaskFenceRow task = stageMapper.selectTaskForUpdate(value.getTaskId());
            if (!guard.isCurrentTask(task, value)) return ApplyResult.STALE_FENCE;

            AdvertisingGenerationRow generation = generationMapper.selectForUpdate(
                    value.getTaskId()
            );
            if (generation == null) return initialize(value);
            guard.requireSameGeneration(generation, value);
            if ("SEALED".equals(generation.getState())) {
                guard.requireWinningHead(
                        headMapper.selectForUpdate(value), value, generation
                );
                return ApplyResult.ALREADY_APPLIED;
            }
            if (!"PREPARING".equals(generation.getState())) {
                throw new IllegalStateException("advertising generation state is invalid");
            }
            generation = adoptFence(generation, value);
            List<AdvertisingRawStageRow> rows = requireRows(stageMapper.selectRawChunk(
                    value.getTaskId(), generation.getCursorPageNo(),
                    generation.getCursorItemOrdinal(), CHUNK_SIZE
            ));
            if (!rows.isEmpty()) return prepareChunk(value, generation, rows);
            return seal(value, generation);
        } catch (AdvertisingApplyLeaseExpiredException
                | AdvertisingApplyContractException known) {
            throw known;
        } catch (IllegalArgumentException | IllegalStateException contractFailure) {
            throw new AdvertisingApplyContractException(contractFailure);
        }
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public ResetResult reset(long taskId, long fenceEpoch, String leaseOwner) {
        AdvertisingTaskFenceRow task = stageMapper.selectTaskForUpdate(taskId);
        if (task == null || !Objects.equals(task.getTaskId(), taskId)
                || !"DP06".equals(task.getOperationCode())
                || !Objects.equals(task.getFenceEpoch(), fenceEpoch)
                || !"RUNNING".equals(task.getState())
                || !Objects.equals(task.getLeaseOwner(), leaseOwner)
                || !Boolean.TRUE.equals(task.getLeaseValid())) {
            return ResetResult.STALE_FENCE;
        }
        AdvertisingGenerationRow generation = generationMapper.selectForUpdate(taskId);
        if (generation != null && "SEALED".equals(generation.getState())) {
            throw new AdvertisingApplyContractException(
                    new IllegalStateException("sealed advertising generation cannot reset")
            );
        }
        if (headMapper.countForTask(taskId) != 0) {
            throw new AdvertisingApplyContractException(
                    new IllegalStateException("visible advertising generation cannot reset")
            );
        }
        if (deletePreparedBatch(taskId) || deleteRawBatch(taskId)) {
            requireResetFence(taskId, fenceEpoch, leaseOwner);
            return ResetResult.MORE_WORK;
        }
        int deleted = generationMapper.deletePreparingIfEmpty(taskId);
        if (generation != null && deleted != 1) {
            throw new AdvertisingApplyContractException(
                    new IllegalStateException("advertising generation reset remained non-empty")
            );
        }
        requireResetFence(taskId, fenceEpoch, leaseOwner);
        return ResetResult.CLEARED;
    }

    private ApplyResult initialize(AdvertisingApplyCommand command) {
        AdvertisingStageManifestRow manifest = stageMapper.selectManifest(command.getTaskId());
        guard.validateManifest(command, manifest);
        if (stageMapper.countInvalidPageShapes(command.getTaskId()) != 0) {
            throw new IllegalStateException("advertising stage page proof failed");
        }
        long queryCapacity = Math.subtractExact(
                Math.subtractExact(manifest.getStagedItemCount(), manifest.getDashboardItemCount()),
                command.getActiveCampaigns().size()
        );
        if (queryCapacity < 0L) {
            throw new IllegalStateException("advertising stage has fewer rows than campaigns");
        }
        long batchId = reserve("noon_ad_report_batch", BATCH_INITIAL, 1L);
        Long campaignStart = reserveOptional(
                "noon_ad_campaign_fact",
                CAMPAIGN_INITIAL,
                manifest.getDashboardItemCount()
        );
        Long queryStart = reserveOptional(
                "noon_ad_query_fact", QUERY_INITIAL, queryCapacity
        );
        AdvertisingGenerationSeed seed = new AdvertisingGenerationSeed(
                command, manifest, batchId, campaignStart, queryStart
        );
        if (generationMapper.insertIfAbsent(seed) != 1) {
            throw new IllegalStateException("advertising generation initialization raced");
        }
        requireLiveFence(command);
        return ApplyResult.MORE_WORK;
    }

    private ApplyResult prepareChunk(
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation,
            List<AdvertisingRawStageRow> rows
    ) {
        if (rows.size() > CHUNK_SIZE) {
            throw new IllegalStateException("advertising apply chunk exceeded its bound");
        }
        AdvertisingFactChunk provisional = chunkPreparer.prepare(
                command, generation, rows, Set.of()
        );
        List<String> candidates = identities(provisional);
        Set<String> existing = candidates.isEmpty()
                ? Set.of()
                : new HashSet<>(Objects.requireNonNull(
                        factMapper.selectExistingIdentities(command.getTaskId(), candidates),
                        "existing advertising identities"
                ));
        AdvertisingFactChunk chunk = existing.isEmpty()
                ? provisional
                : chunkPreparer.prepare(command, generation, rows, existing);
        insertFacts(chunk);
        if (generationMapper.advance(command, generation, chunk) != 1) {
            throw new IllegalStateException("advertising generation cursor lost its fence");
        }
        requireLiveFence(command);
        return ApplyResult.MORE_WORK;
    }

    private ApplyResult seal(
            AdvertisingApplyCommand command,
            AdvertisingGenerationRow generation
    ) {
        guard.requireCompleteAccounting(generation);
        if (deleteRawBatch(command.getTaskId())) {
            requireLiveFence(command);
            return ApplyResult.MORE_WORK;
        }
        AdvertisingGenerationHeadRow current = headMapper.selectForUpdate(command);
        if (guard.isNewer(current, command)) return ApplyResult.STALE_FENCE;
        headMapper.upsert(command, generation);
        guard.requireWinningHead(headMapper.selectForUpdate(command), command, generation);
        if (generationMapper.seal(command) != 1) {
            throw new IllegalStateException("advertising generation seal was rejected");
        }
        requireLiveFence(command);
        return ApplyResult.APPLIED;
    }

    private void insertFacts(AdvertisingFactChunk chunk) {
        if (!chunk.getCampaigns().isEmpty()
                && factMapper.insertCampaigns(chunk.getCampaigns())
                        != chunk.getCampaigns().size()) {
            throw new IllegalStateException("advertising campaign chunk count drift");
        }
        if (!chunk.getQueries().isEmpty()
                && factMapper.insertQueries(chunk.getQueries()) != chunk.getQueries().size()) {
            throw new IllegalStateException("advertising query chunk count drift");
        }
    }

    private boolean deletePreparedBatch(long taskId) {
        return factMapper.deleteQueriesBatch(taskId, CHUNK_SIZE) > 0
                || factMapper.deleteCampaignsBatch(taskId, CHUNK_SIZE) > 0;
    }

    private boolean deleteRawBatch(long taskId) {
        if (stageMapper.deleteRawItemsBatch(taskId, CHUNK_SIZE) > 0) return true;
        if (stageMapper.deleteRawPagesBatch(taskId, CHUNK_SIZE) > 0) return true;
        return stageMapper.deleteRawStageIfEmpty(taskId) > 0;
    }

    private AdvertisingGenerationRow adoptFence(
            AdvertisingGenerationRow generation,
            AdvertisingApplyCommand command
    ) {
        if (generation.getActiveFenceEpoch() > command.getFenceEpoch()) {
            throw new IllegalStateException("advertising generation has a future fence");
        }
        if (generation.getActiveFenceEpoch() < command.getFenceEpoch()) {
            if (generationMapper.adoptFence(command.getTaskId(), command.getFenceEpoch()) != 1) {
                throw new IllegalStateException("advertising generation fence adoption failed");
            }
            AdvertisingGenerationRow adopted = generationMapper.selectForUpdate(
                    command.getTaskId()
            );
            guard.requireSameGeneration(adopted, command);
            return adopted;
        }
        return generation;
    }

    private long reserve(String name, long initial, long count) {
        AdvertisingIdBlockCommand block = new AdvertisingIdBlockCommand(name, initial, count);
        idMapper.reserve(block);
        return block.allocatedStart();
    }

    private Long reserveOptional(String name, long initial, long count) {
        return count == 0L ? null : reserve(name, initial, count);
    }

    private List<String> identities(AdvertisingFactChunk chunk) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (AdvertisingGenerationFactRow row : chunk.getCampaigns()) {
            values.add(row.getNormalizedIdentity());
        }
        for (AdvertisingGenerationFactRow row : chunk.getQueries()) {
            values.add(row.getNormalizedIdentity());
        }
        return List.copyOf(values);
    }

    private List<AdvertisingRawStageRow> requireRows(List<AdvertisingRawStageRow> rows) {
        return List.copyOf(Objects.requireNonNull(rows, "advertising raw stage rows"));
    }

    private void requireLiveFence(AdvertisingApplyCommand command) {
        if (stageMapper.countLiveFence(
                command.getTaskId(), command.getFenceEpoch(), command.getLeaseOwner()
        ) != 1) {
            throw new AdvertisingApplyLeaseExpiredException();
        }
    }

    private void requireResetFence(long taskId, long fenceEpoch, String leaseOwner) {
        if (stageMapper.countLiveFence(taskId, fenceEpoch, leaseOwner) != 1) {
            throw new AdvertisingApplyLeaseExpiredException();
        }
    }

    static final class AdvertisingApplyLeaseExpiredException extends RuntimeException {
        private AdvertisingApplyLeaseExpiredException() {
            super("DP06 fact apply lease expired");
        }
    }

    static final class AdvertisingApplyContractException extends RuntimeException {
        private AdvertisingApplyContractException(Throwable cause) {
            super("DP06 fact generation contract failed", cause);
        }
    }
}
