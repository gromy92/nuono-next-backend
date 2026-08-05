package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.orchestration.ConditionalOnDataPullExecutionMode;
import com.nuono.next.datapull.orchestration.DataPullExecutionMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.orchestration.DataPullRuntimeProperties;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10ApplyStageMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10RuntimeMapper;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10StageMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deep bounded Adapter for per-page/per-item verification and persistent fact locators. */
@Service
@Profile("local-db")
@ConditionalOnDataPullExecutionMode(DataPullExecutionMode.RUNTIME)
public class Ali1688Dp10BoundedStageStore {
    private static final Pattern VALIDATION_CODE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}");
    private final Ali1688Dp10ApplyStageMapper mapper;
    private final Ali1688Dp10RuntimeMapper runtimeMapper;
    private final Ali1688Dp10StageAssembler assembler;
    private final Ali1688Dp10BatchVerifier verifier;

    public Ali1688Dp10BoundedStageStore(
            Ali1688Dp10ApplyStageMapper mapper,
            Ali1688Dp10StageMapper stageMapper,
            Ali1688Dp10RuntimeMapper runtimeMapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.runtimeMapper = Objects.requireNonNull(runtimeMapper, "runtimeMapper");
        this.assembler = new Ali1688Dp10StageAssembler(stageMapper, objectMapper);
        this.verifier = new Ali1688Dp10BatchVerifier(mapper, assembler);
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Ali1688Dp10BatchVerifier.Advance verifyNext(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        return verifier.verifyNext(task, command);
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public Optional<Ali1688Dp10ApplySlice> nextApplySlice(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        if (mapper.countUnverifiedPages(task.getId(), command.getGenerationNo()) != 0) {
            throw new IllegalStateException("DP10_STAGE_VERIFICATION_INCOMPLETE");
        }
        Ali1688Dp10StageItemRow row = mapper.selectNextApplyItemForUpdate(
                task.getId(), command.getGenerationNo());
        if (row == null) return Optional.empty();
        int cursor = row.getApplyItemCursor() == null ? -1 : row.getApplyItemCursor();
        var order = assembler.decodeComplete(row);
        if (cursor < 0 || cursor >= order.getItems().size()
                || !"VERIFIED".equals(row.getVerificationState())) {
            throw new Ali1688Dp10PageContractException("DP10_APPLY_CURSOR_INVALID");
        }
        return Optional.of(new Ali1688Dp10ApplySlice(
                command.getGenerationNo(), row.getPartitionName(), row.getPageNo(),
                row.getItemOrdinal(), cursor, order));
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public void recordAppliedSegment(
            DataPullTask task,
            Ali1688Dp10ApplySlice slice,
            int nextCursor,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        int itemCount = slice.getOrder().getItems().size();
        if (nextCursor <= slice.getItemCursor() || nextCursor > itemCount) {
            throw new IllegalArgumentException("invalid DP-10 applied segment cursor");
        }
        int changed = nextCursor == itemCount
                ? mapper.markItemApplied(
                        task.getId(), slice.getGenerationNo(), slice.getPartition(),
                        slice.getPageNo(), slice.getItemOrdinal(), slice.getItemCursor(), nextCursor)
                : mapper.advanceApplyCursor(
                        task.getId(), slice.getGenerationNo(), slice.getPartition(),
                        slice.getPageNo(), slice.getItemOrdinal(), slice.getItemCursor(), nextCursor);
        requireOne(changed, "apply cursor");
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public void recordBusinessSkip(
            DataPullTask task,
            Ali1688Dp10ApplySlice slice,
            String validationCode,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        if (slice == null || validationCode == null
                || !VALIDATION_CODE.matcher(validationCode).matches()) {
            throw new IllegalArgumentException("invalid DP-10 business skip");
        }
        requireOne(mapper.markBusinessSkipped(task, slice, validationCode),
                "business skip");
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public boolean markNextPageApplied(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        Ali1688Dp10StagePageRow page = mapper.selectNextVerifiedPageForUpdate(
                task.getId(), command.getGenerationNo());
        if (page == null) return false;
        if (mapper.countUnappliedItemsOnPage(
                task.getId(), command.getGenerationNo(),
                page.getPartitionName(), page.getPageNo()) != 0) {
            throw new IllegalStateException("DP10_STAGE_PAGE_APPLY_INCOMPLETE");
        }
        requireOne(mapper.markPageApplied(
                task.getId(), command.getGenerationNo(), page.getPartitionName(),
                page.getPageNo(), task.getFenceEpoch()), "page applied");
        return true;
    }

    @Transactional(timeout = DataPullRuntimeProperties.DATABASE_TRANSACTION_TIMEOUT_SECONDS)
    public boolean allApplied(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            LocalDateTime nowUtc
    ) {
        requireFence(task, nowUtc);
        int expectedPages = Math.addExact(
                command.getCurrentExpectedPages(), command.getHistoryExpectedPages());
        return mapper.countPassTwoPages(task.getId(), command.getGenerationNo()) == expectedPages
                && mapper.countUnappliedPages(task.getId(), command.getGenerationNo()) == 0;
    }

    private void requireFence(DataPullTask task, LocalDateTime nowUtc) {
        if (task == null || task.getId() == null || task.getFenceEpoch() == null) {
            throw new IllegalStateException("DP10_TASK_FENCE_STALE");
        }
        Ali1688Dp10FenceGuard.requireLive(task, runtimeMapper.lockTask(task.getId()), nowUtc);
    }

    private void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException("DP10 " + action + " must affect one row");
    }
}
