package com.nuono.next.noonauth.gateway;

import java.util.Collections;
import java.util.List;

public final class NoonAuthRecoveryAttemptResult {
    private final NoonAuthRecoveryFailureCode failureCode;
    private final String messageKeyHash;
    private final String safeDiagnostic;
    private final List<NoonAuthRecoveryProjectResult> projectResults;

    private NoonAuthRecoveryAttemptResult(
            NoonAuthRecoveryFailureCode failureCode,
            String messageKeyHash,
            String safeDiagnostic,
            List<NoonAuthRecoveryProjectResult> projectResults
    ) {
        this.failureCode = failureCode;
        this.messageKeyHash = messageKeyHash;
        this.safeDiagnostic = safeDiagnostic;
        this.projectResults = projectResults == null
                ? Collections.emptyList()
                : List.copyOf(projectResults);
    }

    public static NoonAuthRecoveryAttemptResult authenticated(
            String messageKeyHash,
            List<NoonAuthRecoveryProjectResult> projectResults
    ) {
        return new NoonAuthRecoveryAttemptResult(null, messageKeyHash, "identity authenticated", projectResults);
    }

    public static NoonAuthRecoveryAttemptResult failed(
            NoonAuthRecoveryFailureCode failureCode,
            String messageKeyHash,
            String safeDiagnostic
    ) {
        return new NoonAuthRecoveryAttemptResult(failureCode, messageKeyHash, safeDiagnostic, Collections.emptyList());
    }

    public boolean isIdentityAuthenticated() {
        return failureCode == null;
    }

    public NoonAuthRecoveryFailureCode getFailureCode() {
        return failureCode;
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
}
