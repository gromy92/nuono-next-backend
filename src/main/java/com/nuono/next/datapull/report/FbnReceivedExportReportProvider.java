package com.nuono.next.datapull.report;

import com.nuono.next.datapull.runtime.ProviderOutcome;
import com.nuono.next.noon.NoonBinaryDownloadContractException;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.ExportStatus;
import com.nuono.next.officialwarehouse.OfficialWarehouseFbnExportProvider.PullRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.util.StringUtils;

/** DP-07-B bridge that fails closed where the real provider lacks task-bound authority. */
public final class FbnReceivedExportReportProvider
        implements ExportReportProvider, ReportProviderCapabilitySource {
    public static final String REPORT_TYPE = "fbn_inbound_fbnreceivedreport";

    private final NoonReportDefinition definition;
    private final OfficialWarehouseFbnExportProvider delegate;
    private final FbnReportDownloadTransport downloadTransport;
    private final ReportDownloadLocatorVault locatorVault;
    private final ReportArtifactStore artifactStore;
    private final ReportProviderCapabilities.EmptyProofEvidence emptyProofEvidence;
    private final ReportProviderCapabilities.ArtifactCompletenessEvidence
            artifactCompletenessEvidence;

    public FbnReceivedExportReportProvider(
            NoonReportDefinition definition,
            OfficialWarehouseFbnExportProvider delegate,
            FbnReportDownloadTransport downloadTransport,
            ReportDownloadLocatorVault locatorVault,
            ReportArtifactStore artifactStore,
            ReportProviderCapabilities.EmptyProofEvidence emptyProofEvidence,
            ReportProviderCapabilities.ArtifactCompletenessEvidence
                    artifactCompletenessEvidence
    ) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.downloadTransport = Objects.requireNonNull(
                downloadTransport, "downloadTransport"
        );
        this.locatorVault = Objects.requireNonNull(locatorVault, "locatorVault");
        this.artifactStore = Objects.requireNonNull(artifactStore, "artifactStore");
        this.emptyProofEvidence = Objects.requireNonNull(
                emptyProofEvidence,
                "emptyProofEvidence"
        );
        this.artifactCompletenessEvidence = Objects.requireNonNull(
                artifactCompletenessEvidence,
                "artifactCompletenessEvidence"
        );
        if (!REPORT_TYPE.equalsIgnoreCase(definition.getReportType())) {
            throw new IllegalArgumentException("DP07B report type mismatch");
        }
    }

    @Override
    public ReportProviderCapabilities reportProviderCapabilities() {
        return new ReportProviderCapabilities(
                definition.getOperationCode(),
                ReportProviderCapabilities.CreateReadbackEvidence
                        .READ_ONLY_EXPORT_RETRY_AFTER_PERSISTED_BACKOFF,
                emptyProofEvidence,
                artifactCompletenessEvidence
        );
    }

    @Override
    public boolean retryUnknownCreateAfterReadbackFailure() {
        return true;
    }

    @Override
    public ProviderOutcome<RemoteExportHandle> create(ExportReportIntent intent) {
        NoonReportWindow window;
        PullRequest request;
        try {
            window = NoonReportIntentSupport.window(intent);
            request = request(intent);
        } catch (RuntimeException invalidIntent) {
            return ProviderOutcome.contractError("DP07B_REPORT_INTENT_INVALID");
        }
        try {
            OfficialWarehouseFbnExportProvider.CreateExportResult result = delegate.createExport(
                    request,
                    new OfficialWarehouseFbnExportProvider.CreateExportRequest(
                            REPORT_TYPE,
                            window.getDateFrom().toString(),
                            window.getDateTo().toString()
                    )
            );
            if (result == null || !StringUtils.hasText(result.exportCode)) {
                return NoonReportOutcomeClassifier.ambiguousCreate();
            }
            if (!REPORT_TYPE.equalsIgnoreCase(normalize(result.reportType))) {
                return NoonReportOutcomeClassifier.ambiguousCreate();
            }
            return ProviderOutcome.success(new RemoteExportHandle(result.exportCode));
        } catch (RuntimeException failure) {
            return NoonReportOutcomeClassifier.createFailure(failure);
        }
    }

    @Override
    public ProviderOutcome<ExportCreateReadback> findByRequestKey(ExportReportIntent intent) {
        Objects.requireNonNull(intent, "intent");
        // The real list API exposes report type/window metadata but no caller supplied
        // stable request key. Even a full paginated scan cannot distinguish this task's
        // export from a manual or earlier export for the same date window.
        return ProviderOutcome.contractError(
                "DP07B_CREATE_READBACK_STABLE_REQUEST_KEY_UNAVAILABLE"
        );
    }

    @Override
    public ProviderOutcome<ExportPollResult> poll(
            ExportReportIntent intent,
            RemoteExportHandle handle
    ) {
        try {
            ExportStatus status = delegate.exportStatus(request(intent), handle.getValue(), false);
            if (status == null || !StringUtils.hasText(status.status)) {
                return ProviderOutcome.contractError("DP07B_POLL_STATUS_MISSING");
            }
            String normalized = status.status.trim().toUpperCase(Locale.ROOT);
            if (isComplete(normalized)) {
                if (status.totalRows != null && status.totalRows == 0) {
                    if (emptyProofEvidence == ReportProviderCapabilities.EmptyProofEvidence.UNAVAILABLE) {
                        return ProviderOutcome.contractError("DP07B_EMPTY_PROOF_UNAVAILABLE");
                    }
                    if (!handle.getValue().equals(status.providerExportCode)) {
                        return ProviderOutcome.contractError("DP07B_EMPTY_HANDLE_UNPROVEN");
                    }
                    return ProviderOutcome.success(
                            ExportPollResult.authoritativeEmpty(
                                    intent,
                                    handle,
                                    status.totalRows
                            )
                    );
                }
                if (StringUtils.hasText(status.downloadUrl)) {
                    if (artifactCompletenessEvidence
                            == ReportProviderCapabilities.ArtifactCompletenessEvidence
                                    .UNAVAILABLE) {
                        return ProviderOutcome.contractError(
                                "DP07B_ARTIFACT_COMPLETENESS_UNAVAILABLE"
                        );
                    }
                    if (status.totalRows == null || status.totalRows <= 0) {
                        return ProviderOutcome.contractError(
                                "DP07B_ARTIFACT_ROW_COUNT_UNPROVEN"
                        );
                    }
                    if (!handle.getValue().equals(status.providerExportCode)) {
                        return ProviderOutcome.contractError(
                                "DP07B_ARTIFACT_HANDLE_UNPROVEN"
                        );
                    }
                    String reference = locatorVault.store(intent, handle, status.downloadUrl);
                    return ProviderOutcome.success(ExportPollResult.ready(
                            intent,
                            handle,
                            reference,
                            status.totalRows
                    ));
                }
                return ProviderOutcome.contractError("DP07B_READY_LOCATOR_MISSING");
            }
            if (isFailed(normalized)) {
                return ProviderOutcome.success(
                        ExportPollResult.terminalFailure("DP07B_PROVIDER_TERMINAL_FAILURE")
                );
            }
            if (isPending(normalized)) {
                return ProviderOutcome.success(ExportPollResult.pending());
            }
            return ProviderOutcome.contractError("DP07B_POLL_STATUS_UNKNOWN");
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
        } catch (RuntimeException invalid) {
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
            downloadTransport.download(request(intent), rawLocator, download);
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

    private PullRequest request(ExportReportIntent intent) {
        if (intent.getOperationCode() != definition.getOperationCode()
                || !intent.getProviderChannel().equals(definition.getProviderChannel())) {
            throw new IllegalArgumentException("DP07B_REPORT_INTENT_MISMATCH");
        }
        return new PullRequest(intent.getOwnerUserId(), intent.getStoreCode(), intent.getSiteCode());
    }

    private boolean isComplete(String value) {
        return List.of("COMPLETE", "COMPLETED", "SUCCESS", "READY", "DONE").contains(value);
    }

    private boolean isFailed(String value) {
        return List.of("FAILED", "FAILURE", "ERROR", "CANCELLED", "CANCELED").contains(value);
    }

    private boolean isPending(String value) {
        return List.of("PENDING", "RUNNING", "PROCESSING", "IN_PROGRESS", "QUEUED").contains(value);
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

}
