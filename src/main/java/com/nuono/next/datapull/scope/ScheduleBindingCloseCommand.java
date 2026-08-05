package com.nuono.next.datapull.scope;

import java.time.LocalDateTime;
import java.util.Objects;

/** Exact CAS tuple for one bounded binding close. */
public final class ScheduleBindingCloseCommand {
    private final String bindingId;
    private final String payloadSha256;
    private final LocalDateTime effectiveUntilUtc;

    public ScheduleBindingCloseCommand(
            String bindingId,
            String payloadSha256,
            LocalDateTime effectiveUntilUtc
    ) {
        this.bindingId = Objects.requireNonNull(bindingId, "bindingId");
        this.payloadSha256 = Objects.requireNonNull(payloadSha256, "payloadSha256");
        this.effectiveUntilUtc = Objects.requireNonNull(
                effectiveUntilUtc, "effectiveUntilUtc"
        );
    }

    public String getBindingId() { return bindingId; }
    public String getPayloadSha256() { return payloadSha256; }
    public LocalDateTime getEffectiveUntilUtc() { return effectiveUntilUtc; }
}
