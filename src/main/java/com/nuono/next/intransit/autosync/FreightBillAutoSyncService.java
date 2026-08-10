package com.nuono.next.intransit.autosync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.intransit.InTransitFreightCostCommands.ActualFreightSyncCommand;
import com.nuono.next.intransit.InTransitFreightCostService;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FreightBillAutoSyncService {
    static final String TASK_TYPE = "LOGISTICS_FREIGHT_BILL_SYNC";
    private static final int RECENT_LIMIT = 50;

    private final Map<String, FreightBillProviderAdapter> adaptersBySource = new HashMap<>();
    private final LogisticsCredentialCipher credentialCipher;
    private final LogisticsAutoSyncAccessContextFactory accessContextFactory;
    private final FreightBillSyncPreviewService previewService;
    private final InTransitFreightCostService freightCostService;
    private final OperationalTaskService taskService;
    private final LogisticsAutoSyncMapper mapper;
    private final ObjectMapper objectMapper;

    public FreightBillAutoSyncService(
            List<FreightBillProviderAdapter> adapters,
            LogisticsCredentialCipher credentialCipher,
            LogisticsAutoSyncAccessContextFactory accessContextFactory,
            FreightBillSyncPreviewService previewService,
            InTransitFreightCostService freightCostService,
            OperationalTaskService taskService,
            LogisticsAutoSyncMapper mapper,
            ObjectMapper objectMapper
    ) {
        if (adapters != null) {
            for (FreightBillProviderAdapter adapter : adapters) {
                if (adapter != null && StringUtils.hasText(adapter.sourceSystem())) {
                    adaptersBySource.put(normalizeSource(adapter.sourceSystem()), adapter);
                }
            }
        }
        this.credentialCipher = credentialCipher;
        this.accessContextFactory = accessContextFactory;
        this.previewService = previewService;
        this.freightCostService = freightCostService;
        this.taskService = taskService;
        this.mapper = mapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public FreightBillAutoSyncTaskSummary runAccount(LogisticsAutoSyncAccount account) {
        requireAccount(account);
        String sourceSystem = normalizeSource(account.getSourceSystem());
        LocalDateTime startedAt = LocalDateTime.now();
        OperationalTask task = taskService.start(
                TASK_TYPE,
                naturalKey(account, sourceSystem, startedAt),
                OperationalTaskPayload.builder()
                        .ownerUserId(account.getOwnerUserId())
                        .payloadJson(toJson(baseSummary(account, sourceSystem, null, "running")))
                        .message("三方物流实际费用拉取中。")
                        .build()
        );
        FreightBillAutoSyncTaskSummary summary = baseSummary(account, sourceSystem, task.getId(), "running");
        String password = null;
        try {
            BusinessAccessContext accessContext = accessContextFactory.requireAccessContext(account);
            password = credentialCipher.decrypt(account.getPasswordCipher());
            FreightBillFetchResult fetchResult = adapter(sourceSystem).fetchFreightBills(fetchRequest(account, sourceSystem, password));
            if (fetchResult == null || !fetchResult.isSuccess()) {
                return providerFailure(account, task, summary, fetchResult, password);
            }
            summary.setSourceRowCount(fetchResult.getSourceRowCount());
            summary.setRevisionDigest(fetchResult.getRevisionDigest());
            summary.setSourceUpdatedAt(fetchResult.getSourceUpdatedAt());
            ActualFreightSyncCommand command = fetchResult.getCommand() == null
                    ? new ActualFreightSyncCommand()
                    : fetchResult.getCommand();
            command.setOwnerUserId(account.getOwnerUserId());
            command.setOperatorUserId(account.getOperatorUserId());
            command.setAccessContext(accessContext);
            command.setSourceSystem(sourceSystem);

            FreightBillSyncPreview preview = previewService.preview(command, fetchResult);
            applyPreview(summary, preview);
            if (!preview.isCommittable()) {
                summary.setStatus("preview_blocked");
                summary.setFailureCode(LogisticsProviderFailureCode.PREVIEW_BLOCKED);
                summary.setFailureMessage("三方物流费用快照不完整或校验未通过，未写入。");
                updateRunState(account, task, "FAILED", "BLOCKED", false, null,
                        LogisticsProviderFailureCode.PREVIEW_BLOCKED, summary.getFailureMessage());
                taskService.fail(task.getId(), LogisticsProviderFailureCode.PREVIEW_BLOCKED, summary.getFailureMessage(), toJson(summary));
                return summary;
            }
            if (preview.getBillCount() == 0) {
                summary.setStatus("no_data");
                updateRunState(account, task, "SUCCESS", "NO_DATA", true, null, null, null);
                taskService.complete(task.getId(), toJson(summary), "三方物流费用拉取完成：没有新账单。");
                return summary;
            }
            if (!Boolean.TRUE.equals(account.getFreightBillCommitEnabled())) {
                summary.setStatus("preview_only");
                updateRunState(account, task, "SUCCESS", "PREVIEW_ONLY", true, null, null, null);
                taskService.complete(task.getId(), toJson(summary), "三方物流费用预览通过，费用提交开关未开启。");
                return summary;
            }
            if (preview.getChangedCommand().getBills().isEmpty()) {
                summary.setStatus("unchanged");
                updateRunState(account, task, "SUCCESS", "UNCHANGED", true, null, null, null);
                taskService.complete(task.getId(), toJson(summary), "三方物流费用拉取完成：账单内容未变化。");
                return summary;
            }
            freightCostService.syncActualCosts(preview.getChangedCommand());
            summary.setStatus("committed");
            updateRunState(account, task, "SUCCESS", "COMMITTED", true, null, null, null);
            taskService.complete(task.getId(), toJson(summary), "三方物流实际费用已提交。");
            return summary;
        } catch (RuntimeException exception) {
            String message = sanitize(exception.getMessage(), account, password);
            if (!StringUtils.hasText(message)) {
                message = "三方物流费用同步执行异常。";
            }
            summary.setStatus("internal_failed");
            summary.setFailureCode(LogisticsProviderFailureCode.PROVIDER_ERROR);
            summary.setFailureMessage(message);
            updateRunState(account, task, "FAILED", "FAILED", false, null,
                    LogisticsProviderFailureCode.PROVIDER_ERROR, message);
            taskService.fail(task.getId(), LogisticsProviderFailureCode.PROVIDER_ERROR, message, toJson(summary));
            return summary;
        }
    }

    private FreightBillAutoSyncTaskSummary providerFailure(
            LogisticsAutoSyncAccount account,
            OperationalTask task,
            FreightBillAutoSyncTaskSummary summary,
            FreightBillFetchResult fetchResult,
            String password
    ) {
        String code = fetchResult == null || !StringUtils.hasText(fetchResult.getFailureCode())
                ? LogisticsProviderFailureCode.PROVIDER_ERROR
                : fetchResult.getFailureCode();
        String message = sanitize(fetchResult == null ? null : fetchResult.getFailureMessage(), account, password);
        if (!StringUtils.hasText(message)) {
            message = "三方物流费用渠道拉取失败。";
        }
        LocalDateTime cooldown = cooldownFailure(code) ? LocalDateTime.now().plusHours(6) : null;
        summary.setStatus("provider_failed");
        summary.setFailureCode(code);
        summary.setFailureMessage(message);
        updateRunState(account, task, null, "FAILED", false, cooldown, code, message);
        taskService.fail(task.getId(), code, message, toJson(summary));
        return summary;
    }

    private void applyPreview(FreightBillAutoSyncTaskSummary summary, FreightBillSyncPreview preview) {
        summary.setRevisionDigest(preview.getRevisionDigest());
        summary.setBillCount(preview.getBillCount());
        summary.setComponentCount(preview.getComponentCount());
        summary.setCreateCount(preview.getCreateCount());
        summary.setUpdateCount(preview.getUpdateCount());
        summary.setUnchangedCount(preview.getUnchangedCount());
        summary.setIssues(preview.getIssues());
    }

    private void updateRunState(
            LogisticsAutoSyncAccount account,
            OperationalTask task,
            String previewStatus,
            String syncStatus,
            boolean updateLastSyncedAt,
            LocalDateTime cooldownUntil,
            String failureCode,
            String failureMessage
    ) {
        LocalDateTime now = LocalDateTime.now();
        mapper.updateFreightBillRunState(
                account.getId(),
                account.getOwnerUserId(),
                task.getId(),
                previewStatus,
                syncStatus,
                updateLastSyncedAt ? now : null,
                now.plusHours(Math.max(1, account.getMinIntervalHours() == null ? 24 : account.getMinIntervalHours())),
                cooldownUntil,
                failureCode,
                failureMessage,
                account.getOperatorUserId()
        );
    }

    private LogisticsProviderFetchRequest fetchRequest(LogisticsAutoSyncAccount account, String sourceSystem, String password) {
        LogisticsProviderFetchRequest request = new LogisticsProviderFetchRequest();
        request.setAccountId(account.getId());
        request.setOwnerUserId(account.getOwnerUserId());
        request.setSourceSystem(sourceSystem);
        request.setForwarderName(account.getForwarderName());
        request.setLoginAccount(account.getLoginAccount());
        request.setPassword(password);
        request.setRecentLimit(RECENT_LIMIT);
        return request;
    }

    private FreightBillProviderAdapter adapter(String sourceSystem) {
        FreightBillProviderAdapter adapter = adaptersBySource.get(sourceSystem);
        if (adapter == null) {
            throw new IllegalStateException("未配置费用账单渠道 adapter：" + sourceSystem);
        }
        return adapter;
    }

    private FreightBillAutoSyncTaskSummary baseSummary(
            LogisticsAutoSyncAccount account,
            String sourceSystem,
            Long taskId,
            String status
    ) {
        FreightBillAutoSyncTaskSummary summary = new FreightBillAutoSyncTaskSummary();
        summary.setAccountId(account.getId());
        summary.setOwnerUserId(account.getOwnerUserId());
        summary.setTaskId(taskId);
        summary.setSourceSystem(sourceSystem);
        summary.setStatus(status);
        return summary;
    }

    private String naturalKey(LogisticsAutoSyncAccount account, String sourceSystem, LocalDateTime now) {
        return "logistics-freight-bill-sync:" + account.getOwnerUserId() + ":" + sourceSystem + ":"
                + account.getId() + ":" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("费用账单任务结果序列化失败。", exception);
        }
    }

    private static boolean cooldownFailure(String code) {
        return LogisticsProviderFailureCode.CAPTCHA_REQUIRED.equals(code)
                || LogisticsProviderFailureCode.RISK_BLOCKED.equals(code)
                || LogisticsProviderFailureCode.INVALID_CREDENTIAL.equals(code);
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

    private static String normalizeSource(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static void requireAccount(LogisticsAutoSyncAccount account) {
        if (account == null || account.getId() == null || account.getOwnerUserId() == null
                || account.getOperatorUserId() == null || !StringUtils.hasText(account.getSourceSystem())) {
            throw new IllegalArgumentException("费用账单自动同步账号不完整。");
        }
    }
}
