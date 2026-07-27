package com.nuono.next.productlisting;

import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductListingReauthenticationAttemptProjector {
    private static final String PENDING_REASON = "NOON_AUTH_RECOVERY_PENDING";

    public ProductListingWorkflowView overlay(
            ProductListingWorkflowView workflow,
            ProductListingReauthenticationAttemptRecord attempt
    ) {
        if (workflow == null || attempt == null
                || "COMPLETED".equals(attempt.getStatus())) {
            return workflow;
        }
        String failureCode = "FAILED".equals(attempt.getStatus())
                ? attempt.getFailureCode()
                : terminalFailureCode(attempt);
        return failureCode == null
                ? pending(workflow, attempt.getRecoveryStatus())
                : failed(workflow, failureCode);
    }

    public ProductListingWorkflowView pending(
            ProductListingWorkflowView workflow,
            String recoveryStatus
    ) {
        workflow.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        workflow.setNextAction(
                ProductListingWorkflowView.NextAction.WAIT_FOR_REAUTHENTICATION
        );
        workflow.setReasonCode(PENDING_REASON);
        workflow.setMessage(
                "Noon 邮箱授权正在后台处理"
                        + ("WAITING_COOLDOWN".equals(recoveryStatus)
                        ? "，当前处于安全发送间隔"
                        : "")
                        + "；系统不会自动重放上架。"
        );
        return workflow;
    }

    public ProductListingWorkflowView failed(
            ProductListingWorkflowView workflow,
            String failureCode
    ) {
        workflow.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        workflow.setNextAction(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE
        );
        workflow.setReasonCode(
                "NOON_AUTH_RECOVERY_" + safeCode(failureCode)
        );
        workflow.setMessage(actionableFailureMessage(failureCode));
        return workflow;
    }

    public boolean isRecovered(
            ProductListingReauthenticationAttemptRecord attempt
    ) {
        return attempt != null
                && "PENDING".equals(attempt.getStatus())
                && "RECOVERED".equals(attempt.getRecoveryItemStatus())
                && attempt.getRecoveryItemRecoveredAt() != null
                && "HEALTHY".equals(attempt.getProjectAuthStatus())
                && attempt.getActiveRecoveryId() == null
                && attempt.getCurrentAuthVersion() != null
                && attempt.getRequestedAuthVersion() != null
                && attempt.getCurrentAuthVersion()
                        == attempt.getRequestedAuthVersion() + 1L;
    }

    public String terminalFailureCode(
            ProductListingReauthenticationAttemptRecord attempt
    ) {
        if (attempt == null) {
            return "ATTEMPT_NOT_FOUND";
        }
        if (attempt.getCurrentAuthVersion() != null
                && attempt.getRequestedAuthVersion() != null
                && attempt.getCurrentAuthVersion()
                        > attempt.getRequestedAuthVersion() + 1L) {
            return "AUTH_EPOCH_ADVANCED";
        }
        if (oneOf(
                attempt.getRecoveryItemStatus(),
                "FAILED",
                "STALE",
                "SKIPPED"
        )) {
            return firstNonBlank(
                    attempt.getRecoveryItemFailureCode(),
                    "PROJECT_RECOVERY_FAILED"
            );
        }
        if ("MANUAL_HOLD".equals(attempt.getProjectAuthStatus())
                || oneOf(
                        attempt.getRecoveryStatus(),
                        "MANUAL_HOLD",
                        "FAILED_FINAL",
                        "CANCELLED"
                )) {
            return firstNonBlank(
                    attempt.getRecoveryItemFailureCode(),
                    attempt.getRecoveryFailureCode(),
                    attempt.getRecoveryStatus(),
                    "PROJECT_RECOVERY_FAILED"
            );
        }
        if ("COMPLETED".equals(attempt.getRecoveryStatus())
                && !"RECOVERED".equals(attempt.getRecoveryItemStatus())) {
            return "PROJECT_RESULT_MISSING";
        }
        return null;
    }

    private String actionableFailureMessage(String failureCode) {
        if ("MAILBOX_AUTH_FAILED".equals(failureCode)) {
            return "共享邮箱认证失败，请更新邮箱授权码后重新授权；系统未重放上架。";
        }
        if ("MAILBOX_UNAVAILABLE".equals(failureCode)) {
            return "共享邮箱暂不可用，请检查 IMAP 和可信发件域名配置后重新授权；系统未重放上架。";
        }
        if ("SEND_RATE_LIMITED".equals(failureCode)) {
            return "Noon 验证码发送受限，请等待安全间隔后重新授权；系统未重放上架。";
        }
        if ("AUTH_EPOCH_ADVANCED".equals(failureCode)) {
            return "Project 授权版本已再次变化，请重新关联本次上架授权；系统未重放上架。";
        }
        return "Noon 邮箱授权恢复未完成，请处理授权配置后重试；系统未重放上架。";
    }

    private boolean oneOf(String value, String... candidates) {
        if (value == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String safeCode(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replaceAll("[^A-Za-z0-9_]", "_")
                        .toUpperCase(Locale.ROOT)
                : "FAILED";
    }
}
