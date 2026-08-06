package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.schedule.ScheduleSourceReadContext;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/** Seals one bounded nested-scan batch into a restart-safe member-set transition. */
final class Dp08MemberSetScheduleStore {
    static final int MEMBER_BATCH_SIZE = 64;

    private final Dp08MemberSetHandleCodec codec;
    private final Dp08MemberStageWriter stageWriter;
    private final Dp08MemberSetFinalizer finalizer;

    Dp08MemberSetScheduleStore(
            Dp08MemberSetMapper mapper,
            ObjectMapper objectMapper
    ) {
        Dp08MemberSetMapper requiredMapper = Objects.requireNonNull(mapper, "mapper");
        this.codec = new Dp08MemberSetHandleCodec(objectMapper);
        this.stageWriter = new Dp08MemberStageWriter(requiredMapper);
        this.finalizer = new Dp08MemberSetFinalizer(requiredMapper, codec);
    }

    Dp08MemberStageHead lockHead(
            ScheduleSourceReadContext context,
            String scopeKey
    ) {
        return stageWriter.lockHead(context, scopeKey);
    }

    Dp08MemberSetScheduleResult completeSmall(
            ScheduleSourceReadContext context,
            Dp08MemberSetBase base,
            String logicalCursor,
            String nextCursor,
            List<Dp08MemberSetItem> members
    ) {
        requireBatch(members, "small member set");
        Dp08MemberOrderedDigest digest = Dp08MemberOrderedDigest.initial();
        LocalDateTime effectiveFromUtc = null;
        for (Dp08MemberSetItem item : members) {
            digest.append(item);
            effectiveFromUtc = latest(effectiveFromUtc, item.getSourceUpdatedAtUtc());
        }
        effectiveFromUtc = utc(effectiveFromUtc);
        Dp08MemberSetHandle handle = codec.seal(
                base,
                members.size(),
                digest.snapshot(),
                effectiveFromUtc
        );
        if (context.getPass() == ScheduleSourceReadContext.Pass.ONE) {
            finalizer.ensureSmallSet(handle, effectiveFromUtc, members);
        }
        return Dp08MemberSetScheduleResult.complete(
                finalizer.scope(handle, logicalCursor, effectiveFromUtc),
                nextCursor
        );
    }

    Dp08MemberSetScheduleResult append(
            ScheduleSourceReadContext context,
            Dp08MemberSetBase base,
            String logicalCursor,
            String nextCursor,
            List<Dp08MemberSetItem> members,
            boolean sourceComplete
    ) {
        requireBatch(members, "staged member batch");
        String scopeKey = base.stableScopeKey();
        String basePayload = codec.encodeBase(base);
        Dp08MemberStageWriter.Snapshot previous = stageWriter.snapshot(
                context,
                scopeKey,
                basePayload
        );
        Dp08MemberOrderedDigest digest = Dp08MemberOrderedDigest.resume(
                previous.memberOrderedSha256()
        );
        LocalDateTime effectiveFromUtc = previous.effectiveFromUtc();
        for (Dp08MemberSetItem item : members) {
            digest.append(item);
            effectiveFromUtc = latest(effectiveFromUtc, item.getSourceUpdatedAtUtc());
        }
        effectiveFromUtc = utc(effectiveFromUtc);
        long nextCount = Math.addExact(previous.memberCount(), members.size());

        stageWriter.persistAdvance(
                context,
                scopeKey,
                previous,
                nextCursor,
                nextCount,
                digest.snapshot(),
                basePayload,
                effectiveFromUtc,
                members
        );
        if (!sourceComplete) {
            return Dp08MemberSetScheduleResult.progress(nextCursor);
        }
        Dp08MemberSetHandle handle = codec.seal(
                base,
                nextCount,
                digest.snapshot(),
                effectiveFromUtc
        );
        stageWriter.finalizeIdentity(
                context,
                scopeKey,
                nextCursor,
                nextCount,
                digest.snapshot(),
                effectiveFromUtc,
                handle.getMemberSetId()
        );
        if (context.getPass() == ScheduleSourceReadContext.Pass.TWO) {
            return Dp08MemberSetScheduleResult.complete(
                    finalizer.scope(handle, logicalCursor, effectiveFromUtc),
                    finalizer.emittedCursor(nextCursor, handle)
            );
        }
        finalizer.ensureSetHeader(handle, effectiveFromUtc);
        return finalizer.copyAndMaybeEmit(
                context,
                lockHead(context, scopeKey),
                handle,
                logicalCursor
        );
    }

    Dp08MemberSetScheduleResult resumeFinalization(
            ScheduleSourceReadContext context,
            Dp08MemberStageHead head,
            String logicalCursor
    ) {
        if (!"FINALIZING".equals(head.getStageState())
                || head.getMemberSetId() == null) {
            throw new IllegalStateException("DP08 stage is not finalizing");
        }
        return finalizer.copyAndMaybeEmit(
                context,
                head,
                finalizer.handle(head),
                logicalCursor
        );
    }

    Dp08MemberSetScheduleResult finishExisting(
            ScheduleSourceReadContext context,
            Dp08MemberStageHead head,
            String logicalCursor
    ) {
        if (!"SCANNING".equals(head.getStageState()) || head.getMemberCount() < 1) {
            throw new IllegalStateException("DP08 incomplete stage cannot be finished");
        }
        Dp08MemberSetHandle handle = codec.seal(
                codec.decodeBase(head.getBasePayload()),
                head.getMemberCount(),
                head.getMemberOrderedSha256(),
                head.getEffectiveFromUtc()
        );
        stageWriter.finishExisting(context, head, handle.getMemberSetId());
        if (context.getPass() == ScheduleSourceReadContext.Pass.TWO) {
            return Dp08MemberSetScheduleResult.complete(
                    finalizer.scope(handle, logicalCursor, head.getEffectiveFromUtc()),
                    finalizer.emittedCursor(head.getSourceCursor(), handle)
            );
        }
        finalizer.ensureSetHeader(handle, head.getEffectiveFromUtc());
        return finalizer.copyAndMaybeEmit(
                context,
                lockHead(context, head.getScopeKey()),
                handle,
                logicalCursor
        );
    }

    Dp08MemberSetBase base(Dp08MemberStageHead head) {
        return codec.decodeBase(head.getBasePayload());
    }

    private void requireBatch(List<Dp08MemberSetItem> members, String action) {
        if (members.isEmpty() || members.size() > MEMBER_BATCH_SIZE) {
            throw new IllegalArgumentException("invalid DP08 " + action);
        }
    }

    private LocalDateTime latest(LocalDateTime left, LocalDateTime right) {
        return left == null ? right : right != null && right.isAfter(left) ? right : left;
    }

    private LocalDateTime utc(LocalDateTime value) {
        return Objects.requireNonNull(value, "effectiveFromUtc")
                .truncatedTo(ChronoUnit.MILLIS);
    }
}
