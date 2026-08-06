package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.schedule.ScheduleSourceReadContext;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Owns fenced writes to the persistent DP08 nested-scan stage. */
final class Dp08MemberStageWriter {
    private final Dp08MemberSetMapper mapper;

    Dp08MemberStageWriter(Dp08MemberSetMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    Dp08MemberStageHead lockHead(
            ScheduleSourceReadContext context,
            String scopeKey
    ) {
        return mapper.lockStageHead(
                context.getOperationCode(),
                context.getEpochNo(),
                context.getScanPass(),
                scopeKey
        );
    }

    Snapshot snapshot(
            ScheduleSourceReadContext context,
            String scopeKey,
            String expectedBasePayload
    ) {
        Snapshot snapshot = new Snapshot(lockHead(context, scopeKey));
        snapshot.requireCompatible(expectedBasePayload);
        return snapshot;
    }

    void persistAdvance(
            ScheduleSourceReadContext context,
            String scopeKey,
            Snapshot previous,
            String nextCursor,
            long nextCount,
            String nextDigest,
            String basePayload,
            LocalDateTime effectiveFromUtc,
            List<Dp08MemberSetItem> members
    ) {
        if (previous.newStage()) {
            requireOne(mapper.insertStageHead(newHead(
                    context,
                    scopeKey,
                    nextCursor,
                    nextCount,
                    nextDigest,
                    basePayload,
                    effectiveFromUtc
            )), "stage head insert");
        }
        if (context.getPass() == ScheduleSourceReadContext.Pass.ONE) {
            insertStage(context, scopeKey, members);
        }
        if (!previous.newStage()) {
            requireOne(mapper.advanceStageHead(
                    context.getOperationCode(),
                    context.getEpochNo(),
                    context.getScanPass(),
                    scopeKey,
                    previous.version(),
                    previous.sourceCursor(),
                    previous.memberCount(),
                    previous.memberOrderedSha256(),
                    nextCursor,
                    nextCount,
                    nextDigest,
                    effectiveFromUtc,
                    "SCANNING",
                    null
            ), "stage head advance");
        }
    }

    void finalizeIdentity(
            ScheduleSourceReadContext context,
            String scopeKey,
            String nextCursor,
            long nextCount,
            String nextDigest,
            LocalDateTime effectiveFromUtc,
            String memberSetId
    ) {
        Dp08MemberStageHead locked = Objects.requireNonNull(
                lockHead(context, scopeKey),
                "DP08 stage head"
        );
        requireOne(mapper.advanceStageHead(
                context.getOperationCode(),
                context.getEpochNo(),
                context.getScanPass(),
                scopeKey,
                locked.getVersion(),
                locked.getSourceCursor(),
                locked.getMemberCount(),
                locked.getMemberOrderedSha256(),
                nextCursor,
                nextCount,
                nextDigest,
                effectiveFromUtc,
                finalState(context),
                memberSetId
        ), "stage finalization identity");
    }

    void finishExisting(
            ScheduleSourceReadContext context,
            Dp08MemberStageHead head,
            String memberSetId
    ) {
        requireOne(mapper.advanceStageHead(
                context.getOperationCode(),
                context.getEpochNo(),
                context.getScanPass(),
                head.getScopeKey(),
                head.getVersion(),
                head.getSourceCursor(),
                head.getMemberCount(),
                head.getMemberOrderedSha256(),
                head.getSourceCursor(),
                head.getMemberCount(),
                head.getMemberOrderedSha256(),
                head.getEffectiveFromUtc(),
                finalState(context),
                memberSetId
        ), "stage boundary finalization");
    }

    private void insertStage(
            ScheduleSourceReadContext context,
            String scopeKey,
            List<Dp08MemberSetItem> members
    ) {
        List<Dp08MemberStageItem> rows = new ArrayList<>(members.size());
        for (Dp08MemberSetItem item : members) {
            rows.add(Dp08MemberStageItem.from(
                    context.getOperationCode(),
                    context.getEpochNo(),
                    context.getScanPass(),
                    scopeKey,
                    item
            ));
        }
        if (mapper.insertStageItems(rows) != rows.size()) {
            throw new IllegalStateException("DP08 stage insert count drift");
        }
    }

    private Dp08MemberStageHead newHead(
            ScheduleSourceReadContext context,
            String scopeKey,
            String cursor,
            long memberCount,
            String digest,
            String basePayload,
            LocalDateTime effectiveFromUtc
    ) {
        Dp08MemberStageHead head = new Dp08MemberStageHead();
        head.setOperationCode(context.getOperationCode());
        head.setEpochNo(context.getEpochNo());
        head.setScanPass(context.getScanPass());
        head.setScopeKey(scopeKey);
        head.setSourceCursor(cursor);
        head.setMemberCount(memberCount);
        head.setMemberOrderedSha256(digest);
        head.setBasePayload(basePayload);
        head.setEffectiveFromUtc(effectiveFromUtc);
        head.setStageState("SCANNING");
        return head;
    }

    private String finalState(ScheduleSourceReadContext context) {
        return context.getPass() == ScheduleSourceReadContext.Pass.ONE
                ? "FINALIZING"
                : "EMITTED";
    }

    private void requireOne(int changed, String action) {
        if (changed != 1) {
            throw new IllegalStateException(action + " must affect one row");
        }
    }

    static final class Snapshot {
        private final boolean newStage;
        private final long memberCount;
        private final String memberOrderedSha256;
        private final String sourceCursor;
        private final long version;
        private final LocalDateTime effectiveFromUtc;
        private final String stageState;
        private final String basePayload;

        private Snapshot(Dp08MemberStageHead head) {
            this.newStage = head == null;
            this.memberCount = head == null
                    ? 0L
                    : required(head.getMemberCount(), "memberCount");
            this.memberOrderedSha256 = head == null
                    ? Dp08MemberOrderedDigest.initial().snapshot()
                    : head.getMemberOrderedSha256();
            this.sourceCursor = head == null ? null : head.getSourceCursor();
            this.version = head == null ? 0L : required(head.getVersion(), "version");
            this.effectiveFromUtc = head == null ? null : head.getEffectiveFromUtc();
            this.stageState = head == null ? null : head.getStageState();
            this.basePayload = head == null ? null : head.getBasePayload();
        }

        void requireCompatible(String expectedBasePayload) {
            if (!newStage && (!"SCANNING".equals(stageState)
                    || !expectedBasePayload.equals(basePayload))) {
                throw new IllegalStateException("DP08 stage head drift");
            }
        }

        boolean newStage() {
            return newStage;
        }

        long memberCount() {
            return memberCount;
        }

        String memberOrderedSha256() {
            return memberOrderedSha256;
        }

        String sourceCursor() {
            return sourceCursor;
        }

        long version() {
            return version;
        }

        LocalDateTime effectiveFromUtc() {
            return effectiveFromUtc;
        }

        private static long required(Long value, String field) {
            if (value == null || value < 0L) {
                throw new IllegalStateException("invalid " + field);
            }
            return value;
        }
    }
}
