package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fenced Adapter for immutable DP-10 generation/pass staging. */
@Service
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Ali1688Dp10MyBatisPageStageStore implements Ali1688Dp10PageStageStore {
    private final Ali1688Dp10StageMapper mapper;
    private final Ali1688Dp10RuntimeMapper runtimeMapper;
    private final Ali1688Dp10StageAssembler assembler;
    private final Ali1688Dp10FingerprintStage fingerprintStage;

    public Ali1688Dp10MyBatisPageStageStore(
            Ali1688Dp10StageMapper mapper,
            Ali1688Dp10RuntimeMapper runtimeMapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.runtimeMapper = Objects.requireNonNull(runtimeMapper, "runtimeMapper");
        this.assembler = new Ali1688Dp10StageAssembler(mapper, objectMapper);
        this.fingerprintStage = new Ali1688Dp10FingerprintStage(mapper);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Optional<Ali1688Dp10StagedPage> load(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        Ali1688Dp10StagePageRow row = mapper.selectPageForUpdate(
                task.getId(), generationNo, scanPass, partition.name(), pageNo);
        if (row == null) return Optional.empty();
        adoptFence(task, row);
        return Optional.of(assembler.assemble(row));
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10StagedPage stageList(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688Dp10ValidatedPage page,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        List<Ali1688Dp10StageItemRow> items = assembler.itemRows(
                task, generationNo, scanPass, page);
        Ali1688Dp10StagePageRow candidate = assembler.pageRow(
                task, generationNo, scanPass, page, items);
        Ali1688Dp10StagePageRow existing = mapper.selectPageForUpdate(
                task.getId(), generationNo, scanPass,
                page.getPartition().name(), page.getPageNo());
        if (existing != null) {
            if (!Objects.equals(existing.getPageFingerprint(), candidate.getPageFingerprint())) {
                throw new Ali1688Dp10PageContractException("DP10_STAGED_PAGE_DRIFT");
            }
            adoptFence(task, existing);
            return assembler.assemble(existing);
        }
        requireOne(mapper.insertPage(candidate), "page insert");
        for (Ali1688Dp10StageItemRow item : items) {
            requireOne(mapper.insertItem(item), "item insert");
        }
        fingerprintStage.stagePage(
                task.getId(), generationNo, scanPass, page.getPartition(), items);
        if (scanPass == 2 && items.stream().noneMatch(this::pending)) {
            requireOne(mapper.markReady(
                    task.getId(), generationNo, page.getPartition().name(),
                    page.getPageNo(), task.getFenceEpoch()), "page ready");
        }
        return assembler.assemble(requirePage(
                task, generationNo, scanPass, page.getPartition(), page.getPageNo()));
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10SealBatch readSealBatch(
            DataPullTask task,
            long generationNo,
            Ali1688HistoricalOrderProvider.Partition partition,
            String afterFingerprint,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        return fingerprintStage.readBatch(
                task.getId(), generationNo, partition, afterFingerprint);
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Optional<Ali1688Dp10PendingItem> nextPendingDetail(
            DataPullTask task,
            long generationNo,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        Ali1688Dp10StageItemRow row = mapper.selectNextPendingForUpdate(
                task.getId(), generationNo);
        if (row == null) return Optional.empty();
        return Optional.of(new Ali1688Dp10PendingItem(
                row.getGenerationNo(), row.getScanPass(), partition(row.getPartitionName()),
                row.getPageNo(), row.getItemOrdinal()));
    }

    @Override
    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10StagedPage recordDetail(
            DataPullTask task,
            Ali1688Dp10PendingItem locator,
            Ali1688Dp10DetailDecision decision,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        Ali1688Dp10StagePageRow page = requirePage(
                task, locator.getGenerationNo(), locator.getScanPass(),
                locator.getPartition(), locator.getPageNo());
        adoptFence(task, page);
        Ali1688Dp10StageItemRow item = mapper.selectItemForUpdate(
                task.getId(), locator.getGenerationNo(), locator.getScanPass(),
                locator.getPartition().name(), locator.getPageNo(), locator.getItemOrdinal());
        if (item == null) throw invalid("DP10_STAGED_ITEM_MISSING");
        if (!Ali1688Dp10ItemState.PENDING_DETAIL.name().equals(item.getState())) {
            return assembler.assemble(page);
        }
        applyDecision(item, Objects.requireNonNull(decision, "decision"));
        requireOne(mapper.completeItem(item), "detail completion");
        if (mapper.countPendingOnPage(
                task.getId(), locator.getGenerationNo(), locator.getPartition().name(),
                locator.getPageNo()) == 0) {
            requireOne(mapper.markReady(
                    task.getId(), locator.getGenerationNo(), locator.getPartition().name(),
                    locator.getPageNo(), task.getFenceEpoch()), "page ready");
        }
        return assembler.assemble(requirePage(
                task, locator.getGenerationNo(), locator.getScanPass(),
                locator.getPartition(), locator.getPageNo()));
    }

    private void applyDecision(Ali1688Dp10StageItemRow item, Ali1688Dp10DetailDecision decision) {
        Ali1688Dp10ItemState state = decision.getState();
        if (state == Ali1688Dp10ItemState.PENDING_DETAIL
                || state == Ali1688Dp10ItemState.SKIP_LATER_IDENTITY_CONFLICT) {
            throw new IllegalArgumentException("invalid DP-10 detail decision");
        }
        if (state == Ali1688Dp10ItemState.COMPLETE
                && !Objects.equals(item.getProviderOrderNo(),
                        decision.getOrder().getProviderOrderNo())) {
            throw invalid("DP10_DETAIL_IDENTITY_MISMATCH");
        }
        assembler.applyDetailPayload(
                item, state, decision.getSanitizedCode(), decision.getOrder());
    }

    private void requireFence(DataPullTask task, LocalDateTime nowUtc) {
        if (task == null || task.getId() == null || task.getFenceEpoch() == null) {
            throw new IllegalStateException("DP10_TASK_FENCE_STALE");
        }
        Ali1688Dp10FenceGuard.requireLive(task, runtimeMapper.lockTask(task.getId()), nowUtc);
    }

    private void adoptFence(DataPullTask task, Ali1688Dp10StagePageRow row) {
        if (row.getActiveFenceEpoch() == null || row.getActiveFenceEpoch() < 1L
                || row.getActiveFenceEpoch() > task.getFenceEpoch()) {
            throw new IllegalStateException("DP10_STAGE_FENCE_STALE");
        }
        if (row.getActiveFenceEpoch() < task.getFenceEpoch()) {
            requireOne(mapper.adoptFence(
                    task.getId(), row.getGenerationNo(), row.getScanPass(),
                    row.getPartitionName(), row.getPageNo(), task.getFenceEpoch()),
                    "fence adoption");
        }
    }

    private Ali1688Dp10StagePageRow requirePage(
            DataPullTask task,
            long generationNo,
            int scanPass,
            Ali1688HistoricalOrderProvider.Partition partition,
            int pageNo
    ) {
        Ali1688Dp10StagePageRow row = mapper.selectPageForUpdate(
                task.getId(), generationNo, scanPass, partition.name(), pageNo);
        if (row == null) throw invalid("DP10_STAGED_PAGE_MISSING");
        return row;
    }

    private boolean pending(Ali1688Dp10StageItemRow item) {
        return Ali1688Dp10ItemState.PENDING_DETAIL.name().equals(item.getState());
    }

    private Ali1688HistoricalOrderProvider.Partition partition(String name) {
        return Ali1688HistoricalOrderProvider.Partition.valueOf(name);
    }

    private void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException("DP10 " + action + " must affect one row");
    }

    private Ali1688Dp10PageContractException invalid(String code) {
        return new Ali1688Dp10PageContractException(code);
    }
}
