package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.intransit.InTransitPluginSyncCommands.PluginSyncCommand;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncIssueView;
import com.nuono.next.intransit.InTransitPluginSyncRecords.PluginSyncPreviewView;
import com.nuono.next.intransit.InTransitPluginSyncService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class LogisticsAutoSyncService {
    private static final String TASK_TYPE = "LOGISTICS_AUTO_SYNC";
    private static final int DEFAULT_RECENT_LIMIT = 10;
    private static final int MAX_PREVIEW_ISSUES_IN_TASK_RESULT = 20;

    private final Map<String, LogisticsProviderAdapter> adaptersBySource = new HashMap<>();
    private final LogisticsCredentialCipher credentialCipher;
    private final LogisticsAutoSyncAccessContextFactory accessContextFactory;
    private final InTransitPluginSyncService pluginSyncService;
    private final OperationalTaskService taskService;
    private final LogisticsAutoSyncMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogisticsAutoSyncService(
            List<LogisticsProviderAdapter> adapters,
            LogisticsCredentialCipher credentialCipher,
            LogisticsAutoSyncAccessContextFactory accessContextFactory,
            InTransitPluginSyncService pluginSyncService,
            OperationalTaskService taskService,
            LogisticsAutoSyncMapper mapper
    ) {
        if (adapters != null) {
            for (LogisticsProviderAdapter adapter : adapters) {
                if (adapter != null && StringUtils.hasText(adapter.sourceSystem())) {
                    adaptersBySource.put(normalizeSource(adapter.sourceSystem()), adapter);
                }
            }
        }
        this.credentialCipher = credentialCipher;
        this.accessContextFactory = accessContextFactory;
        this.pluginSyncService = pluginSyncService;
        this.taskService = taskService;
        this.mapper = mapper;
    }

    public LogisticsAutoSyncTaskSummary runAccount(LogisticsAutoSyncAccount account) {
        requireAccount(account);
        String sourceSystem = normalizeSource(account.getSourceSystem());
        LocalDateTime startedAt = LocalDateTime.now();
        OperationalTask task = taskService.start(
                TASK_TYPE,
                naturalKey(account, sourceSystem, startedAt),
                safePayload(account, sourceSystem)
        );
        LogisticsAutoSyncTaskSummary summary = baseSummary(account, sourceSystem, task.getId());
        String password = null;
        try {
            BusinessAccessContext accessContext = accessContextFactory.requireAccessContext(account);
            password = credentialCipher.decrypt(account.getPasswordCipher());
            LogisticsProviderFetchResult fetchResult = adapter(sourceSystem).fetch(fetchRequest(account, sourceSystem, password));
            if (fetchResult == null || !fetchResult.isSuccess()) {
                return providerFailure(account, task, summary, fetchResult, password);
            }

            applyProviderCounts(summary, fetchResult);
            PluginSyncCommand command = fetchResult.getCommand();
            if (command == null || command.getBatches() == null || command.getBatches().isEmpty()) {
                summary.setStatus("no_data");
                updateRunState(account, task, "SUCCESS", null, "NO_DATA", "READY", true, null, null, null);
                taskService.complete(task.getId(), toJson(summary), "物流自动同步完成：没有新数据。");
                return summary;
            }

            enrichCommand(command, account, sourceSystem, accessContext);
            PluginSyncPreviewView preview = pluginSyncService.preview(command);
            summary.setBatchCount(preview == null ? summary.getBatchCount() : preview.getBatchCount());
            summary.setPackageCount(preview == null ? summary.getPackageCount() : preview.getPackageCount());
            summary.setLineCount(preview == null ? summary.getLineCount() : preview.getLineCount());
            summary.setNodeCount(preview == null ? summary.getNodeCount() : preview.getNodeCount());
            if (preview == null || !preview.isCommittable()) {
                summary.setStatus("preview_blocked");
                summary.setFailureCode(LogisticsProviderFailureCode.PREVIEW_BLOCKED);
                summary.setFailureMessage("物流自动同步预览未通过，未提交。");
                applyPreviewIssues(summary, preview, account, password);
                updateRunState(
                        account,
                        task,
                        "SUCCESS",
                        "FAILED",
                        "FAILED",
                        "FAILED",
                        false,
                        null,
                        LogisticsProviderFailureCode.PREVIEW_BLOCKED,
                        summary.getFailureMessage()
                );
                taskService.fail(
                        task.getId(),
                        LogisticsProviderFailureCode.PREVIEW_BLOCKED,
                        summary.getFailureMessage(),
                        toJson(summary)
                );
                return summary;
            }

            if (!Boolean.TRUE.equals(account.getCommitEnabled())) {
                summary.setStatus("preview_only");
                updateRunState(account, task, "SUCCESS", "SUCCESS", "PREVIEW_ONLY", "READY", true, null, null, null);
                taskService.complete(task.getId(), toJson(summary), "物流自动同步完成：预览通过，账号未开启自动提交。");
                return summary;
            }

            pluginSyncService.commit(command);
            summary.setStatus("committed");
            updateRunState(account, task, "SUCCESS", "SUCCESS", "COMMITTED", "READY", true, null, null, null);
            taskService.complete(task.getId(), toJson(summary), "物流自动同步完成：已自动提交。");
            return summary;
        } catch (RuntimeException exception) {
            return internalFailure(account, task, summary, exception, password);
        }
    }

    private LogisticsAutoSyncTaskSummary providerFailure(
            LogisticsAutoSyncAccount account,
            OperationalTask task,
            LogisticsAutoSyncTaskSummary summary,
            LogisticsProviderFetchResult fetchResult,
            String password
    ) {
        String failureCode = fetchResult == null || !StringUtils.hasText(fetchResult.getFailureCode())
                ? LogisticsProviderFailureCode.PROVIDER_ERROR
                : fetchResult.getFailureCode().trim();
        String failureMessage = sanitize(fetchResult == null ? null : fetchResult.getFailureMessage(), account, password);
        if (!StringUtils.hasText(failureMessage)) {
            failureMessage = "物流渠道拉取失败。";
        }
        LocalDateTime cooldownUntil = cooldownFailure(failureCode) ? LocalDateTime.now().plusHours(6) : null;
        String verificationStatus = cooldownFailure(failureCode) ? "BLOCKED" : "FAILED";
        summary.setStatus("provider_failed");
        summary.setFailureCode(failureCode);
        summary.setFailureMessage(failureMessage);
        updateRunState(
                account,
                task,
                "FAILED",
                null,
                "FAILED",
                verificationStatus,
                false,
                cooldownUntil,
                failureCode,
                failureMessage
        );
        taskService.fail(task.getId(), failureCode, failureMessage);
        return summary;
    }

    private LogisticsAutoSyncTaskSummary internalFailure(
            LogisticsAutoSyncAccount account,
            OperationalTask task,
            LogisticsAutoSyncTaskSummary summary,
            RuntimeException exception,
            String password
    ) {
        String failureMessage = sanitize(exception == null ? null : exception.getMessage(), account, password);
        if (!StringUtils.hasText(failureMessage)) {
            failureMessage = "物流自动同步执行异常。";
        }
        summary.setStatus("internal_failed");
        summary.setFailureCode(LogisticsProviderFailureCode.PROVIDER_ERROR);
        summary.setFailureMessage(failureMessage);
        updateRunState(
                account,
                task,
                "FAILED",
                null,
                "FAILED",
                "FAILED",
                false,
                null,
                LogisticsProviderFailureCode.PROVIDER_ERROR,
                failureMessage
        );
        taskService.fail(task.getId(), LogisticsProviderFailureCode.PROVIDER_ERROR, failureMessage);
        return summary;
    }

    private void updateRunState(
            LogisticsAutoSyncAccount account,
            OperationalTask task,
            String loginStatus,
            String previewStatus,
            String syncStatus,
            String verificationStatus,
            boolean updateLastSyncedAt,
            LocalDateTime cooldownUntil,
            String failureCode,
            String failureMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastSyncedAt = updateLastSyncedAt ? now : null;
        mapper.updateAccountRunState(
                account.getId(),
                task.getId(),
                loginStatus,
                previewStatus,
                syncStatus,
                verificationStatus,
                lastSyncedAt,
                now.plusHours(Math.max(1, account.getMinIntervalHours() == null ? 24 : account.getMinIntervalHours())),
                cooldownUntil,
                failureCode,
                failureMessage,
                account.getOperatorUserId()
        );
    }

    private OperationalTaskPayload safePayload(LogisticsAutoSyncAccount account, String sourceSystem) {
        LogisticsAutoSyncTaskSummary payload = baseSummary(account, sourceSystem, null);
        payload.setStatus("running");
        return OperationalTaskPayload.builder()
                .ownerUserId(account.getOwnerUserId())
                .payloadJson(toJson(payload))
                .message("物流自动同步执行中。")
                .build();
    }

    private LogisticsProviderFetchRequest fetchRequest(
            LogisticsAutoSyncAccount account,
            String sourceSystem,
            String password
    ) {
        LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
        request.setAccountId(account.getId());
        request.setOwnerUserId(account.getOwnerUserId());
        request.setSourceSystem(sourceSystem);
        request.setForwarderName(account.getForwarderName());
        request.setLoginAccount(account.getLoginAccount());
        request.setPassword(password);
        request.setRecentLimit(DEFAULT_RECENT_LIMIT);
        request.setForceFullSync(false);
        return request;
    }

    private void enrichCommand(
            PluginSyncCommand command,
            LogisticsAutoSyncAccount account,
            String sourceSystem,
            BusinessAccessContext accessContext
    ) {
        command.setOwnerUserId(account.getOwnerUserId());
        command.setOperatorUserId(account.getOperatorUserId());
        command.setAccessContext(accessContext);
        command.setSourceSystem(sourceSystem);
        command.setForwarderName(account.getForwarderName());
    }

    private LogisticsProviderAdapter adapter(String sourceSystem) {
        LogisticsProviderAdapter adapter = adaptersBySource.get(sourceSystem);
        if (adapter == null) {
            throw new IllegalStateException("未配置物流自动同步渠道 adapter：" + sourceSystem);
        }
        return adapter;
    }

    private LogisticsAutoSyncTaskSummary baseSummary(
            LogisticsAutoSyncAccount account,
            String sourceSystem,
            Long taskId
    ) {
        LogisticsAutoSyncTaskSummary summary = new LogisticsAutoSyncTaskSummary();
        summary.setAccountId(account.getId());
        summary.setOwnerUserId(account.getOwnerUserId());
        summary.setTaskId(taskId);
        summary.setSourceSystem(sourceSystem);
        return summary;
    }

    private void applyProviderCounts(
            LogisticsAutoSyncTaskSummary summary,
            LogisticsProviderFetchResult fetchResult
    ) {
        summary.setBatchCount(fetchResult.getBatchCount());
        summary.setPackageCount(fetchResult.getPackageCount());
        summary.setLineCount(fetchResult.getLineCount());
        summary.setNodeCount(fetchResult.getNodeCount());
    }

    private void applyPreviewIssues(
            LogisticsAutoSyncTaskSummary summary,
            PluginSyncPreviewView preview,
            LogisticsAutoSyncAccount account,
            String password
    ) {
        if (summary == null || preview == null) {
            return;
        }
        List<PluginSyncIssueView> issues = preview.getIssues();
        if (issues == null || issues.isEmpty()) {
            summary.setPreviewIssueCount(0);
            summary.setPreviewIssues(List.of());
            return;
        }
        summary.setPreviewIssueCount(issues.size());
        List<LogisticsAutoSyncTaskSummary.PreviewIssueSummary> sanitized = new ArrayList<>();
        for (PluginSyncIssueView issue : issues) {
            if (issue == null) {
                continue;
            }
            sanitized.add(toPreviewIssueSummary(issue, account, password));
            if (sanitized.size() >= MAX_PREVIEW_ISSUES_IN_TASK_RESULT) {
                break;
            }
        }
        summary.setPreviewIssues(sanitized);
    }

    private LogisticsAutoSyncTaskSummary.PreviewIssueSummary toPreviewIssueSummary(
            PluginSyncIssueView issue,
            LogisticsAutoSyncAccount account,
            String password
    ) {
        LogisticsAutoSyncTaskSummary.PreviewIssueSummary summary =
                new LogisticsAutoSyncTaskSummary.PreviewIssueSummary();
        summary.setLevel(sanitize(issue.getLevel(), account, password));
        summary.setBatchNo(sanitize(issue.getBatchNo(), account, password));
        summary.setBoxNo(sanitize(issue.getBoxNo(), account, password));
        summary.setPsku(sanitize(issue.getPsku(), account, password));
        summary.setField(sanitize(issue.getField(), account, password));
        summary.setMessage(truncate(sanitize(issue.getMessage(), account, password), 300));
        return summary;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("物流自动同步任务结果序列化失败。", exception);
        }
    }

    private String naturalKey(LogisticsAutoSyncAccount account, String sourceSystem, LocalDateTime now) {
        return "logistics-auto-sync:"
                + account.getOwnerUserId()
                + ":"
                + sourceSystem
                + ":"
                + account.getId()
                + ":"
                + now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }

    private static boolean cooldownFailure(String failureCode) {
        return LogisticsProviderFailureCode.CAPTCHA_REQUIRED.equals(failureCode)
                || LogisticsProviderFailureCode.RISK_BLOCKED.equals(failureCode)
                || LogisticsProviderFailureCode.INVALID_CREDENTIAL.equals(failureCode);
    }

    private static String sanitize(String value, LogisticsAutoSyncAccount account, String password) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value;
        if (account != null && StringUtils.hasText(account.getLoginAccount())) {
            result = result.replace(account.getLoginAccount(), "[redacted]");
        }
        if (StringUtils.hasText(password)) {
            result = result.replace(password, "[redacted]");
        }
        return result;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private static String normalizeSource(String sourceSystem) {
        return sourceSystem == null ? "" : sourceSystem.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireAccount(LogisticsAutoSyncAccount account) {
        if (account == null || account.getId() == null || account.getOwnerUserId() == null
                || account.getOperatorUserId() == null || !StringUtils.hasText(account.getSourceSystem())) {
            throw new IllegalArgumentException("物流自动同步账号不完整。");
        }
    }
}
