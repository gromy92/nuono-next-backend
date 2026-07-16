package com.nuono.next.noonauth.gateway;

import java.util.Objects;

public final class NoonAuthRecoveryProjectResult {
    public enum Code {
        RECOVERED,
        PROJECT_ACCESS_DENIED,
        SESSION_CREATE_FAILED,
        COOKIE_VALIDATION_FAILED
    }

    private final NoonAuthRecoveryProjectTarget target;
    private final Code code;
    private final String cookie;
    private final String safeDiagnostic;

    private NoonAuthRecoveryProjectResult(
            NoonAuthRecoveryProjectTarget target,
            Code code,
            String cookie,
            String safeDiagnostic
    ) {
        this.target = Objects.requireNonNull(target, "target must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.cookie = cookie;
        this.safeDiagnostic = safeDiagnostic;
    }

    public static NoonAuthRecoveryProjectResult recovered(
            NoonAuthRecoveryProjectTarget target,
            String cookie
    ) {
        return new NoonAuthRecoveryProjectResult(target, Code.RECOVERED, cookie, "project session verified");
    }

    public static NoonAuthRecoveryProjectResult failed(
            NoonAuthRecoveryProjectTarget target,
            Code code,
            String safeDiagnostic
    ) {
        return new NoonAuthRecoveryProjectResult(target, code, null, safeDiagnostic);
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

    public String getCookie() {
        return cookie;
    }

    public String getSafeDiagnostic() {
        return safeDiagnostic;
    }
}
