package com.nuono.next.datapull.schedule;

import com.nuono.next.datapull.runtime.OperationCode;
import java.time.Instant;
import java.util.Objects;

/** Persistent scan identity supplied to sources that need nested, restart-safe keysets. */
public final class ScheduleSourceReadContext {
    public enum Pass { ONE, TWO }

    private final OperationCode operationCode;
    private final long epochNo;
    private final Pass pass;
    private final String afterNativeCursorExclusive;
    private final Instant reconcileUntil;
    private final int limit;

    public ScheduleSourceReadContext(
            OperationCode operationCode,
            long epochNo,
            Pass pass,
            String afterNativeCursorExclusive,
            Instant reconcileUntil,
            int limit
    ) {
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode");
        if (epochNo < 1) throw new IllegalArgumentException("epochNo must be positive");
        this.epochNo = epochNo;
        this.pass = Objects.requireNonNull(pass, "pass");
        this.afterNativeCursorExclusive = afterNativeCursorExclusive;
        this.reconcileUntil = Objects.requireNonNull(reconcileUntil, "reconcileUntil");
        if (limit < 1 || limit > 64) throw new IllegalArgumentException("invalid source limit");
        this.limit = limit;
    }

    public OperationCode getOperationCode() { return operationCode; }
    public long getEpochNo() { return epochNo; }
    public Pass getPass() { return pass; }
    public int getScanPass() { return pass == Pass.ONE ? 1 : 2; }
    public String getAfterNativeCursorExclusive() { return afterNativeCursorExclusive; }
    public Instant getReconcileUntil() { return reconcileUntil; }
    public int getLimit() { return limit; }
}
