package com.nuono.next.noonauth.gateway;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class NoonAuthRecoveryAttemptResult {
    private final NoonAuthRecoveryFailureCode failureCode;
    private final String messageKeyHash;
    private final String safeDiagnostic;
    private final List<NoonAuthRecoveryProjectResult> projectResults;
    private final List<NoonAuthTransientFailure> transientFailures;

    private NoonAuthRecoveryAttemptResult(
            NoonAuthRecoveryFailureCode failureCode,
            String messageKeyHash,
            String safeDiagnostic,
            List<NoonAuthRecoveryProjectResult> projectResults,
            List<NoonAuthTransientFailure> transientFailures
    ) {
        this.failureCode = failureCode;
        this.messageKeyHash = messageKeyHash;
        this.safeDiagnostic = safeDiagnostic;
        this.projectResults = projectResults == null
                ? Collections.emptyList()
                : List.copyOf(projectResults);
        this.transientFailures = transientFailures == null
                ? Collections.emptyList()
                : List.copyOf(transientFailures);
    }

    public static NoonAuthRecoveryAttemptResult authenticated(
            String messageKeyHash,
            List<NoonAuthRecoveryProjectResult> projectResults
    ) {
        return new NoonAuthRecoveryAttemptResult(
                null,
                messageKeyHash,
                "identity authenticated",
                projectResults,
                Collections.emptyList()
        );
    }

    public static NoonAuthRecoveryAttemptResult failed(
            NoonAuthRecoveryFailureCode failureCode,
            String messageKeyHash,
            String safeDiagnostic
    ) {
        if (failureCode == NoonAuthRecoveryFailureCode.PROVIDER_TRANSIENT_FAILURE) {
            throw new IllegalArgumentException(
                    "Provider transient failures require stage and exact error type."
            );
        }
        return new NoonAuthRecoveryAttemptResult(
                failureCode,
                messageKeyHash,
                safeDiagnostic,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static NoonAuthRecoveryAttemptResult transientFailure(
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientErrorType,
            String messageKeyHash,
            String safeDiagnostic
    ) {
        return transientFailures(
                List.of(new NoonAuthTransientFailure(
                        Objects.requireNonNull(failureStage, "failureStage must not be null"),
                        Objects.requireNonNull(
                                transientErrorType,
                                "transientErrorType must not be null"
                        ),
                        safeDiagnostic
                )),
                messageKeyHash,
                safeDiagnostic
        );
    }

    public static NoonAuthRecoveryAttemptResult transientFailures(
            List<NoonAuthTransientFailure> transientFailures,
            String messageKeyHash,
            String safeDiagnostic
    ) {
        if (transientFailures == null || transientFailures.isEmpty()) {
            throw new IllegalArgumentException("At least one transient failure is required.");
        }
        return new NoonAuthRecoveryAttemptResult(
                NoonAuthRecoveryFailureCode.PROVIDER_TRANSIENT_FAILURE,
                messageKeyHash,
                safeDiagnostic,
                Collections.emptyList(),
                transientFailures
        );
    }

    public boolean isIdentityAuthenticated() {
        return failureCode == null;
    }

    public NoonAuthRecoveryFailureCode getFailureCode() {
        return failureCode;
    }

    public boolean isTransientFailure() {
        return failureCode == NoonAuthRecoveryFailureCode.PROVIDER_TRANSIENT_FAILURE
                && !transientFailures.isEmpty();
    }

    public String getMessageKeyHash() {
        return messageKeyHash;
    }

    public String getSafeDiagnostic() {
        return safeDiagnostic;
    }

    public List<NoonAuthRecoveryProjectResult> getProjectResults() {
        return projectResults;
    }

    public NoonAuthRecoveryFailureStage getFailureStage() {
        return transientFailures.isEmpty() ? null : transientFailures.get(0).getStage();
    }

    public NoonTransientErrorType getTransientErrorType() {
        return transientFailures.isEmpty() ? null : transientFailures.get(0).getErrorType();
    }

    public List<NoonAuthTransientFailure> getTransientFailures() {
        return transientFailures;
    }
}
