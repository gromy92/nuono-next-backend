package com.nuono.next.datapull.report;

final class ExportReportCheckpoint {

    enum Phase {
        CREATE,
        RECONCILE_CREATE,
        POLL,
        DOWNLOAD,
        APPLY
    }

    private final Phase phase;
    private final String stableRequestKey;
    private final int consecutiveRetryAttempt;
    private final boolean createOutcomeUnknown;
    private final String downloadLocatorReference;
    private final ReportArtifactAuthority artifactAuthority;
    private final DownloadedReportArtifact artifact;

    private ExportReportCheckpoint(
            Phase phase,
            String stableRequestKey,
            int consecutiveRetryAttempt,
            boolean createOutcomeUnknown,
            String downloadLocatorReference,
            ReportArtifactAuthority artifactAuthority,
            DownloadedReportArtifact artifact
    ) {
        this.phase = phase;
        this.stableRequestKey = ReportContract.requireIdentity(
                stableRequestKey,
                "stableRequestKey"
        );
        if (consecutiveRetryAttempt < 0) {
            throw new IllegalArgumentException("consecutiveRetryAttempt must not be negative");
        }
        if (createOutcomeUnknown && phase != Phase.RECONCILE_CREATE) {
            throw new IllegalArgumentException(
                    "unknown create outcome is valid only while reconciling create"
            );
        }
        if ((phase == Phase.APPLY) != (artifact != null)) {
            throw new IllegalArgumentException("only APPLY checkpoints carry a downloaded artifact");
        }
        if (artifact != null && artifact.getAuthority() == null) {
            throw new IllegalArgumentException("APPLY artifact requires provider authority");
        }
        if (phase != Phase.DOWNLOAD && phase != Phase.APPLY && artifactAuthority != null) {
            throw new IllegalArgumentException("provider authority is not valid for this phase");
        }
        if ((phase == Phase.DOWNLOAD)
                != (downloadLocatorReference != null && artifactAuthority != null)) {
            throw new IllegalArgumentException(
                    "only DOWNLOAD checkpoints carry a locator and provider authority"
            );
        }
        this.consecutiveRetryAttempt = consecutiveRetryAttempt;
        this.createOutcomeUnknown = createOutcomeUnknown;
        this.downloadLocatorReference = downloadLocatorReference == null
                ? null
                : ReportContract.requireIdentity(
                        downloadLocatorReference,
                        "downloadLocatorReference"
                );
        this.artifactAuthority = phase == Phase.APPLY
                ? artifact.getAuthority()
                : artifactAuthority;
        this.artifact = artifact;
    }

    static ExportReportCheckpoint at(Phase phase, String requestKey) {
        return new ExportReportCheckpoint(phase, requestKey, 0, false, null, null, null);
    }

    static ExportReportCheckpoint restored(
            Phase phase,
            String requestKey,
            int retryAttempt,
            boolean createOutcomeUnknown,
            String downloadLocatorReference,
            ReportArtifactAuthority artifactAuthority,
            DownloadedReportArtifact artifact
    ) {
        return new ExportReportCheckpoint(
                phase,
                requestKey,
                retryAttempt,
                createOutcomeUnknown,
                downloadLocatorReference,
                artifactAuthority,
                artifact
        );
    }

    ExportReportCheckpoint progressTo(Phase nextPhase) {
        return new ExportReportCheckpoint(
                nextPhase, stableRequestKey, 0, false, null, null, null
        );
    }

    ExportReportCheckpoint download(
            String locatorReference,
            ReportArtifactAuthority authority
    ) {
        return new ExportReportCheckpoint(
                Phase.DOWNLOAD,
                stableRequestKey,
                0,
                false,
                locatorReference,
                authority,
                null
        );
    }

    ExportReportCheckpoint preserveAttemptAt(Phase nextPhase) {
        return new ExportReportCheckpoint(
                nextPhase,
                stableRequestKey,
                consecutiveRetryAttempt,
                nextPhase == Phase.RECONCILE_CREATE && createOutcomeUnknown,
                nextPhase == Phase.DOWNLOAD ? downloadLocatorReference : null,
                nextPhase == Phase.DOWNLOAD ? artifactAuthority : null,
                nextPhase == Phase.APPLY ? artifact : null
        );
    }

    ExportReportCheckpoint retryAt(Phase nextPhase) {
        int nextAttempt = consecutiveRetryAttempt == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : consecutiveRetryAttempt + 1;
        DownloadedReportArtifact retainedArtifact = nextPhase == Phase.APPLY ? artifact : null;
        String retainedLocator = nextPhase == Phase.DOWNLOAD
                ? downloadLocatorReference
                : null;
        ReportArtifactAuthority retainedAuthority = nextPhase == Phase.DOWNLOAD
                ? artifactAuthority
                : null;
        return new ExportReportCheckpoint(
                nextPhase,
                stableRequestKey,
                nextAttempt,
                nextPhase == Phase.RECONCILE_CREATE && createOutcomeUnknown,
                retainedLocator,
                retainedAuthority,
                retainedArtifact
        );
    }

    ExportReportCheckpoint apply(DownloadedReportArtifact downloadedArtifact) {
        return new ExportReportCheckpoint(
                Phase.APPLY,
                stableRequestKey,
                0,
                false,
                null,
                null,
                downloadedArtifact.bind(artifactAuthority)
        );
    }

    ExportReportCheckpoint unknownCreateOutcome() {
        return new ExportReportCheckpoint(
                Phase.RECONCILE_CREATE,
                stableRequestKey,
                consecutiveRetryAttempt,
                true,
                null,
                null,
                null
        );
    }

    Phase getPhase() { return phase; }
    String getStableRequestKey() { return stableRequestKey; }
    int getConsecutiveRetryAttempt() { return consecutiveRetryAttempt; }
    boolean isCreateOutcomeUnknown() { return createOutcomeUnknown; }
    String getDownloadLocatorReference() { return downloadLocatorReference; }
    ReportArtifactAuthority getArtifactAuthority() { return artifactAuthority; }
    DownloadedReportArtifact getArtifact() { return artifact; }
}
