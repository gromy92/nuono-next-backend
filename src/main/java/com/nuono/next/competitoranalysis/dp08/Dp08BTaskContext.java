package com.nuono.next.competitoranalysis.dp08;

import java.time.LocalDate;

/** Exactly one decoded legacy target or persistent member-set handle. */
final class Dp08BTaskContext {
    private final Dp08ListTarget legacyTarget;
    private final Dp08MemberSetHandle memberSetHandle;

    private Dp08BTaskContext(
            Dp08ListTarget legacyTarget,
            Dp08MemberSetHandle memberSetHandle
    ) {
        if ((legacyTarget == null) == (memberSetHandle == null)) {
            throw new IllegalArgumentException("DP08-B task context must have one authority");
        }
        this.legacyTarget = legacyTarget;
        this.memberSetHandle = memberSetHandle;
    }

    static Dp08BTaskContext legacy(Dp08ListTarget target) {
        return new Dp08BTaskContext(target, null);
    }

    static Dp08BTaskContext memberSet(Dp08MemberSetHandle handle) {
        return new Dp08BTaskContext(null, handle);
    }

    boolean isMemberSet() {
        return memberSetHandle != null;
    }

    Dp08ListTarget providerTarget(LocalDate factDate) {
        return isMemberSet()
                ? memberSetHandle.listProviderTarget(factDate, true)
                : legacyTarget;
    }

    Dp08ListTarget legacyTarget() {
        return legacyTarget;
    }

    Dp08MemberSetHandle memberSetHandle() {
        return memberSetHandle;
    }
}
