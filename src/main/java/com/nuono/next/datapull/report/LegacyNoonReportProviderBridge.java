package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.noon.NoonBinaryDownloadContractException;
import com.nuono.next.noonpull.NoonReportExportStatus;
import com.nuono.next.noonpull.NoonReportProvider;
import com.nuono.next.noonpull.NoonReportPullRequest;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** Bounded bridge around the existing sales/order/finance Noon export providers. */
public final class LegacyNoonReportProviderBridge
        implements ExportReportProvider, ReportProviderCapabilitySource {
    public enum ReadbackMode {
        /** The delegate itself proves that its immutable handle belongs to this exact window. */
        DELEGATE_PROVES_EXACT_WINDOW,
        /** Persist, reconcile once, then retry the same read-only export intent after backoff. */
        RETRY_READ_ONLY_EXPORT_AFTER_BACKOFF,
        /** Re-read the same requested window; complete rows later prove container identity. */
        SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION,
        UNAVAILABLE
    }

    public enum EmptyProofMode {
        AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
        UNPROVEN_EMPTY_REMAINS_WAITING,
        UNAVAILABLE
    }

    public enum ArtifactCompletenessMode {
        AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT,
        COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION,
        UNAVAILABLE
    }

    private final NoonReportDefinition definition;
    private final NoonReportProvider delegate;
    private final ReadbackMode readbackMode;
    private final EmptyProofMode emptyProofMode;
    private final ArtifactCompletenessMode artifactCompletenessMode;
    private final ReportDownloadLocatorVault locatorVault;
    private final ReportArtifactStore artifactStore;

    public LegacyNoonReportProviderBridge(
            NoonReportDefinition definition,
            NoonReportProvider delegate,
            ReadbackMode readbackMode,
            EmptyProofMode emptyProofMode,
            ArtifactCompletenessMode artifactCompletenessMode,
            ReportDownloadLocatorVault locatorVault,
            ReportArtifactStore artifactStore
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.readbackMode = Objects.requireNonNull(readbackMode, "readbackMode");
        this.emptyProofMode = Objects.requireNonNull(emptyProofMode, "emptyProofMode");
        this.artifactCompletenessMode = Objects.requireNonNull(
                artifactCompletenessMode,
                "artifactCompletenessMode"
        );
        this.locatorVault = Objects.requireNonNull(locatorVault, "locatorVault");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        if ((readbackMode == ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW
                || readbackMode == ReadbackMode.SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION)
                && !definition.hasSyntheticHandle()) {
            throw new IllegalArgumentException("same-intent readback requires a synthetic handle");
        }
    }

    @Override
    public ReportProviderCapabilities reportProviderCapabilities() {
        ReportProviderCapabilities.CreateReadbackEvidence createEvidence;
        if (readbackMode == ReadbackMode.DELEGATE_PROVES_EXACT_WINDOW) {
            createEvidence = ReportProviderCapabilities.CreateReadbackEvidence
                    .EXACT_IMMUTABLE_HANDLE_AND_INTENT;
        } else if (readbackMode == ReadbackMode.RETRY_READ_ONLY_EXPORT_AFTER_BACKOFF) {
            createEvidence = ReportProviderCapabilities.CreateReadbackEvidence
                    .READ_ONLY_EXPORT_RETRY_AFTER_PERSISTED_BACKOFF;
        } else if (readbackMode
                == ReadbackMode.SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION) {
            createEvidence = ReportProviderCapabilities.CreateReadbackEvidence
                    .SAME_INTENT_POLL_WITH_CONTAINER_VALIDATION;
        } else {
            createEvidence = ReportProviderCapabilities.CreateReadbackEvidence.UNAVAILABLE;
        }
        ReportProviderCapabilities.EmptyProofEvidence emptyEvidence =
                emptyProofMode == EmptyProofMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
                ? ReportProviderCapabilities.EmptyProofEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
                : emptyProofMode == EmptyProofMode.UNPROVEN_EMPTY_REMAINS_WAITING
                        ? ReportProviderCapabilities.EmptyProofEvidence
                                .UNPROVEN_EMPTY_REMAINS_WAITING
                        : ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE;
        ReportProviderCapabilities.ArtifactCompletenessEvidence artifactEvidence =
                artifactCompletenessMode == ArtifactCompletenessMode
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
                ? ReportProviderCapabilities.ArtifactCompletenessEvidence
                        .AUTHORITATIVE_ROW_COUNT_FOR_EXACT_HANDLE_AND_INTENT
                : artifactCompletenessMode == ArtifactCompletenessMode
                        .COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION
                        ? ReportProviderCapabilities.ArtifactCompletenessEvidence
                                .COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION
                        : ReportProviderCapabilities.ArtifactCompletenessEvidence.UNAVAILABLE;
        return new ReportProviderCapabilities(
                definition.getOperationCode(),
                createEvidence,
                emptyEvidence,
                artifactEvidence
        );
    }

    @Override
    public boolean retryUnknownCreateAfterReadbackFailure() {
        return readbackMode == ReadbackMode.RETRY_READ_ONLY_EXPORT_AFTER_BACKOFF;
    }

    @Override
    public ProviderOutcome<RemoteExportHandle> create(ExportReportIntent intent) {
        final NoonReportPullRequest request;
        final String expected;
        try {
            request = NoonReportIntentSupport.request(intent, definition);
            expected = definition.hasSyntheticHandle()
                    ? definition.syntheticHandle(NoonReportIntentSupport.window(intent))
                    : null;
        } catch (RuntimeException invalidIntent) {
            return ProviderOutcome.contractError("REPORT_CREATE_INTENT_INVALID");
        }
        try {
            String handle = delegate.createExport(request);
            if (!StringUtils.hasText(handle)) {
                return NoonReportOutcomeClassifier.ambiguousCreate();
            }
            if (expected != null && !expected.equals(handle)) {
                return NoonReportOutcomeClassifier.ambiguousCreate();
            }
            return ProviderOutcome.success(new RemoteExportHandle(handle));
        } catch (RuntimeException failure) {
            return NoonReportOutcomeClassifier.createFailure(failure);
        }
    }

    @Override
    public ProviderOutcome<ExportCreateReadback> findByRequestKey(ExportReportIntent intent) {
        if (readbackMode == ReadbackMode.UNAVAILABLE
                || readbackMode == ReadbackMode.RETRY_READ_ONLY_EXPORT_AFTER_BACKOFF) {
            return ProviderOutcome.contractError("REPORT_CREATE_READBACK_UNAVAILABLE");
        }
        try {
            NoonReportPullRequest request = NoonReportIntentSupport.request(intent, definition);
            String expected = definition.syntheticHandle(NoonReportIntentSupport.window(intent));
            NoonReportExportStatus status = delegate.pollExport(request, expected);
            if (status == null || (!status.isReady() && !status.isPending() && !status.isFailed())) {
                return ProviderOutcome.contractError("REPORT_LATEST_READBACK_UNPROVEN");
            }
            return ProviderOutcome.success(ExportCreateReadback.found(
                    intent,
                    new RemoteExportHandle(expected)
            ));
        } catch (RuntimeException failure) {
            return NoonReportOutcomeClassifier.readFailure(failure);
        }
    }

    @Override
    public ProviderOutcome<ExportPollResult> poll(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        try {
            NoonReportExportStatus status = delegate.pollExport(
                    NoonReportIntentSupport.request(intent, definition),
                    handle.getValue()
            );
            if (status == null) {
                return ProviderOutcome.contractError("REPORT_POLL_RESPONSE_MISSING");
            }
            if (status.isReady()) {
                if (status.getTotalRows() != null && status.getTotalRows() == 0) {
                    if (emptyProofMode == EmptyProofMode.UNAVAILABLE) {
                        return ProviderOutcome.contractError("REPORT_EMPTY_PROOF_UNAVAILABLE");
                    }
                    if (emptyProofMode == EmptyProofMode.UNPROVEN_EMPTY_REMAINS_WAITING) {
                        return ProviderOutcome.success(ExportPollResult.pending());
                    }
                    if (!handle.getValue().equals(status.getProviderExportId())) {
                        return ProviderOutcome.contractError("REPORT_EMPTY_HANDLE_UNPROVEN");
                    }
                    return ProviderOutcome.success(
                            ExportPollResult.authoritativeEmpty(
                                    intent,
                                    handle,
                                    status.getTotalRows()
                            )
                    );
                }
                if (StringUtils.hasText(status.getDownloadUrl())) {
                    if (artifactCompletenessMode == ArtifactCompletenessMode.UNAVAILABLE) {
                        return ProviderOutcome.contractError(
                                "REPORT_ARTIFACT_COMPLETENESS_UNAVAILABLE"
                        );
                    }
                    String reference = locatorVault.store(
                            intent, handle, status.getDownloadUrl()
                    );
                    if (artifactCompletenessMode == ArtifactCompletenessMode
                            .COMPLETE_DOWNLOAD_WITH_LOCAL_ROW_COUNT_AND_CONTAINER_VALIDATION) {
                        return ProviderOutcome.success(
                                ExportPollResult.readyWithLocalRowCount(
                                        intent, handle, reference
                                )
                        );
                    }
                    if (status.getTotalRows() == null || status.getTotalRows() <= 0) {
                        return ProviderOutcome.contractError(
                                "REPORT_ARTIFACT_ROW_COUNT_UNPROVEN"
                        );
                    }
                    if (!handle.getValue().equals(status.getProviderExportId())) {
                        return ProviderOutcome.contractError(
                                "REPORT_ARTIFACT_HANDLE_UNPROVEN"
                        );
                    }
                    return ProviderOutcome.success(ExportPollResult.ready(
                            intent,
                            handle,
                            reference,
                            status.getTotalRows()
                    ));
                }
                return ProviderOutcome.contractError("REPORT_READY_LOCATOR_MISSING");
            }
            if (status.isPending()) {
                return ProviderOutcome.success(ExportPollResult.pending());
            }
            if (status.isFailed()) {
                return ProviderOutcome.success(
                        ExportPollResult.terminalFailure("REPORT_PROVIDER_TERMINAL_FAILURE")
                );
            }
            return ProviderOutcome.contractError("REPORT_POLL_STATUS_UNKNOWN");
        } catch (RuntimeException failure) {
            return NoonReportOutcomeClassifier.readFailure(failure);
        }
    }

    @Override
    public ProviderOutcome<DownloadedReportArtifact> download(
            ExportReportIntent intent,
            RemoteExportHandle handle,
            String downloadLocatorReference
    ) {
        final String rawLocator;
        try {
            rawLocator = locatorVault.resolve(intent, handle, downloadLocatorReference);
        } catch (ReportLocatorNotFoundException missing) {
            return ProviderOutcome.notFound("REPORT_DOWNLOAD_LOCATOR_NOT_FOUND");
        } catch (RuntimeException invalidLocator) {
            return ProviderOutcome.contractError("REPORT_DOWNLOAD_LOCATOR_INVALID");
        }
        ReportArtifactDownload download = null;
        try {
            java.util.Optional<DownloadedReportArtifact> recovered = artifactStore.findCompleted(
                    intent, handle
            );
            if (recovered.isPresent()) {
                return ProviderOutcome.success(recovered.get());
            }
            download = artifactStore.openDownload(intent, handle);
            if (download.isComplete()) {
                return ProviderOutcome.success(download.completedArtifact());
            }
            delegate.download(
                    NoonReportIntentSupport.request(intent, definition),
                    rawLocator,
                    download
            );
            return ProviderOutcome.success(download.completedArtifact());
        } catch (NoonBinaryDownloadContractException contractFailure) {
            abort(download, contractFailure);
            return ProviderOutcome.contractError(contractFailure.getCode());
        } catch (RuntimeException failure) {
            abort(download, failure);
            return NoonReportOutcomeClassifier.downloadFailure(failure);
        }
    }

    private void abort(ReportArtifactDownload download, RuntimeException failure) {
        if (download != null) {
            download.abort(failure);
        }
    }
}
