package com.nuono.next.product;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.StringUtils;

public class ProductPublishTaskLifecycleService {

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "queued",
            "running",
            "submitted",
            "verifying",
            "pending_effective",
            "write_unknown",
            "verify_timeout"
    );
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "synced",
            "failed",
            "cancelled",
            "pending_manual_check"
    );

    private final ProductManagementMapper productManagementMapper;

    public ProductPublishTaskLifecycleService(ProductManagementMapper productManagementMapper) {
        this.productManagementMapper = productManagementMapper;
    }

    public ProductPublishTaskRecord loadTask(Long taskId, Long ownerUserId, int staleRunningMinutes) {
        if (taskId == null) {
            throw new IllegalArgumentException("缺少发布任务 ID。");
        }
        recoverStaleRunningTasks(staleRunningMinutes);
        ProductPublishTaskRecord task = productManagementMapper.selectProductPublishTaskById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("发布任务不存在或已删除。");
        }
        ensureTaskOwner(task, ownerUserId);
        return task;
    }

    public ProductPublishTaskRecord retryTask(Long taskId, Long ownerUserId, int staleRunningMinutes) {
        ProductPublishTaskRecord task = loadTask(taskId, ownerUserId, staleRunningMinutes);
        ProductPublishTaskRecord activeTask = task.getProductMasterId() == null
                ? null
                : productManagementMapper.selectActiveProductPublishTask(task.getProductMasterId());
        if (activeTask != null && !Objects.equals(activeTask.getId(), taskId)) {
            throw new IllegalStateException("当前商品已有发布任务正在执行，请等待完成后再重试。");
        }
        int updated;
        try {
            updated = productManagementMapper.retryProductPublishTask(taskId, task.getOwnerUserId());
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("当前商品已有发布任务正在执行，请等待完成后再重试。", exception);
        }
        if (updated <= 0) {
            throw new IllegalStateException("当前发布任务不能重试，可能已超过重试次数或状态已变化。");
        }
        ProductPublishTaskRecord freshTask = productManagementMapper.selectProductPublishTaskById(taskId);
        return freshTask == null ? task : freshTask;
    }

    public ProductPublishTaskRecord cancelTask(Long taskId, Long ownerUserId, int staleRunningMinutes) {
        ProductPublishTaskRecord task = loadTask(taskId, ownerUserId, staleRunningMinutes);
        int updated = productManagementMapper.cancelQueuedProductPublishTask(taskId, task.getOwnerUserId());
        if (updated <= 0) {
            throw new IllegalStateException("只有尚未执行的发布任务可以取消。");
        }
        ProductPublishTaskRecord freshTask = productManagementMapper.selectProductPublishTaskById(taskId);
        return freshTask == null ? task : freshTask;
    }

    public CreateQueuedPublishTaskResult createQueuedTask(CreateQueuedPublishTaskRequest request) {
        if (request == null || request.getProductMasterId() == null) {
            throw new IllegalStateException("本地商品主档还没有落库，暂时不能创建发布任务。");
        }
        ProductPublishTaskRecord activeTask = productManagementMapper.selectActiveProductPublishTask(request.getProductMasterId());
        if (activeTask != null) {
            return CreateQueuedPublishTaskResult.active(activeTask);
        }

        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(productManagementMapper.nextProductPublishTaskId());
        task.setOwnerUserId(request.getOwnerUserId());
        task.setProductMasterId(request.getProductMasterId());
        task.setStoreCode(normalize(request.getStoreCode()));
        task.setProjectCode(normalize(request.getProjectCode()));
        task.setSkuParent(normalize(request.getSkuParent()));
        task.setPartnerSku(normalize(request.getPartnerSku()));
        task.setPskuCode(normalize(request.getPskuCode()));
        task.setCurrentSiteCode(normalize(request.getCurrentSiteCode()));
        task.setTaskType("publish-current");
        task.setStatus("queued");
        task.setActiveLockKey(activeLockKey(request.getProductMasterId()));
        task.setDraftJson(request.getDraftJson());
        task.setBaselineJson(request.getBaselineJson());
        task.setDraftHash(request.getDraftHash());
        task.setChangedDomainsJson(request.getChangedDomainsJson());
        task.setRequestJson(request.getRequestJson());
        task.setIdempotencyKey(idempotencyKey(request));
        task.setRetryCount(0);
        task.setVerifyAttemptCount(0);
        task.setMaxRetryCount(3);
        task.setVersionNo(1);
        try {
            productManagementMapper.insertProductPublishTask(task);
            return CreateQueuedPublishTaskResult.created(task);
        } catch (DuplicateKeyException exception) {
            ProductPublishTaskRecord duplicateTask =
                    productManagementMapper.selectProductPublishTaskByIdempotency(task.getIdempotencyKey());
            if (duplicateTask == null) {
                duplicateTask = productManagementMapper.selectActiveProductPublishTask(request.getProductMasterId());
            }
            if (duplicateTask == null) {
                throw exception;
            }
            return CreateQueuedPublishTaskResult.duplicate(duplicateTask, task.getIdempotencyKey());
        }
    }

    public ProductPublishTaskRecord findActiveTask(Long productMasterId) {
        return productMasterId == null ? null : productManagementMapper.selectActiveProductPublishTask(productMasterId);
    }

    public List<ProductPublishTaskRecord> selectRunnableTasks(int limit) {
        List<ProductPublishTaskRecord> tasks =
                productManagementMapper.selectRunnableProductPublishTasks(Math.max(1, limit));
        return tasks == null ? Collections.emptyList() : tasks;
    }

    public boolean claimTask(ProductPublishTaskRecord task, String lockedBy) {
        if (task == null || task.getId() == null || !StringUtils.hasText(task.getStatus())) {
            return false;
        }
        int updated = productManagementMapper.tryStartProductPublishTask(
                task.getId(),
                task.getStatus(),
                taskVersionNo(task),
                lockedBy,
                task.getOwnerUserId()
        );
        if (updated <= 0) {
            return false;
        }
        task.setStatus("running");
        task.setLockedBy(lockedBy);
        task.setLockedAt(LocalDateTime.now());
        task.setVersionNo(taskVersionNo(task) + 1);
        return true;
    }

    public int recoverStaleRunningTasks(int staleRunningMinutes) {
        return productManagementMapper.recoverStaleRunningProductPublishTasks(
                Math.max(1, staleRunningMinutes),
                0L
        );
    }

    public void updateTaskStatus(ProductPublishTaskRecord task, StatusUpdate update) {
        if (task == null || update == null || !StringUtils.hasText(update.getStatus())) {
            throw new IllegalArgumentException("缺少发布任务状态更新信息。");
        }
        LocalDateTime now = LocalDateTime.now();
        String status = update.getStatus();
        String expectedStatus = task.getStatus();
        String expectedLockedBy = task.getLockedBy();
        int expectedVersionNo = taskVersionNo(task);
        boolean releaseLock = shouldReleaseLock(status);
        LocalDateTime resolvedFinishedAt = update.getFinishedAt();
        if (resolvedFinishedAt == null && isTerminalStatus(status)) {
            resolvedFinishedAt = now;
        }
        int updated = productManagementMapper.updateProductPublishTaskStatus(
                task.getId(),
                expectedStatus,
                expectedLockedBy,
                expectedVersionNo,
                status,
                update.getResultJson(),
                update.getErrorCode(),
                update.getErrorMessage(),
                update.getNextRunAt(),
                "submitted".equalsIgnoreCase(status) ? now : null,
                "verifying".equalsIgnoreCase(status) ? now : null,
                isTerminalStatus(status) || "pending_effective".equalsIgnoreCase(status)
                        || "verify_timeout".equalsIgnoreCase(status)
                        || "pending_manual_check".equalsIgnoreCase(status) ? now : null,
                resolvedFinishedAt,
                update.getVerifyAttemptCount(),
                releaseLock,
                task.getOwnerUserId()
        );
        if (updated <= 0) {
            throw new ProductPublishTaskFenceLostException(task, status);
        }
        task.setStatus(status);
        task.setErrorCode(update.getErrorCode());
        task.setErrorMessage(update.getErrorMessage());
        task.setResultJson(update.getResultJson());
        task.setNextRunAt(update.getNextRunAt());
        task.setFinishedAt(resolvedFinishedAt);
        task.setVersionNo(expectedVersionNo + 1);
        if (releaseLock) {
            task.setLockedBy(null);
            task.setLockedAt(null);
        }
        if (update.getVerifyAttemptCount() != null) {
            task.setVerifyAttemptCount(update.getVerifyAttemptCount());
        }
    }

    public boolean isTerminalStatus(String status) {
        return TERMINAL_STATUSES.contains(normalize(status));
    }

    public boolean isActiveStatus(String status) {
        return ACTIVE_STATUSES.contains(normalize(status));
    }

    public boolean shouldReleaseLock(String status) {
        return !"submitted".equalsIgnoreCase(status) && !"verifying".equalsIgnoreCase(status);
    }

    private void ensureTaskOwner(ProductPublishTaskRecord task, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取发布任务。");
        }
        if (task == null || !Objects.equals(task.getOwnerUserId(), ownerUserId)) {
            throw new IllegalArgumentException("当前发布任务不属于选中的老板上下文。");
        }
    }

    private String idempotencyKey(CreateQueuedPublishTaskRequest request) {
        return request.getProductMasterId() + ":"
                + normalize(request.getDraftHash()) + ":"
                + normalize(request.getCurrentSiteCode());
    }

    private String activeLockKey(Long productMasterId) {
        return productMasterId == null ? null : "product:" + productMasterId;
    }

    private int taskVersionNo(ProductPublishTaskRecord task) {
        return task != null && task.getVersionNo() != null ? task.getVersionNo() : 0;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public static class CreateQueuedPublishTaskRequest {

        private Long ownerUserId;
        private Long productMasterId;
        private String storeCode;
        private String projectCode;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String currentSiteCode;
        private String baselineJson;
        private String draftJson;
        private String draftHash;
        private String changedDomainsJson;
        private String requestJson;

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getProductMasterId() {
            return productMasterId;
        }

        public void setProductMasterId(Long productMasterId) {
            this.productMasterId = productMasterId;
        }

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getProjectCode() {
            return projectCode;
        }

        public void setProjectCode(String projectCode) {
            this.projectCode = projectCode;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public void setSkuParent(String skuParent) {
            this.skuParent = skuParent;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public void setPartnerSku(String partnerSku) {
            this.partnerSku = partnerSku;
        }

        public String getPskuCode() {
            return pskuCode;
        }

        public void setPskuCode(String pskuCode) {
            this.pskuCode = pskuCode;
        }

        public String getCurrentSiteCode() {
            return currentSiteCode;
        }

        public void setCurrentSiteCode(String currentSiteCode) {
            this.currentSiteCode = currentSiteCode;
        }

        public String getBaselineJson() {
            return baselineJson;
        }

        public void setBaselineJson(String baselineJson) {
            this.baselineJson = baselineJson;
        }

        public String getDraftJson() {
            return draftJson;
        }

        public void setDraftJson(String draftJson) {
            this.draftJson = draftJson;
        }

        public String getDraftHash() {
            return draftHash;
        }

        public void setDraftHash(String draftHash) {
            this.draftHash = draftHash;
        }

        public String getChangedDomainsJson() {
            return changedDomainsJson;
        }

        public void setChangedDomainsJson(String changedDomainsJson) {
            this.changedDomainsJson = changedDomainsJson;
        }

        public String getRequestJson() {
            return requestJson;
        }

        public void setRequestJson(String requestJson) {
            this.requestJson = requestJson;
        }
    }

    public static class CreateQueuedPublishTaskResult {

        private final ProductPublishTaskRecord task;
        private final boolean created;
        private final boolean duplicate;
        private final String idempotencyKey;

        private CreateQueuedPublishTaskResult(
                ProductPublishTaskRecord task,
                boolean created,
                boolean duplicate,
                String idempotencyKey
        ) {
            this.task = task;
            this.created = created;
            this.duplicate = duplicate;
            this.idempotencyKey = idempotencyKey;
        }

        private static CreateQueuedPublishTaskResult created(ProductPublishTaskRecord task) {
            return new CreateQueuedPublishTaskResult(task, true, false, task == null ? null : task.getIdempotencyKey());
        }

        private static CreateQueuedPublishTaskResult active(ProductPublishTaskRecord task) {
            return new CreateQueuedPublishTaskResult(task, false, false, task == null ? null : task.getIdempotencyKey());
        }

        private static CreateQueuedPublishTaskResult duplicate(ProductPublishTaskRecord task, String idempotencyKey) {
            return new CreateQueuedPublishTaskResult(task, false, true, idempotencyKey);
        }

        public ProductPublishTaskRecord getTask() {
            return task;
        }

        public boolean isCreated() {
            return created;
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public String getIdempotencyKey() {
            return idempotencyKey;
        }
    }

    public static class StatusUpdate {

        private String status;
        private String errorCode;
        private String errorMessage;
        private String resultJson;
        private LocalDateTime nextRunAt;
        private LocalDateTime finishedAt;
        private Integer verifyAttemptCount;

        public static StatusUpdate to(String status) {
            StatusUpdate update = new StatusUpdate();
            update.setStatus(status);
            return update;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getResultJson() {
            return resultJson;
        }

        public void setResultJson(String resultJson) {
            this.resultJson = resultJson;
        }

        public LocalDateTime getNextRunAt() {
            return nextRunAt;
        }

        public void setNextRunAt(LocalDateTime nextRunAt) {
            this.nextRunAt = nextRunAt;
        }

        public LocalDateTime getFinishedAt() {
            return finishedAt;
        }

        public void setFinishedAt(LocalDateTime finishedAt) {
            this.finishedAt = finishedAt;
        }

        public Integer getVerifyAttemptCount() {
            return verifyAttemptCount;
        }

        public void setVerifyAttemptCount(Integer verifyAttemptCount) {
            this.verifyAttemptCount = verifyAttemptCount;
        }
    }
}
