package com.nuono.next.product.publish;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductMasterWorkbenchView;
import com.nuono.next.product.ProductPublishTaskRecord;
import com.nuono.next.product.ProductPublishTaskView;
import com.nuono.next.product.noon.NoonProductException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductPublishCommandService {
    private static final Logger log = LoggerFactory.getLogger(ProductPublishCommandService.class);

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "queued",
            "running",
            "submitted",
            "verifying",
            "pending_effective",
            "write_unknown",
            "verify_timeout",
            "write_retry_scheduled"
    );
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "synced",
            "failed",
            "cancelled",
            "pending_manual_check"
    );
    private static final Set<Integer> RETRYABLE_NOON_WRITE_HTTP_STATUSES = Set.of(408, 429, 500, 502, 503, 504);
    private static final Pattern HTTP_STATUS_PATTERN = Pattern.compile("\\bHTTP\\s+(\\d{3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> NON_RETRYABLE_NOON_AUTH_MARKERS = Set.of(
            "invalid username or password",
            "password validate",
            "invalid password",
            "bad credentials",
            "账号或密码错误"
    );

    private final ProductManagementMapper productManagementMapper;

    @Value("${nuono.product-management.publish-task.default-poll-after-millis:2000}")
    private long defaultPollAfterMillis;

    @Value("${nuono.product-management.publish-task.scheduler.stale-running-minutes:15}")
    private int staleRunningMinutes;

    @Value("${nuono.product-management.publish-task.scheduler.legacy-retryable-failed-recovery-hours:24}")
    private int legacyRetryableFailedRecoveryHours;

    @Value("${nuono.product-management.publish-task.transient-automatic-max-retry-count:48}")
    private int transientAutomaticMaxRetryCount = 48;

    public ProductPublishCommandService(ProductManagementMapper productManagementMapper) {
        this.productManagementMapper = productManagementMapper;
    }

    public ProductPublishTaskView loadTask(
            Long taskId,
            Long ownerUserId,
            Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder,
            Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver
    ) {
        if (taskId == null) {
            throw new IllegalArgumentException("缺少发布任务 ID。");
        }
        recoverStaleRunningTasks();
        ProductPublishTaskRecord task = productManagementMapper.selectProductPublishTaskById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("发布任务不存在或已删除。");
        }
        ensureOwner(task, ownerUserId);
        return buildTaskView(task, true, terminalWorkbenchBuilder, changedDomainsResolver);
    }

    public ProductPublishTaskView retryTask(
            Long taskId,
            Long ownerUserId,
            Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder,
            Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver
    ) {
        recoverStaleRunningTasks();
        ProductPublishTaskRecord task = productManagementMapper.selectProductPublishTaskById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("发布任务不存在或已删除。");
        }
        ensureOwner(task, ownerUserId);
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
        return loadTask(taskId, ownerUserId, terminalWorkbenchBuilder, changedDomainsResolver);
    }

    public ProductPublishTaskView cancelTask(
            Long taskId,
            Long ownerUserId,
            Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder,
            Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver
    ) {
        recoverStaleRunningTasks();
        ProductPublishTaskRecord task = productManagementMapper.selectProductPublishTaskById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("发布任务不存在或已删除。");
        }
        ensureOwner(task, ownerUserId);
        int updated = productManagementMapper.cancelQueuedProductPublishTask(taskId, task.getOwnerUserId());
        if (updated <= 0) {
            throw new IllegalStateException("只有尚未执行的发布任务可以取消。");
        }
        return loadTask(taskId, ownerUserId, terminalWorkbenchBuilder, changedDomainsResolver);
    }

    public ProductPublishTaskRecord selectActiveTask(Long productMasterId) {
        return productMasterId == null ? null : productManagementMapper.selectActiveProductPublishTask(productMasterId);
    }

    public void ensureNoActiveTask(Long productMasterId, String message) {
        recoverStaleRunningTasks();
        ProductPublishTaskRecord activeTask = selectActiveTask(productMasterId);
        if (activeTask != null && isActiveStatus(activeTask.getStatus())) {
            throw new IllegalStateException(message);
        }
    }

    public void ensureNoForegroundBlockingActiveTask(Long productMasterId, String message) {
        recoverStaleRunningTasks();
        ProductPublishTaskRecord activeTask = selectActiveTask(productMasterId);
        if (isForegroundBlockingActiveTask(activeTask)) {
            throw new IllegalStateException(message);
        }
    }

    public boolean isForegroundBlockingActiveTask(ProductPublishTaskRecord task) {
        return task != null
                && isActiveStatus(task.getStatus())
                && !isWriteRetryScheduledStatus(task.getStatus());
    }

    public boolean refreshRetryScheduledTaskDraft(
            ProductPublishTaskRecord task,
            String draftJson,
            String baselineJson,
            String requestJson,
            String changedDomainsJson,
            String draftHash,
            String errorCode,
            String errorMessage
    ) {
        if (task == null || !isWriteRetryScheduledStatus(task.getStatus()) || task.getLockedAt() != null) {
            return false;
        }
        int updated = productManagementMapper.refreshRetryScheduledProductPublishTaskDraft(
                task.getId(),
                versionNo(task),
                draftJson,
                baselineJson,
                requestJson,
                changedDomainsJson,
                draftHash,
                errorCode,
                errorMessage,
                task.getOwnerUserId()
        );
        if (updated <= 0) {
            return false;
        }
        task.setDraftJson(draftJson);
        task.setBaselineJson(baselineJson);
        task.setRequestJson(requestJson);
        task.setChangedDomainsJson(changedDomainsJson);
        task.setDraftHash(draftHash);
        task.setResultJson(null);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setRetryCount(0);
        task.setNextRunAt(LocalDateTime.now());
        task.setFinishedAt(null);
        task.setLockedBy(null);
        task.setLockedAt(null);
        task.setVersionNo(versionNo(task) + 1);
        return true;
    }

    public ProductPublishTaskCreateResult createPublishCurrentTask(ProductPublishTaskCreateCommand command) {
        ProductPublishTaskRecord task = new ProductPublishTaskRecord();
        task.setId(productManagementMapper.nextProductPublishTaskId());
        task.setOwnerUserId(command.getOwnerUserId());
        task.setProductMasterId(command.getProductMasterId());
        task.setStoreCode(normalize(command.getStoreCode()));
        task.setProjectCode(command.getProjectCode());
        task.setSkuParent(command.getSkuParent());
        task.setPartnerSku(command.getPartnerSku());
        task.setPskuCode(command.getPskuCode());
        task.setCurrentSiteCode(normalize(command.getCurrentSiteCode()));
        task.setTaskType("publish-current");
        task.setStatus("queued");
        task.setActiveLockKey(activeLockKey(command.getProductMasterId()));
        task.setDraftJson(command.getDraftJson());
        task.setBaselineJson(command.getBaselineJson());
        task.setDraftHash(command.getDraftHash());
        task.setChangedDomainsJson(command.getChangedDomainsJson());
        task.setRequestJson(command.getRequestJson());
        task.setIdempotencyKey(command.getIdempotencyKey());
        task.setRetryCount(0);
        task.setVerifyAttemptCount(0);
        task.setMaxRetryCount(3);
        task.setVersionNo(1);
        try {
            productManagementMapper.insertProductPublishTask(task);
            return ProductPublishTaskCreateResult.created(task);
        } catch (DuplicateKeyException exception) {
            ProductPublishTaskRecord duplicateTask =
                    productManagementMapper.selectProductPublishTaskByIdempotency(task.getIdempotencyKey());
            if (duplicateTask == null) {
                duplicateTask = selectActiveTask(command.getProductMasterId());
            }
            if (duplicateTask == null) {
                throw exception;
            }
            return ProductPublishTaskCreateResult.duplicate(duplicateTask);
        }
    }

    public int recoverStaleRunningTasks() {
        try {
            int recovered = productManagementMapper.recoverStaleRunningProductPublishTasks(
                    Math.max(1, staleRunningMinutes),
                    0L
            );
            if (recovered > 0) {
                log.warn("product-management recovered stale running publish tasks count={}", recovered);
            }
            int recoveredRetryableFailed = productManagementMapper.recoverRetryableFailedNoonWriteProductPublishTasks(
                    Math.max(1, legacyRetryableFailedRecoveryHours),
                    0L
            );
            if (recoveredRetryableFailed > 0) {
                log.warn(
                        "product-management recovered retryable failed noon write publish tasks count={}",
                        recoveredRetryableFailed
                );
            }
            return recovered + recoveredRetryableFailed;
        } catch (RuntimeException exception) {
            if (isMissingTaskTable(exception)) {
                return 0;
            }
            throw exception;
        }
    }

    public List<ProductPublishTaskRecord> selectRunnableTasks(int limit) {
        recoverStaleRunningTasks();
        return productManagementMapper.selectRunnableProductPublishTasks(Math.max(1, limit));
    }

    public String buildLockToken(ProductPublishTaskRecord task) {
        return "product-publish-task-scheduler:" + (task == null ? null : task.getId()) + ":" + UUID.randomUUID();
    }

    public boolean claimTask(ProductPublishTaskRecord task, String previousStatus, String lockedBy) {
        int claimed = productManagementMapper.tryStartProductPublishTask(
                task.getId(),
                previousStatus,
                versionNo(task),
                lockedBy,
                task.getOwnerUserId()
        );
        if (claimed <= 0) {
            return false;
        }
        task.setStatus("running");
        task.setLockedBy(lockedBy);
        task.setLockedAt(LocalDateTime.now());
        task.setVersionNo(versionNo(task) + 1);
        return true;
    }

    public void updateStatus(
            ProductPublishTaskRecord task,
            String status,
            String errorCode,
            String errorMessage,
            String resultJson,
            LocalDateTime nextRunAt,
            LocalDateTime finishedAt,
            Integer verifyAttemptCount
    ) {
        LocalDateTime now = LocalDateTime.now();
        String expectedStatus = task.getStatus();
        String expectedLockedBy = task.getLockedBy();
        int expectedVersionNo = versionNo(task);
        boolean releaseLock = shouldReleaseLock(status);
        LocalDateTime resolvedFinishedAt = finishedAt;
        if (resolvedFinishedAt == null && isTerminalStatus(status)) {
            resolvedFinishedAt = now;
        }
        int updated = productManagementMapper.updateProductPublishTaskStatus(
                task.getId(),
                expectedStatus,
                expectedLockedBy,
                expectedVersionNo,
                status,
                resultJson,
                errorCode,
                errorMessage,
                nextRunAt,
                "submitted".equalsIgnoreCase(status) ? now : null,
                "verifying".equalsIgnoreCase(status) ? now : null,
                isTerminalStatus(status) || "pending_effective".equalsIgnoreCase(status)
                        || "verify_timeout".equalsIgnoreCase(status)
                        || "pending_manual_check".equalsIgnoreCase(status) ? now : null,
                resolvedFinishedAt,
                verifyAttemptCount,
                releaseLock,
                task.getOwnerUserId()
        );
        if (updated <= 0) {
            throw new ProductPublishTaskFenceLostException(task, status);
        }
        task.setStatus(status);
        task.setErrorCode(errorCode);
        task.setErrorMessage(errorMessage);
        task.setResultJson(resultJson);
        task.setNextRunAt(nextRunAt);
        task.setFinishedAt(resolvedFinishedAt);
        task.setVersionNo(expectedVersionNo + 1);
        if (releaseLock) {
            task.setLockedBy(null);
            task.setLockedAt(null);
        }
        if (verifyAttemptCount != null) {
            task.setVerifyAttemptCount(verifyAttemptCount);
        }
    }

    public ProductPublishTaskView buildTaskView(
            ProductPublishTaskRecord task,
            boolean includeWorkbench,
            Function<ProductPublishTaskRecord, ProductMasterWorkbenchView> terminalWorkbenchBuilder,
            Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver
    ) {
        ProductPublishTaskView view = new ProductPublishTaskView();
        if (task == null) {
            return view;
        }
        List<String> changedDomains = resolveChangedDomains(task, changedDomainsResolver);
        view.setTaskId(task.getId());
        view.setStatus(task.getStatus());
        view.setMessage(message(task, changedDomains));
        view.setChangedDomains(changedDomains);
        view.setRetryCount(task.getRetryCount());
        view.setVerifyAttemptCount(task.getVerifyAttemptCount());
        view.setNextRunAt(task.getNextRunAt());
        view.setFinishedAt(task.getFinishedAt());
        view.setPollAfterMillis(pollAfterMillis(task));
        if (includeWorkbench && isTerminalStatus(task.getStatus()) && terminalWorkbenchBuilder != null) {
            ProductMasterWorkbenchView workbench = terminalWorkbenchBuilder.apply(task);
            if (workbench != null) {
                workbench.setPublishTask(buildTaskView(task, false, null, changedDomainsResolver));
                view.setWorkbench(workbench);
            }
        }
        return view;
    }

    public String message(ProductPublishTaskRecord task, Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver) {
        return message(task, resolveChangedDomains(task, changedDomainsResolver));
    }

    public String message(ProductPublishTaskRecord task, List<String> changedDomains) {
        String status = task == null ? null : normalize(task.getStatus());
        if ("queued".equalsIgnoreCase(status)) {
            return "发布已排队，等待后台执行。";
        }
        if ("running".equalsIgnoreCase(status)) {
            return "正在提交 Noon。";
        }
        if ("submitted".equalsIgnoreCase(status)) {
            return "发布已提交，等待回读校验。";
        }
        if ("verifying".equalsIgnoreCase(status)) {
            return "正在校验 Noon 结果。";
        }
        if ("pending_effective".equalsIgnoreCase(status)) {
            return "Noon 可能延迟生效，系统将继续回读校验。";
        }
        if ("write_unknown".equalsIgnoreCase(status)) {
            return "Noon 写入请求超时，系统只回读校验，不会自动重复写入。";
        }
        if ("write_retry_scheduled".equalsIgnoreCase(status)) {
            return "发布正在后台处理，系统会自动核对 Noon 结果。";
        }
        if ("verify_timeout".equalsIgnoreCase(status)) {
            return "Noon 回读校验超时，系统稍后继续核对。";
        }
        if ("pending_manual_check".equalsIgnoreCase(status)) {
            String changedDomainText = changedDomainText(changedDomains);
            String targetText = StringUtils.hasText(changedDomainText)
                    ? "【" + changedDomainText + "】"
                    : "本次修改";
            return "Noon 多轮回读仍未确认" + targetText + "已生效。诺诺草稿已保留，请在官方后台核对后选择重试发布或从 Noon 同步。";
        }
        if ("synced".equalsIgnoreCase(status)) {
            return "发布已完成，本地基线已更新。";
        }
        if ("failed".equalsIgnoreCase(status)) {
            if ("publish_conflict".equalsIgnoreCase(normalize(task.getErrorCode()))) {
                return "该发布任务按旧冲突规则失败，诺诺草稿已保留。请重新点击发布当前修改，系统会按本地草稿覆盖 Noon 对应字段。";
            }
            return firstNonBlank(task.getErrorMessage(), "发布失败，诺诺草稿已保留。");
        }
        if ("cancelled".equalsIgnoreCase(status)) {
            return "发布任务已取消。";
        }
        return firstNonBlank(task != null ? task.getErrorMessage() : null, "发布任务状态已更新。");
    }

    public boolean isRetryableNoonWriteFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoonProductException && ((NoonProductException) current).isRetryable()) {
                return true;
            }
            current = current.getCause();
        }
        String details = throwableDetails(throwable);
        Matcher matcher = HTTP_STATUS_PATTERN.matcher(details);
        while (matcher.find()) {
            try {
                int status = Integer.parseInt(matcher.group(1));
                if (RETRYABLE_NOON_WRITE_HTTP_STATUSES.contains(status)) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // continue scanning other status fragments
            }
        }
        return false;
    }

    public boolean isRetryableNoonRequestFailure(Throwable throwable) {
        if (isNonRetryableNoonAuthFailure(throwable)) {
            return false;
        }
        if (isRetryableNoonWriteFailure(throwable)) {
            return true;
        }
        return isNoonAccessDeniedTransientFailure(throwable);
    }

    private boolean isNonRetryableNoonAuthFailure(Throwable throwable) {
        String details = throwableDetails(throwable).toLowerCase(Locale.ROOT);
        for (String marker : NON_RETRYABLE_NOON_AUTH_MARKERS) {
            if (details.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean isNoonAccessDeniedTransientFailure(Throwable throwable) {
        String details = throwableDetails(throwable).toLowerCase(Locale.ROOT);
        return details.contains("http 403")
                && details.contains("access denied")
                && details.contains("you don't have permission to access")
                && (details.contains("login")
                || details.contains("noon.partners")
                || details.contains("http&#58;")
                || details.contains("&#47;&#47;login"));
    }

    public boolean scheduleNoonWriteRetryOrManualCheck(
            ProductPublishTaskRecord task,
            String errorCode,
            String errorMessage,
            String resultJson
    ) {
        return scheduleNoonRetryOrManualCheck(
                task,
                errorCode,
                errorMessage,
                "noon_write_retry_exhausted",
                "Noon 多次返回系统错误，系统已停止自动重试，诺诺草稿已保留。",
                resultJson
        );
    }

    public boolean scheduleNoonRetryOrManualCheck(
            ProductPublishTaskRecord task,
            String errorCode,
            String scheduledMessage,
            String exhaustedErrorCode,
            String exhaustedMessage,
            String resultJson
    ) {
        if (hasRemainingAutomaticWriteRetries(task)) {
            updateStatus(
                    task,
                    "write_retry_scheduled",
                    errorCode,
                    scheduledMessage,
                    resultJson,
                    nextAutomaticWriteRetryRunAt(task),
                    null,
                    null
            );
            task.setRetryCount(retryCount(task) + 1);
            return true;
        }
        updateStatus(
                task,
                "pending_manual_check",
                exhaustedErrorCode,
                exhaustedMessage,
                resultJson,
                null,
                LocalDateTime.now(),
                null
        );
        return false;
    }

    public boolean isTerminalStatus(String status) {
        return TERMINAL_STATUSES.contains(normalize(status));
    }

    public boolean isActiveStatus(String status) {
        return ACTIVE_STATUSES.contains(normalize(status));
    }

    public LocalDateTime nextVerifyRunAt(ProductPublishTaskRecord task) {
        int attempts = task != null && task.getVerifyAttemptCount() != null ? task.getVerifyAttemptCount() : 0;
        int seconds = attempts <= 0 ? 10 : attempts == 1 ? 30 : 120;
        return LocalDateTime.now().plusSeconds(seconds);
    }

    public boolean isWriteRetryScheduledStatus(String status) {
        return "write_retry_scheduled".equalsIgnoreCase(normalize(status));
    }

    public boolean isMissingTaskTable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains("product_publish_task")
                    && (message.contains("doesn't exist") || message.contains("does not exist"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void ensureOwner(ProductPublishTaskRecord task, Long ownerUserId) {
        if (ownerUserId == null) {
            throw new IllegalArgumentException("缺少老板上下文，暂时不能读取发布任务。");
        }
        if (task == null || !Objects.equals(task.getOwnerUserId(), ownerUserId)) {
            throw new IllegalArgumentException("当前发布任务不属于选中的老板上下文。");
        }
    }

    private long pollAfterMillis(ProductPublishTaskRecord task) {
        if (task == null || task.getNextRunAt() == null) {
            return defaultPollAfterMillis;
        }
        long delay = Duration.between(LocalDateTime.now(), task.getNextRunAt()).toMillis();
        return Math.max(defaultPollAfterMillis, Math.min(delay, 15000L));
    }

    private boolean shouldReleaseLock(String status) {
        return !"submitted".equalsIgnoreCase(status) && !"verifying".equalsIgnoreCase(status);
    }

    public boolean hasRemainingAutomaticWriteRetries(ProductPublishTaskRecord task) {
        return retryCount(task) < effectiveTransientMaxRetryCount(task);
    }

    private int retryCount(ProductPublishTaskRecord task) {
        return task != null && task.getRetryCount() != null ? task.getRetryCount() : 0;
    }

    private int maxRetryCount(ProductPublishTaskRecord task) {
        return task != null && task.getMaxRetryCount() != null ? Math.max(0, task.getMaxRetryCount()) : 3;
    }

    private int effectiveTransientMaxRetryCount(ProductPublishTaskRecord task) {
        return Math.max(maxRetryCount(task), Math.max(0, transientAutomaticMaxRetryCount));
    }

    private LocalDateTime nextAutomaticWriteRetryRunAt(ProductPublishTaskRecord task) {
        int attemptsAfterCurrentFailure = retryCount(task) + 1;
        int seconds = attemptsAfterCurrentFailure <= 1 ? 120 : attemptsAfterCurrentFailure == 2 ? 600 : 1800;
        return LocalDateTime.now().plusSeconds(seconds);
    }

    private String throwableDetails(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                builder.append(' ').append(current.getMessage());
            }
            builder.append(' ').append(current.getClass().getSimpleName());
            current = current.getCause();
        }
        return builder.toString();
    }

    private int versionNo(ProductPublishTaskRecord task) {
        return task != null && task.getVersionNo() != null ? task.getVersionNo() : 0;
    }

    private String activeLockKey(Long productMasterId) {
        return productMasterId == null ? null : "product:" + productMasterId;
    }

    private List<String> resolveChangedDomains(
            ProductPublishTaskRecord task,
            Function<ProductPublishTaskRecord, List<String>> changedDomainsResolver
    ) {
        if (task == null || changedDomainsResolver == null) {
            return List.of();
        }
        List<String> domains = changedDomainsResolver.apply(task);
        return domains == null ? List.of() : domains;
    }

    private String changedDomainText(List<String> changedDomains) {
        Set<String> labels = new LinkedHashSet<>();
        for (String domain : changedDomains == null ? new ArrayList<String>() : changedDomains) {
            String label = changedDomainLabel(domain);
            if (StringUtils.hasText(label)) {
                labels.add(label);
            }
        }
        return String.join("、", labels);
    }

    private String changedDomainLabel(String domain) {
        String normalized = normalize(domain);
        if ("main".equalsIgnoreCase(normalized)) {
            return "商品主档";
        }
        if ("content".equalsIgnoreCase(normalized)) {
            return "图文内容";
        }
        if ("attributes".equalsIgnoreCase(normalized)) {
            return "关键属性";
        }
        if ("site".equalsIgnoreCase(normalized) || "site_offer".equalsIgnoreCase(normalized)) {
            return "当前站点经营";
        }
        if ("grouping".equalsIgnoreCase(normalized)) {
            return "Group 与变体";
        }
        if ("sizes".equalsIgnoreCase(normalized)) {
            return "尺码";
        }
        return null;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    public static class ProductPublishTaskCreateCommand {
        private Long ownerUserId;
        private Long productMasterId;
        private String storeCode;
        private String projectCode;
        private String skuParent;
        private String partnerSku;
        private String pskuCode;
        private String currentSiteCode;
        private String draftJson;
        private String baselineJson;
        private String draftHash;
        private String changedDomainsJson;
        private String requestJson;
        private String idempotencyKey;

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

        public String getDraftJson() {
            return draftJson;
        }

        public void setDraftJson(String draftJson) {
            this.draftJson = draftJson;
        }

        public String getBaselineJson() {
            return baselineJson;
        }

        public void setBaselineJson(String baselineJson) {
            this.baselineJson = baselineJson;
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

        public String getIdempotencyKey() {
            return idempotencyKey;
        }

        public void setIdempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
        }
    }

    public static class ProductPublishTaskCreateResult {
        private final ProductPublishTaskRecord task;
        private final boolean duplicate;

        private ProductPublishTaskCreateResult(ProductPublishTaskRecord task, boolean duplicate) {
            this.task = task;
            this.duplicate = duplicate;
        }

        public static ProductPublishTaskCreateResult created(ProductPublishTaskRecord task) {
            return new ProductPublishTaskCreateResult(task, false);
        }

        public static ProductPublishTaskCreateResult duplicate(ProductPublishTaskRecord task) {
            return new ProductPublishTaskCreateResult(task, true);
        }

        public ProductPublishTaskRecord getTask() {
            return task;
        }

        public boolean isDuplicate() {
            return duplicate;
        }
    }
}
