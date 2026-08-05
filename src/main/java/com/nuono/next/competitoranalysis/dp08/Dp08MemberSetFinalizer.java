package com.nuono.next.competitoranalysis.dp08;

import com.nuono.next.datapull.schedule.ScheduleSourceReadContext;
import com.nuono.next.datapull.schedule.ScheduleSourceScope;
import com.nuono.next.datapull.scope.DataPullScopeBindingCandidate;
import com.nuono.next.infrastructure.mapper.Dp08MemberSetMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Copies staged members into a content-addressed set and emits its temporal handle. */
final class Dp08MemberSetFinalizer {
    private final Dp08MemberSetMapper mapper;
    private final Dp08MemberSetHandleCodec codec;

    Dp08MemberSetFinalizer(
            Dp08MemberSetMapper mapper,
            Dp08MemberSetHandleCodec codec
    ) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    Dp08MemberSetScheduleResult copyAndMaybeEmit(
            ScheduleSourceReadContext context,
            Dp08MemberStageHead head,
            Dp08MemberSetHandle handle,
            String logicalCursor
    ) {
        Dp08MemberSetRecord memberSet = requireMemberSet(handle.getMemberSetId());
        verify(memberSet, handle, head.getEffectiveFromUtc());
        if (!"SEALED".equals(memberSet.getSetState())) {
            Dp08MemberSetScheduleResult progress = copyOnePage(
                    context,
                    head,
                    handle,
                    memberSet
            );
            if (progress != null) {
                return progress;
            }
        }
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
                "EMITTED",
                handle.getMemberSetId()
        ), "stage emit CAS");
        return Dp08MemberSetScheduleResult.complete(
                scope(handle, logicalCursor, head.getEffectiveFromUtc()),
                emittedCursor(head.getSourceCursor(), handle)
        );
    }

    void ensureSmallSet(
            Dp08MemberSetHandle handle,
            LocalDateTime effectiveFromUtc,
            List<Dp08MemberSetItem> members
    ) {
        ensureSetHeader(handle, effectiveFromUtc);
        Dp08MemberSetRecord memberSet = requireMemberSet(handle.getMemberSetId());
        verify(memberSet, handle, effectiveFromUtc);
        if ("SEALED".equals(memberSet.getSetState())) {
            return;
        }
        if (memberSet.getCopiedMemberCount() != 0L
                || memberSet.getCopyCursor() != null) {
            throw new IllegalStateException("small DP08 member set is partially copied");
        }
        for (Dp08MemberSetItem item : members) {
            item.setMemberSetId(handle.getMemberSetId());
        }
        mapper.insertMemberItems(members);
        requireOne(mapper.advanceMemberSet(
                memberSet.getMemberSetId(),
                memberSet.getVersion(),
                null,
                0L,
                members.get(members.size() - 1).getMemberKey(),
                members.size()
        ), "small member set seal");
    }

    void ensureSetHeader(
            Dp08MemberSetHandle handle,
            LocalDateTime effectiveFromUtc
    ) {
        String payload = codec.encode(handle);
        Dp08MemberSetRecord row = new Dp08MemberSetRecord();
        row.setMemberSetId(handle.getMemberSetId());
        row.setOperationCode(handle.getOperationCode());
        row.setScopeKey(handle.getStableScopeKey());
        row.setMemberCount(handle.getMemberCount());
        row.setMemberOrderedSha256(handle.getMemberOrderedSha256());
        row.setHandlePayloadType(type(handle));
        row.setHandlePayload(payload);
        row.setHandlePayloadSha256(sha256(payload));
        row.setEffectiveFromUtc(effectiveFromUtc);
        mapper.insertMemberSet(row);
    }

    Dp08MemberSetHandle handle(Dp08MemberStageHead head) {
        return new Dp08MemberSetHandle(
                codec.decodeBase(head.getBasePayload()),
                head.getMemberSetId(),
                head.getMemberCount(),
                head.getMemberOrderedSha256()
        );
    }

    ScheduleSourceScope scope(
            Dp08MemberSetHandle handle,
            String nativeCursor,
            LocalDateTime effectiveFromUtc
    ) {
        String payload = codec.encode(handle);
        return ScheduleSourceScope.bound(
                nativeCursor,
                handle.toDataPullScope(),
                new DataPullScopeBindingCandidate(
                        handle.getOperationCode(),
                        handle.getStableScopeKey(),
                        type(handle),
                        payload,
                        effectiveFromUtc
                )
        );
    }

    String emittedCursor(String nativeCursor, Dp08MemberSetHandle handle) {
        return Dp08MemberSourceCursor.resume(
                nativeCursor,
                "EMIT:" + handle.getMemberSetId()
        );
    }

    private Dp08MemberSetScheduleResult copyOnePage(
            ScheduleSourceReadContext context,
            Dp08MemberStageHead head,
            Dp08MemberSetHandle handle,
            Dp08MemberSetRecord memberSet
    ) {
        List<Dp08MemberSetItem> page = List.copyOf(mapper.listStageItemsAfter(
                context.getOperationCode(),
                context.getEpochNo(),
                context.getScanPass(),
                head.getScopeKey(),
                memberSet.getCopyCursor(),
                Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE + 1
        ));
        if (page.isEmpty()
                || page.size() > Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE + 1) {
            throw new IllegalStateException("DP08 stage copy page is invalid");
        }
        boolean more = page.size() > Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE;
        List<Dp08MemberSetItem> selected = page.subList(
                0,
                Math.min(page.size(), Dp08MemberSetScheduleStore.MEMBER_BATCH_SIZE)
        );
        for (Dp08MemberSetItem item : selected) {
            item.setMemberSetId(handle.getMemberSetId());
        }
        mapper.insertMemberItems(selected);
        long copiedCount = Math.addExact(
                memberSet.getCopiedMemberCount(),
                selected.size()
        );
        if (more && copiedCount >= memberSet.getMemberCount()) {
            throw new IllegalStateException("DP08 member copy overflow");
        }
        requireOne(mapper.advanceMemberSet(
                memberSet.getMemberSetId(),
                memberSet.getVersion(),
                memberSet.getCopyCursor(),
                memberSet.getCopiedMemberCount(),
                selected.get(selected.size() - 1).getMemberKey(),
                copiedCount
        ), "member set copy CAS");
        Dp08MemberSetRecord advanced = requireMemberSet(handle.getMemberSetId());
        if ("SEALED".equals(advanced.getSetState())) {
            return null;
        }
        return Dp08MemberSetScheduleResult.progress(Dp08MemberSourceCursor.resume(
                head.getSourceCursor(),
                "COPY:" + Objects.requireNonNull(advanced.getCopyCursor(), "copyCursor")
        ));
    }

    private Dp08MemberSetRecord requireMemberSet(String memberSetId) {
        return Objects.requireNonNull(
                mapper.lockMemberSet(memberSetId),
                "DP08 member set"
        );
    }

    private void verify(
            Dp08MemberSetRecord record,
            Dp08MemberSetHandle handle,
            LocalDateTime effectiveFromUtc
    ) {
        if (record.getOperationCode() != handle.getOperationCode()
                || !record.getScopeKey().equals(handle.getStableScopeKey())
                || !record.getMemberCount().equals(handle.getMemberCount())
                || !record.getMemberOrderedSha256().equals(
                        handle.getMemberOrderedSha256()
                )
                || !record.getEffectiveFromUtc().equals(effectiveFromUtc)) {
            throw new IllegalStateException("DP08 member-set identity collision");
        }
    }

    private String type(Dp08MemberSetHandle handle) {
        return handle.getOperationCode() == com.nuono.next.datapull.runtime.OperationCode.DP08A
                ? Dp08MemberSetHandleCodec.KEYWORD_TYPE
                : Dp08MemberSetHandleCodec.LIST_TYPE;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(64);
            for (byte item : digest) {
                output.append(String.format("%02x", item & 255));
            }
            return output.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private void requireOne(int changed, String action) {
        if (changed != 1) {
            throw new IllegalStateException(action + " must affect one row");
        }
    }
}
