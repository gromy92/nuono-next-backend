package com.nuono.next.competitoranalysis;

import com.nuono.next.competitoranalysis.dp08.Dp08FactWriter;
import com.nuono.next.competitoranalysis.dp08.Dp08ListTarget;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetHandle;
import com.nuono.next.competitoranalysis.dp08.Dp08MemberSetItem;
import com.nuono.next.competitoranalysis.dp08.Dp08TaskMemberProgress;
import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies at most 64 member references inside the caller's Spring transaction. */
final class Dp08ListMemberFactApplier {
    private static final int MEMBER_BATCH_SIZE = 64;

    private final Dp08MemberSetMapper members;
    private final Dp08FactFence fence;
    private final Dp08ListFactSupport factSupport;

    Dp08ListMemberFactApplier(
            Dp08MemberSetMapper members,
            Dp08FactFence fence,
            Dp08ListFactSupport factSupport
    ) {
        this.members = Objects.requireNonNull(members, "members");
        this.fence = Objects.requireNonNull(fence, "fence");
        this.factSupport = Objects.requireNonNull(factSupport, "factSupport");
    }

    Dp08FactWriter.ApplyResult applyFound(
            DataPullTask task,
            Dp08MemberSetHandle handle,
            LocalDate factDate,
            NoonProductDetail detail
    ) {
        Dp08TaskMemberProgress progress = progress(task, handle);
        if (Boolean.TRUE.equals(progress.getApplyComplete())) {
            return Dp08FactWriter.ApplyResult.ALREADY_APPLIED;
        }
        List<Dp08MemberSetItem> page = memberPage(handle, progress);
        boolean more = page.size() > MEMBER_BATCH_SIZE;
        List<Dp08MemberSetItem> selected = page.subList(
                0,
                Math.min(MEMBER_BATCH_SIZE, page.size())
        );
        List<Dp08ListTarget.Reference> references = new ArrayList<>(selected.size());
        for (Dp08MemberSetItem item : selected) {
            references.add(item.reference());
        }
        Dp08ListTarget target = handle.listTarget(factDate, true, references);
        factSupport.recordFound(target, detail, !more);

        long appliedCount = Math.addExact(
                progress.getAppliedMemberCount(),
                selected.size()
        );
        requireExactCompletion(handle, appliedCount, more);
        requireOne(members.advanceTaskApply(
                task.getId(),
                OperationCode.DP08B,
                progress.getVersion(),
                progress.getApplyCursor(),
                progress.getAppliedMemberCount(),
                selected.get(selected.size() - 1).getMemberKey(),
                appliedCount,
                !more,
                0,
                null,
                null
        ));
        fence.requireStillLive(task);
        return more
                ? Dp08FactWriter.ApplyResult.MORE
                : Dp08FactWriter.ApplyResult.APPLIED;
    }

    Dp08FactWriter.ApplyResult applyNotFound(
            DataPullTask task,
            Dp08MemberSetHandle handle,
            LocalDate factDate,
            NoonSearchPage evidence
    ) {
        Dp08TaskMemberProgress progress = progress(task, handle);
        if (Boolean.TRUE.equals(progress.getApplyComplete())) {
            return Dp08FactWriter.ApplyResult.ALREADY_APPLIED;
        }
        if (!Boolean.TRUE.equals(progress.getEvidenceComplete())
                || progress.getEvidenceMemberCount() != handle.getMemberCount()) {
            throw new IllegalStateException("DP08-B not-found evidence is incomplete");
        }
        factSupport.recordNotFound(handle.listProviderTarget(factDate, true), evidence);
        requireOne(members.advanceTaskApply(
                task.getId(),
                OperationCode.DP08B,
                progress.getVersion(),
                progress.getApplyCursor(),
                progress.getAppliedMemberCount(),
                progress.getEvidenceCursor(),
                handle.getMemberCount(),
                true,
                0,
                null,
                null
        ));
        fence.requireStillLive(task);
        return Dp08FactWriter.ApplyResult.APPLIED;
    }

    private Dp08TaskMemberProgress progress(
            DataPullTask task,
            Dp08MemberSetHandle handle
    ) {
        fence.require(task, OperationCode.DP08B);
        members.insertTaskProgress(
                task.getId(),
                OperationCode.DP08B,
                handle.getMemberSetId()
        );
        Dp08TaskMemberProgress progress = Objects.requireNonNull(
                members.lockTaskProgress(task.getId()),
                "DP08 task progress"
        );
        if (progress.getOperationCode() != OperationCode.DP08B
                || !progress.getMemberSetId().equals(handle.getMemberSetId())) {
            throw new IllegalStateException("DP08-B progress identity drift");
        }
        return progress;
    }

    private List<Dp08MemberSetItem> memberPage(
            Dp08MemberSetHandle handle,
            Dp08TaskMemberProgress progress
    ) {
        List<Dp08MemberSetItem> page = List.copyOf(members.listMemberItemsAfter(
                handle.getMemberSetId(),
                progress.getApplyCursor(),
                MEMBER_BATCH_SIZE + 1
        ));
        if (page.isEmpty() || page.size() > MEMBER_BATCH_SIZE + 1) {
            throw new IllegalStateException("DP08-B apply member page is invalid");
        }
        return page;
    }

    private void requireExactCompletion(
            Dp08MemberSetHandle handle,
            long appliedCount,
            boolean more
    ) {
        if (more && appliedCount >= handle.getMemberCount()
                || !more && appliedCount != handle.getMemberCount()) {
            throw new IllegalStateException("DP08-B apply count drift");
        }
    }

    private void requireOne(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("DP08-B apply CAS lost");
        }
    }
}
