package com.nuono.next.procurement.aliorder.datapull;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10ApplyStageMapper;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderFactPreflight;
import com.nuono.next.procurement.aliorder.Ali1688HistoricalOrderProvider;
import com.nuono.next.procurement.aliorder.Ali1688PaginationMath;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Verifies one page envelope or one item per call; never accumulates the batch in memory. */
public final class Ali1688Dp10BatchVerifier {
    public enum Advance { PROGRESSED, COMPLETE }

    private static final EnumSet<Ali1688Dp10ItemState> TERMINAL_STATES = EnumSet.of(
            Ali1688Dp10ItemState.COMPLETE,
            Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM,
            Ali1688Dp10ItemState.SKIP_NOT_FOUND,
            Ali1688Dp10ItemState.SKIP_LATER_IDENTITY_CONFLICT
    );
    private final Ali1688Dp10ApplyStageMapper mapper;
    private final Ali1688Dp10StageAssembler assembler;
    private final Ali1688HistoricalOrderFactPreflight factPreflight =
            new Ali1688HistoricalOrderFactPreflight();

    Ali1688Dp10BatchVerifier(
            Ali1688Dp10ApplyStageMapper mapper,
            Ali1688Dp10StageAssembler assembler
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.assembler = Objects.requireNonNull(assembler, "assembler");
    }

    Advance verifyNext(DataPullTask task, Ali1688Dp10ApplyCommand command) {
        Ali1688Dp10StagePageRow page = mapper.selectNextVerificationPageForUpdate(
                task.getId(), command.getGenerationNo());
        if (page == null) {
            int expected = Math.addExact(
                    command.getCurrentExpectedPages(), command.getHistoryExpectedPages());
            if (mapper.countPassTwoPages(task.getId(), command.getGenerationNo()) != expected
                    || mapper.countUnverifiedPages(task.getId(), command.getGenerationNo()) != 0) {
                throw invalid("DP10_STAGE_PAGE_COUNT_INVALID");
            }
            return Advance.COMPLETE;
        }
        if ("READY".equals(page.getState())) {
            verifyPageEnvelope(task, command, page);
            requireOne(mapper.markPageVerifying(
                    task.getId(), command.getGenerationNo(), page.getPartitionName(),
                    page.getPageNo(), task.getFenceEpoch()), "page verify start");
            return Advance.PROGRESSED;
        }
        if (!"VERIFYING".equals(page.getState())) {
            throw invalid("DP10_STAGE_PAGE_STATE_INVALID");
        }
        Ali1688Dp10StageItemRow item = mapper.selectNextVerificationItemForUpdate(
                task.getId(), command.getGenerationNo(), page.getPartitionName(), page.getPageNo());
        if (item != null) {
            verifyOneItem(task, command, page, item);
            return Advance.PROGRESSED;
        }
        if (mapper.countUnverifiedItemsOnPage(
                task.getId(), command.getGenerationNo(),
                page.getPartitionName(), page.getPageNo()) != 0) {
            throw invalid("DP10_STAGE_ITEM_VERIFICATION_INCOMPLETE");
        }
        requireOne(mapper.markPageVerified(
                task.getId(), command.getGenerationNo(), page.getPartitionName(),
                page.getPageNo(), task.getFenceEpoch()), "page verified");
        return Advance.PROGRESSED;
    }

    private void verifyPageEnvelope(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            Ali1688Dp10StagePageRow page
    ) {
        Ali1688HistoricalOrderProvider.Partition partition = partition(page.getPartitionName());
        long total = partition == Ali1688HistoricalOrderProvider.Partition.CURRENT
                ? command.getCurrentExpectedTotal() : command.getHistoryExpectedTotal();
        int pages = partition == Ali1688HistoricalOrderProvider.Partition.CURRENT
                ? command.getCurrentExpectedPages() : command.getHistoryExpectedPages();
        int pageNo = positive(page.getPageNo(), "DP10_STAGE_PAGE_NO_INVALID");
        int pageSize = positive(page.getPageSize(), "DP10_STAGE_PAGE_SIZE_INVALID");
        if (!Ali1688Dp10ListPageContract.isSupported(pageSize)) {
            throw invalid("DP10_STAGE_PAGE_SIZE_INVALID");
        }
        if (!Objects.equals(page.getTaskId(), task.getId())
                || !Objects.equals(page.getGenerationNo(), command.getGenerationNo())
                || !Objects.equals(page.getScanPass(), 2)
                || !Objects.equals(page.getTotalRecord(), total)
                || !Objects.equals(page.getExpectedPages(), pages)
                || pages != Ali1688PaginationMath.expectedPages(total, pageSize)
                || pageNo > pages) {
            throw invalid("DP10_STAGE_PAGE_CONTRACT_INVALID");
        }
        List<Ali1688Dp10StageItemRow> items = mapper.selectPageItemsForUpdate(
                task.getId(), command.getGenerationNo(), partition.name(), pageNo);
        int expectedRows = Ali1688PaginationMath.expectedRowsOnPage(
                total, pageNo, pageSize, pages);
        if (items == null || items.size() != expectedRows
                || !Objects.equals(page.getRawRowCount(), expectedRows)) {
            throw invalid("DP10_STAGE_RAW_ROW_COUNT_INVALID");
        }
        int ordinal = 0;
        for (Ali1688Dp10StageItemRow item : items) {
            if (!Objects.equals(item.getTaskId(), task.getId())
                    || !Objects.equals(item.getGenerationNo(), command.getGenerationNo())
                    || !Objects.equals(item.getScanPass(), 2)
                    || !Objects.equals(item.getPartitionName(), partition.name())
                    || !Objects.equals(item.getPageNo(), pageNo)
                    || !Objects.equals(item.getItemOrdinal(), ordinal++)
                    || !"PENDING".equals(item.getVerificationState())
                    || !"BLOCKED".equals(item.getApplyState())) {
                throw invalid("DP10_STAGE_ITEM_STATE_INVALID");
            }
            requireTerminalState(item.getState());
        }
        assembler.verifyRawFingerprint(page, items);
    }

    private void verifyOneItem(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            Ali1688Dp10StagePageRow page,
            Ali1688Dp10StageItemRow item
    ) {
        requireTerminalState(item.getState());
        item.setApplyState("SKIPPED");
        if (Ali1688Dp10ItemState.COMPLETE.name().equals(item.getState())) {
            Ali1688HistoricalOrderProvider.OrderSnapshot order = assembler.decodeComplete(item);
            Ali1688HistoricalOrderFactPreflight.Decision fact = factPreflight.inspectFact(order);
            if (!fact.isAccepted()) {
                item.setState(Ali1688Dp10ItemState.SKIP_BUSINESS_ITEM.name());
                item.setValidationCode(fact.getSanitizedCode());
            } else if (claimsIdentity(task, command, item)) {
                item.setApplyState("READY");
            } else {
                item.setState(Ali1688Dp10ItemState.SKIP_LATER_IDENTITY_CONFLICT.name());
                item.setValidationCode("DP10_LATER_IDENTITY_CONFLICT");
            }
        }
        requireOne(mapper.completeVerification(item), "item verification");
    }

    private boolean claimsIdentity(
            DataPullTask task,
            Ali1688Dp10ApplyCommand command,
            Ali1688Dp10StageItemRow item
    ) {
        int inserted = mapper.insertIdentityIfAbsent(
                task.getId(), command.getGenerationNo(), item.getProviderOrderNo(),
                pagePartition(item), item.getPageNo(), item.getItemOrdinal(), task.getFenceEpoch());
        if (inserted == 1) return true;
        if (inserted != 0) throw invalid("DP10_STAGE_IDENTITY_WRITE_INVALID");
        return mapper.countIdentityOwner(
                task.getId(), command.getGenerationNo(), item.getProviderOrderNo(),
                pagePartition(item), item.getPageNo(), item.getItemOrdinal()) == 1;
    }

    private String pagePartition(Ali1688Dp10StageItemRow item) {
        return partition(item.getPartitionName()).name();
    }

    private Ali1688HistoricalOrderProvider.Partition partition(String value) {
        try { return Ali1688HistoricalOrderProvider.Partition.valueOf(value); }
        catch (RuntimeException invalid) { throw invalid("DP10_STAGE_PARTITION_INVALID"); }
    }

    private Ali1688Dp10ItemState requireTerminalState(String value) {
        try {
            Ali1688Dp10ItemState state = Ali1688Dp10ItemState.valueOf(value);
            if (TERMINAL_STATES.contains(state)) return state;
        } catch (RuntimeException ignored) {
            // Use the same sanitized contract failure.
        }
        throw invalid("DP10_STAGE_ITEM_STATE_INVALID");
    }

    private int positive(Integer value, String code) {
        if (value == null || value < 1) throw invalid(code);
        return value;
    }

    private void requireOne(int changed, String action) {
        if (changed != 1) throw new IllegalStateException("DP10 " + action + " must affect one row");
    }

    private Ali1688Dp10PageContractException invalid(String code) {
        return new Ali1688Dp10PageContractException(code);
    }
}
