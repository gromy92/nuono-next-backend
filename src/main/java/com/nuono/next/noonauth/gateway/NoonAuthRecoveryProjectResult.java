package com.nuono.next.noonauth.gateway;

import java.util.Objects;

public final class NoonAuthRecoveryProjectResult {
    public enum Code {
        RECOVERED,
        PROJECT_ACCESS_DENIED,
        SESSION_CREATE_FAILED,
        COOKIE_VALIDATION_FAILED,
        TRANSIENT_PROVIDER_FAILURE
    }

    private final NoonAuthRecoveryProjectTarget target;
    private final Code code;
    private final String cookie;
    private final String safeDiagnostic;
    private final NoonAuthRecoveryFailureStage failureStage;
    private final NoonTransientErrorType transientErrorType;

    private NoonAuthRecoveryProjectResult(
            NoonAuthRecoveryProjectTarget target,
            Code code,
            String cookie,
            String safeDiagnostic,
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientErrorType
    ) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.cookie = cookie;
        this.safeDiagnostic = safeDiagnostic;
        this.failureStage = failureStage;
        this.transientErrorType = transientErrorType;
    }

    public static NoonAuthRecoveryProjectResult recovered(
            NoonAuthRecoveryProjectTarget target,
            String cookie
    ) {
        return new NoonAuthRecoveryProjectResult(
                target,
                Code.RECOVERED,
                cookie,
                "project session verified",
                null,
                null
        );
    }

    public static NoonAuthRecoveryProjectResult failed(
            NoonAuthRecoveryProjectTarget target,
            Code code,
            String safeDiagnostic
    ) {
        if (code == Code.RECOVERED || code == Code.TRANSIENT_PROVIDER_FAILURE) {
            throw new IllegalArgumentException(
                    "Recovered and transient project results require their dedicated factory."
            );
        }
        return new NoonAuthRecoveryProjectResult(target, code, null, safeDiagnostic, null, null);
    }

    public static NoonAuthRecoveryProjectResult transientFailure(
            NoonAuthRecoveryProjectTarget target,
            NoonAuthRecoveryFailureStage failureStage,
            NoonTransientErrorType transientErrorType,
            String safeDiagnostic
    ) {
        return new NoonAuthRecoveryProjectResult(
                target,
                Code.TRANSIENT_PROVIDER_FAILURE,
                null,
                safeDiagnostic,
                Objects.requireNonNull(failureStage, "failureStage must not be null"),
                Objects.requireNonNull(transientErrorType, "transientErrorType must not be null")
        );
    }

    public NoonAuthRecoveryProjectTarget getTarget() {
        return target;
    }

    public Code getCode() {
        return code;
    }

    public boolean isRecovered() {
        return code == Code.RECOVERED;
    }

    public boolean isTransientFailure() {
        return code == Code.TRANSIENT_PROVIDER_FAILURE
                && failureStage != null
                && transientErrorType != null;
    }

    public String getCookie() {
        return cookie;
    }

    public String getSafeDiagnostic() {
        return safeDiagnostic;
    }

    public NoonAuthRecoveryFailureStage getFailureStage() {
        return failureStage;
    }

    public NoonTransientErrorType getTransientErrorType() {
        return transientErrorType;
    }
}
