package com.nuono.next.product;

import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonauth.NoonAuthRecoveryTriggerPolicy;
import com.nuono.next.product.ProductWriteAuthTaskContext.TaskIdentity;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductWriteAuthRecovery {
    private final NoonAccountSessionAttentionPort accountSessionAttention;
    private final ProductWriteAuthTaskContext taskContext;
    public ProductWriteAuthRecovery(
            NoonAccountSessionAttentionPort accountSessionAttention
    ) {
        this.accountSessionAttention = accountSessionAttention;
        this.taskContext = new ProductWriteAuthTaskContext();
    }
    public static ProductWriteAuthRecovery disabled() {
        return new ProductWriteAuthRecovery(new NoonAccountSessionAttentionPort() {
            @Override
            public void requireManualLogin() {
                // Intentionally inert outside the local-db runtime.
            }

            @Override
            public boolean blocksProviderCalls() {
                return false;
            }
        });
    }
    public void requireAvailable(Long ownerUserId, String projectCode) {
        requireAvailable(ownerUserId, projectCode, projectCode);
    }
    public void requireAvailable(Long ownerUserId, String projectCode, String storeCode) {
        if (accountSessionAttention == null || !accountSessionAttention.blocksProviderCalls()) {
            return;
        }
        throw pendingException(false, null);
    }
    public ProductWriteAuthRequiredException suspendIfAuthFailure(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            Throwable failure,
            boolean writeMayHaveOccurred
    ) {
        return suspendIfAuthFailure(
                ownerUserId,
                projectCode,
                storeCode,
                failure,
                writeMayHaveOccurred,
                null
        );
    }
    public ProductWriteAuthRequiredException suspendTaskIfAuthFailure(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String sourceDomain,
            Long sourceTaskId,
            String checkpoint,
            Throwable failure,
            boolean writeMayHaveOccurred
    ) {
        return suspendIfAuthFailure(
                ownerUserId,
                projectCode,
                storeCode,
                failure,
                writeMayHaveOccurred,
                taskContext.identity(
                        ownerUserId,
                        projectCode,
                        storeCode,
                        siteCode,
                        sourceDomain,
                        sourceTaskId,
                        checkpoint,
                        false
                )
        );
    }

    private ProductWriteAuthRequiredException suspendIfAuthFailure(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            Throwable failure,
            boolean writeMayHaveOccurred,
            TaskIdentity taskIdentity
    ) {
        TaskIdentity scopedTask = taskContext.current();
        boolean effectiveWriteMayHaveOccurred = writeMayHaveOccurred
                || (scopedTask != null && scopedTask.forceReadback);
        ProductWriteAuthRequiredException existing = ProductWriteAuthRequiredException.find(failure);
        if (existing != null) {
            if (!effectiveWriteMayHaveOccurred || existing.isWriteMayHaveOccurred()) {
                return existing;
            }
            return pendingException(true, existing);
        }
        if (!isExplicitAuthFailure(failure)) {
            return null;
        }

        if (accountSessionAttention != null) {
            accountSessionAttention.requireManualLogin();
        }
        return pendingException(effectiveWriteMayHaveOccurred, failure);
    }
    public TaskScope openTaskScope(ProductPublishTaskRecord task) {
        return taskContext.open(task);
    }
    public TaskScope openTaskScope(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String sourceDomain,
            Long sourceTaskId,
            String checkpoint
    ) {
        return taskContext.open(
                ownerUserId, projectCode, storeCode, siteCode,
                sourceDomain, sourceTaskId, checkpoint, false
        );
    }

    public TaskScope openTaskScope(
            Long ownerUserId,
            String projectCode,
            String storeCode,
            String siteCode,
            String sourceDomain,
            Long sourceTaskId,
            String checkpoint,
            boolean forceReadback
    ) {
        return taskContext.open(
                ownerUserId, projectCode, storeCode, siteCode,
                sourceDomain, sourceTaskId, checkpoint, forceReadback
        );
    }

    public Optional<Long> enqueueTask(
            ProductPublishTaskRecord task,
            String checkpoint,
            boolean writeMayHaveOccurred
    ) {
        if (accountSessionAttention != null) {
            accountSessionAttention.requireManualLogin();
        }
        return Optional.empty();
    }

    public boolean isExplicitAuthFailure(Throwable failure) {
        if (NoonAuthenticationFailureClassifier
                .hasPermanentAuthenticationFailureEvidence(failure)) {
            return false;
        }
        boolean authFailure =
                NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure);
        boolean ambiguousHttpStatus = false;
        StringBuilder details = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (current instanceof NoonProductException) {
                NoonProductErrorCode code = ((NoonProductException) current).getCode();
                if (code == NoonProductErrorCode.NOON_PROJECT_SCOPE_MISSING
                        || code == NoonProductErrorCode.NOON_CREDENTIAL_INVALID) {
                    return false;
                }
                if (code == NoonProductErrorCode.NOON_AUTH_REQUIRED) {
                    authFailure = true;
                }
            }
            if (current instanceof NoonHttpException) {
                NoonHttpException httpFailure = (NoonHttpException) current;
                if (httpFailure.getStatusCode() != 401) {
                    ambiguousHttpStatus = true;
                }
                if (StringUtils.hasText(httpFailure.getResponseBody())) {
                    details.append(' ').append(httpFailure.getResponseBody());
                }
            }
            if (StringUtils.hasText(current.getMessage())) {
                details.append(' ').append(current.getMessage());
            }
            current = current.getCause();
        }
        return authFailure
                || (!ambiguousHttpStatus
                && NoonAuthRecoveryTriggerPolicy.isExplicitAuthExpiry(details.toString()));
    }

    private ProductWriteAuthRequiredException pendingException(
            boolean writeMayHaveOccurred,
            Throwable cause
    ) {
        StringBuilder message = new StringBuilder("Noon 共享账号需要人工登录");
        if (writeMayHaveOccurred) {
            message.append("。本次操作已进入写入阶段，人工登录后请先回读 Noon 结果，再由人工决定是否继续");
        } else {
            message.append("。系统不会自动发送验证码、重试或继续当前业务任务");
        }
        return new ProductWriteAuthRequiredException(
                null,
                writeMayHaveOccurred,
                message.append("。").toString(),
                cause
        );
    }

    @FunctionalInterface
    public interface TaskScope extends AutoCloseable {
        @Override
        void close();
    }

}
