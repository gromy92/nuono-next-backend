package com.nuono.next.product;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonAccountSessionAttentionPort;
import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noonauth.NoonAuthRecoveryTriggerPolicy;
import com.nuono.next.noonauth.NoonAuthRetrySuppressedException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import com.nuono.next.product.ProductWriteAuthTaskContext.TaskIdentity;
import com.nuono.next.noonpull.NoonPullProjectAuthGate;
import com.nuono.next.product.noon.NoonProductErrorCode;
import com.nuono.next.product.noon.NoonProductException;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
@Component
@Profile("local-db")
public class ProductWriteAuthRecovery {
    private final NoonAuthWaitQueue recoveryQueue;
    private final NoonPullProjectAuthGate authGate;
    private final ProductWriteAuthTaskContext taskContext;
    private StoreSyncMapper storeSyncMapper;
    @Autowired
    public ProductWriteAuthRecovery(
            NoonAuthWaitQueue recoveryQueue,
            NoonPullProjectAuthGate authGate
    ) {
        this.recoveryQueue = recoveryQueue;
        this.authGate = authGate;
        this.taskContext = new ProductWriteAuthTaskContext(recoveryQueue, this::canonicalProjectCode);
    }
    public ProductWriteAuthRecovery(NoonAccountSessionAttentionPort attention) {
        this(
                request -> {
                    if (attention != null) {
                        attention.requireManualLogin();
                    }
                    return Optional.empty();
                },
                (ownerUserId, projectCode) -> attention != null && attention.blocksProviderCalls()
        );
    }
    public static ProductWriteAuthRecovery disabled() {
        return new ProductWriteAuthRecovery(
                request -> Optional.empty(),
                (ownerUserId, projectCode) -> false
        );
    }
    @Autowired(required = false)
    public void setStoreSyncMapper(StoreSyncMapper storeSyncMapper) {
        this.storeSyncMapper = storeSyncMapper;
    }
    public void requireAvailable(Long ownerUserId, String projectCode) {
        requireAvailable(ownerUserId, projectCode, projectCode);
    }
    public void requireAvailable(Long ownerUserId, String projectCode, String storeCode) {
        String canonicalProjectCode = canonicalProjectCode(ownerUserId, projectCode, storeCode);
        if (authGate == null
                || ownerUserId == null
                || !StringUtils.hasText(canonicalProjectCode)
                || !authGate.isBlocked(ownerUserId, canonicalProjectCode)) {
            return;
        }
        throw pendingException(null, false, true, null);
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
            return pendingException(existing.getRecoveryId(), true, true, existing);
        }
        if (!isExplicitAuthFailure(failure)) {
            return null;
        }
        String canonicalProjectCode = canonicalProjectCode(ownerUserId, projectCode, storeCode);
        Long recoveryId = null;
        boolean recoveryQueued = false;
        if (recoveryQueue != null
                && ownerUserId != null
                && StringUtils.hasText(canonicalProjectCode)
                && StringUtils.hasText(storeCode)) {
            try {
                TaskIdentity currentTask = scopedTask;
                Optional<Long> queued;
                if (currentTask != null) {
                    queued = taskContext.enqueue(
                            currentTask,
                            canonicalProjectCode,
                            effectiveWriteMayHaveOccurred
                    );
                } else if (taskIdentity != null && taskIdentity.sourceTaskId != null) {
                    queued = taskContext.enqueue(
                            taskIdentity, canonicalProjectCode, effectiveWriteMayHaveOccurred
                    );
                } else {
                    queued = recoveryQueue.enqueue(NoonAuthWaitRequest.binding(
                                ownerUserId,
                                canonicalProjectCode,
                                storeCode.trim()
                    ));
                }
                recoveryId = queued.orElse(null);
                recoveryQueued = queued.isPresent();
            } catch (NoonAuthRetrySuppressedException suppressed) {
                throw suppressed;
            } catch (RuntimeException ignored) {
                recoveryQueued = false;
            }
        }
        return pendingException(recoveryId, effectiveWriteMayHaveOccurred, recoveryQueued, failure);
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
        return taskContext.enqueue(task, checkpoint, writeMayHaveOccurred);
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
    private String canonicalProjectCode(Long ownerUserId, String projectCode, String storeCode) {
        String fallback = StringUtils.hasText(projectCode) ? projectCode.trim() : null;
        if (storeSyncMapper == null) {
            return fallback;
        }
        if (ownerUserId == null || !StringUtils.hasText(storeCode)) {
            throw new IllegalStateException("无法校验 Noon 授权范围：缺少 owner 或 storeCode。");
        }
        String normalizedStoreCode = storeCode.trim();
        StoreSyncStoreRecord localProject;
        try {
            localProject = storeSyncMapper.selectOwnerProject(ownerUserId, normalizedStoreCode);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("无法校验 Noon 授权范围：本地店铺映射查询失败。", exception);
        }
        if (localProject == null || !StringUtils.hasText(localProject.getProjectCode())) {
            throw new IllegalStateException("无法校验 Noon 授权范围：本地店铺映射不存在。");
        }
        return localProject.getProjectCode().trim();
    }
    private ProductWriteAuthRequiredException pendingException(
            Long recoveryId,
            boolean writeMayHaveOccurred,
            boolean recoveryQueued,
            Throwable cause
    ) {
        StringBuilder message = new StringBuilder("Noon Project 授权恢复中");
        if (recoveryId != null) {
            message.append("；recoveryId=").append(recoveryId);
        } else if (!recoveryQueued) {
            message.append("；恢复队列暂未返回记录，请在店铺管理中确认授权状态");
        }
        if (writeMayHaveOccurred) {
            message.append("。本次操作已进入写入阶段，恢复后请先回读 Noon 结果，再人工决定是否继续");
        } else {
            message.append("。业务任务进入授权等待队列，恢复成功后将从安全检查点自动继续");
        }
        return new ProductWriteAuthRequiredException(
                recoveryId,
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
