package com.nuono.next.productselection;

public interface Ali1688ImageSearchGateway {

    Ali1688ImageSearchResult search(Ali1688ImageSearchRequest request);

    default Ali1688GatewayOperationalStatus getOperationalStatus() {
        return Ali1688GatewayOperationalStatus.unavailable(
                "unknown",
                "unknown",
                false,
                false
        );
    }
}

final class Ali1688GatewayOperationalStatus {

    final String gatewayServiceKind;
    final String sessionState;
    final Boolean runtimeReady;
    final Boolean captchaAutoSolveEnabled;
    final String userFacingStatus;
    final String userFacingMessage;

    private Ali1688GatewayOperationalStatus(
            String gatewayServiceKind,
            String sessionState,
            Boolean runtimeReady,
            Boolean captchaAutoSolveEnabled,
            String userFacingStatus,
            String userFacingMessage
    ) {
        this.gatewayServiceKind = hasText(gatewayServiceKind) ? gatewayServiceKind.trim() : "unknown";
        this.sessionState = hasText(sessionState) ? sessionState.trim() : "unknown";
        this.runtimeReady = runtimeReady;
        this.captchaAutoSolveEnabled = captchaAutoSolveEnabled;
        this.userFacingStatus = userFacingStatus;
        this.userFacingMessage = userFacingMessage;
    }

    static Ali1688GatewayOperationalStatus ready(
            String gatewayServiceKind,
            Boolean runtimeReady,
            Boolean captchaAutoSolveEnabled
    ) {
        return from(
                gatewayServiceKind,
                "ready",
                runtimeReady,
                captchaAutoSolveEnabled
        );
    }

    static Ali1688GatewayOperationalStatus blocked(
            String gatewayServiceKind,
            String sessionState,
            Boolean runtimeReady,
            Boolean captchaAutoSolveEnabled
    ) {
        return from(gatewayServiceKind, sessionState, runtimeReady, captchaAutoSolveEnabled);
    }

    static Ali1688GatewayOperationalStatus unavailable(
            String gatewayServiceKind,
            String sessionState,
            Boolean runtimeReady,
            Boolean captchaAutoSolveEnabled
    ) {
        return from(gatewayServiceKind, sessionState, runtimeReady, captchaAutoSolveEnabled);
    }

    static Ali1688GatewayOperationalStatus from(
            String gatewayServiceKind,
            String sessionState,
            Boolean runtimeReady,
            Boolean captchaAutoSolveEnabled
    ) {
        String normalizedState = hasText(sessionState) ? sessionState.trim() : "unknown";
        String status;
        String message;
        switch (normalizedState) {
            case "ready":
                status = "available";
                message = "1688 自动采集通道正常。";
                break;
            case "login_required":
                status = "login_required";
                message = "1688 登录失效。";
                break;
            case "captcha_required":
                status = "blocked_by_captcha";
                message = "1688 访问受限，系统已暂停自动采集。";
                break;
            case "rate_limited":
                status = "cooling_down";
                message = "1688 访问频繁，系统冷却中。";
                break;
            default:
                status = "unavailable";
                message = "1688 自动采集通道暂不可用。";
                break;
        }
        return new Ali1688GatewayOperationalStatus(
                gatewayServiceKind,
                normalizedState,
                runtimeReady,
                captchaAutoSolveEnabled,
                status,
                message
        );
    }

    boolean isReadyForClaim() {
        return "available".equals(userFacingStatus)
                && "ready".equals(sessionState)
                && Boolean.TRUE.equals(runtimeReady)
                && !Boolean.TRUE.equals(captchaAutoSolveEnabled);
    }

    boolean shouldCooldown() {
        return "login_required".equals(sessionState)
                || "captcha_required".equals(sessionState)
                || "rate_limited".equals(sessionState)
                || "unavailable".equals(userFacingStatus);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
