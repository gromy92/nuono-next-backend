package com.nuono.next.competitoranalysis.dp08;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.datapull.persistence.DataPullTask;
import java.time.LocalDate;

/** Decodes and verifies the immutable DP08-B task-scope authority. */
final class Dp08BTaskContextDecoder {
    private final Dp08ScopeSnapshotCodec legacyCodec;
    private final Dp08MemberSetHandleCodec memberSetCodec;

    Dp08BTaskContextDecoder(ObjectMapper objectMapper) {
        this.legacyCodec = new Dp08ScopeSnapshotCodec(objectMapper);
        this.memberSetCodec = new Dp08MemberSetHandleCodec(objectMapper);
    }

    Dp08BTaskContext decode(DataPullTask task, LocalDate factDate) {
        if (Dp08MemberSetHandleCodec.LIST_TYPE.equals(task.getScopePayloadType())) {
            return Dp08BTaskContext.memberSet(memberSetCodec.decode(task));
        }
        Dp08ListTarget target = legacyCodec.decodeListTarget(task);
        if (!factDate.equals(target.getFactDate())) {
            throw new IllegalStateException("DP08-B fact-date binding drift");
        }
        return Dp08BTaskContext.legacy(target);
    }
}
