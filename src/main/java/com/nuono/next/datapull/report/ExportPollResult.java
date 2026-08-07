package com.nuono.next.datapull.report;

/** Complete, typed interpretation of one successful poll response. */
public final class ExportPollResult {

    public enum Status {
        PENDING,
        READY,
        EMPTY,
        TERMINAL_FAILURE
    }

    private final Status status;
    private final String sanitizedCode;
    private final String downloadLocatorReference;
    private final ReportArtifactAuthority artifactAuthority;
    private final String emptyStableRequestKey;
    private final String emptyRemoteHandle;

    private ExportPollResult(
            Status status,
            String sanitizedCode,
            String downloadLocatorReference,
            ReportArtifactAuthority artifactAuthority,
            String emptyStableRequestKey,
            String emptyRemoteHandle
    ) {
        this.status = status;
        this.sanitizedCode = sanitizedCode;
        if ((status == Status.READY)
                != (downloadLocatorReference != null && artifactAuthority != null)) {
            throw new IllegalArgumentException(
                    "only READY poll results carry a locator and provider authority"
            );
        }
        this.downloadLocatorReference = downloadLocatorReference == null
                ? null
                : ReportContract.requireIdentity(
                        downloadLocatorReference,
                        "downloadLocatorReference"
                );
        this.artifactAuthority = artifactAuthority;
        if ((status == Status.EMPTY) != (emptyStableRequestKey != null && emptyRemoteHandle != null)) {
            throw new IllegalArgumentException(
                    "only authoritative EMPTY poll results carry exact intent and handle proof"
            );
        }
        this.emptyStableRequestKey = emptyStableRequestKey == null
                ? null
                : ReportContract.requireIdentity(emptyStableRequestKey, "emptyStableRequestKey");
        this.emptyRemoteHandle = emptyRemoteHandle == null
                ? null
                : ReportContract.requireIdentity(emptyRemoteHandle, "emptyRemoteHandle");
    }

    public static ExportPollResult pending() {
        return new ExportPollResult(
                Status.PENDING, "EXPORT_PENDING", null, null, null, null
        );
    }

    /**
     * A READY result carries a durable, secret-free Adapter reference, never a signed URL.
     * The reference must remain resolvable after a process restart.
     */
    public static ExportPollResult ready(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String downloadLocatorReference,
            long declaredRowCount
    ) {
        return new ExportPollResult(
                Status.READY,
                "EXPORT_READY",
                downloadLocatorReference,
                ReportArtifactAuthority.proven(intent, handle, declaredRowCount),
                null,
                null
        );
    }

    public static ExportPollResult readyWithLocalRowCount(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String downloadLocatorReference
    ) {
        return new ExportPollResult(
                Status.READY,
                "EXPORT_READY_LOCAL_ROW_COUNT",
                downloadLocatorReference,
                ReportArtifactAuthority.locallyCounted(intent, handle),
                null,
                null
        );
    }

    public static ExportPollResult authoritativeEmpty(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            Integer authoritativeRowCount
    ) {
        if (authoritativeRowCount == null || authoritativeRowCount != 0) {
            throw new IllegalArgumentException(
                    "authoritative empty proof requires an explicit zero row count"
            );
        }
        ExportReportIntent safeIntent = java.util.Objects.requireNonNull(intent, "intent");
        RemoteExportHandle safeHandle = java.util.Objects.requireNonNull(handle, "handle");
        return new ExportPollResult(
                Status.EMPTY,
                "EXPORT_EMPTY_AUTHORITATIVE",
                null,
                null,
                safeIntent.getStableRequestKey(),
                safeHandle.getValue()
        );
    }

    public static ExportPollResult terminalFailure(String sanitizedCode) {
        return new ExportPollResult(
                Status.TERMINAL_FAILURE,
                ReportContract.requireSafeCode(sanitizedCode, "sanitizedCode"),
                null,
                null,
                null,
                null
        );
    }

    public Status getStatus() {
        return status;
    }

    public String getSanitizedCode() {
        return sanitizedCode;
    }

    public String getDownloadLocatorReference() {
        return downloadLocatorReference;
    }

    public ReportArtifactAuthority getArtifactAuthority() {
        return artifactAuthority;
    }

    public boolean provesReadyFor(ExportReportIntent intent, RemoteExportHandle handle) {
        return status == Status.READY
                && artifactAuthority != null
                && artifactAuthority.proves(intent, handle);
    }

    public boolean provesAuthoritativeEmptyFor(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        return status == Status.EMPTY
                && intent != null
                && handle != null
                && intent.getStableRequestKey().equals(emptyStableRequestKey)
                && handle.getValue().equals(emptyRemoteHandle);
    }
}
