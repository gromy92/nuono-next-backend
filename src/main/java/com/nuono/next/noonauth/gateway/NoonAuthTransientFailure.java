package com.nuono.next.noonauth.gateway;

import java.util.Objects;

public final class NoonAuthTransientFailure {
    private final NoonAuthRecoveryFailureStage stage;
    private final NoonTransientErrorType errorType;
    private final String safeDiagnostic;

    public NoonAuthTransientFailure(
            NoonAuthRecoveryFailureStage stage,
            NoonTransientErrorType errorType,
            String safeDiagnostic
    ) {
        this.stage = Objects.requireNonNull(stage, "stage must not be null");
        this.errorType = Objects.requireNonNull(errorType, "errorType must not be null");
        this.safeDiagnostic = safeDiagnostic;
    }

    public NoonAuthRecoveryFailureStage getStage() {
        return stage;
    }

    public NoonTransientErrorType getErrorType() {
        return errorType;
    }

    public String getSafeDiagnostic() {
        return safeDiagnostic;
    }
}
