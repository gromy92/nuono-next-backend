package com.nuono.next.datapull.report;

import java.util.Objects;

/** Provider evidence that one non-empty download is complete for the exact export intent. */
public final class ReportArtifactAuthority {
    private static final long LOCAL_ROW_COUNT_SENTINEL = Long.MAX_VALUE;
    private final String stableRequestKey;
    private final String remoteHandle;
    private final long declaredRowCount;

    private ReportArtifactAuthority(
            String stableRequestKey,
            String remoteHandle,
            long declaredRowCount
    ) {
        this.stableRequestKey = ReportContract.requireIdentity(
                stableRequestKey,
                "stableRequestKey"
        );
        this.remoteHandle = ReportContract.requireIdentity(remoteHandle, "remoteHandle");
        if (declaredRowCount <= 0L) {
            throw new IllegalArgumentException(
                    "non-empty report authority requires a positive declaredRowCount"
            );
        }
        this.declaredRowCount = declaredRowCount;
    }

    public static ReportArtifactAuthority proven(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            long declaredRowCount
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = Objects.requireNonNull(handle, "handle");
        return restored(
                safeIntent.getStableRequestKey(),
                safeHandle.getValue(),
                declaredRowCount
        );
    }

    public static ReportArtifactAuthority locallyCounted(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        ExportReportIntent safeIntent = Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = Objects.requireNonNull(handle, "handle");
        return restored(
                safeIntent.getStableRequestKey(),
                safeHandle.getValue(),
                LOCAL_ROW_COUNT_SENTINEL
        );
    }

    static ReportArtifactAuthority restored(
            String stableRequestKey,
            String remoteHandle,
            long declaredRowCount
    ) {
        return new ReportArtifactAuthority(
                stableRequestKey,
                remoteHandle,
                declaredRowCount
        );
    }

    public boolean proves(ExportReportIntent intent, RemoteExportHandle handle) {
        return intent != null
                && handle != null
                && stableRequestKey.equals(intent.getStableRequestKey())
                && remoteHandle.equals(handle.getValue());
    }

    public String getStableRequestKey() { return stableRequestKey; }
    public String getRemoteHandle() { return remoteHandle; }
    public long getDeclaredRowCount() { return declaredRowCount; }
    public boolean usesLocalRowCount() {
        return declaredRowCount == LOCAL_ROW_COUNT_SENTINEL;
    }
}
