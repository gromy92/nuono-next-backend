package com.nuono.next.datapull.report;

import java.util.Objects;

/** Readback of one stable export-create intent; absence is advisory until attempt-bound. */
public final class ExportCreateReadback {
    public enum Status {
        FOUND,
        TERMINALLY_ABSENT
    }

    private final Status status;
    private final String stableRequestKey;
    private final RemoteExportHandle handle;
    private final String providerAbsenceProof;

    private ExportCreateReadback(
            Status status,
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String providerAbsenceProof
    ) {
        this.status = Objects.requireNonNull(status, "status");
        this.stableRequestKey = Objects.requireNonNull(intent, "intent").getStableRequestKey();
        this.handle = handle;
        this.providerAbsenceProof = providerAbsenceProof;
        if ((status == Status.FOUND) != (handle != null)
                || (status == Status.TERMINALLY_ABSENT)
                != (providerAbsenceProof != null)) {
            throw new IllegalArgumentException("export create readback evidence is incomplete");
        }
    }

    public static ExportCreateReadback found(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        return new ExportCreateReadback(
                Status.FOUND,
                intent,
                Objects.requireNonNull(handle, "handle"),
                null
        );
    }

    public static ExportCreateReadback terminallyAbsent(
            ExportReportIntent intent,
            String providerAbsenceProof
    ) {
        return new ExportCreateReadback(
                Status.TERMINALLY_ABSENT,
                intent,
                null,
                ReportContract.requireIdentity(providerAbsenceProof, "providerAbsenceProof")
        );
    }

    public boolean proves(ExportReportIntent intent) {
        return stableRequestKey.equals(
                Objects.requireNonNull(intent, "intent").getStableRequestKey()
        );
    }

    public Status getStatus() { return status; }
    public RemoteExportHandle getHandle() { return handle; }
    public String getProviderAbsenceProof() { return providerAbsenceProof; }
}
