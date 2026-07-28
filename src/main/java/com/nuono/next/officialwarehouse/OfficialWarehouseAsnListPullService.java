package com.nuono.next.officialwarehouse;

import com.nuono.next.noon.NoonAuthenticationFailureClassifier;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullPlanDraft;
import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullTaskDraft;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.noonpull.NoonPullTriggerMode;
import com.nuono.next.noonpull.NoonPullType;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.web.ApiProblemException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OfficialWarehouseAsnListPullService {
    static final String TARGET_IDENTITY = "official-warehouse-asn-list";

    private final NoonPullFoundationService foundationService;
    private final ObjectProvider<OfficialWarehouseAsnListTaskExecutor> executorProvider;

    public OfficialWarehouseAsnListPullService(
            NoonPullFoundationService foundationService,
            ObjectProvider<OfficialWarehouseAsnListTaskExecutor> executorProvider
    ) {
        this.foundationService = foundationService;
        this.executorProvider = executorProvider;
    }

    public AsnListSyncView sync(
            BusinessAccessContext access,
            String storeCode,
            String siteCode
    ) {
        String normalizedStoreCode = requireText(storeCode, "请选择店铺。").toUpperCase(Locale.ROOT);
        String normalizedSiteCode = requireText(siteCode, "请选择站点。").toUpperCase(Locale.ROOT);
        Long ownerUserId = ownerUserId(access, normalizedStoreCode);
        NoonPullPlanRecord plan = findPlan(ownerUserId, normalizedStoreCode, normalizedSiteCode);
        if (plan == null) {
            plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(normalizedStoreCode)
                .siteCode(normalizedSiteCode)
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .scheduleExpression("manual")
                .maxPagesPerRun(50)
                .maxRequestsPerRun(50)
                .build());
        }
        NoonPullTaskRecord task = foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(ownerUserId)
                .storeCode(normalizedStoreCode)
                .siteCode(normalizedSiteCode)
                .pullType(NoonPullType.INTERFACE)
                .dataDomain(NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN)
                .triggerMode(NoonPullTriggerMode.MANUAL_REFRESH)
                .targetIdentity(TARGET_IDENTITY)
                .build()).orElseThrow(() -> new IllegalStateException("无法创建 Noon ASN 列表同步任务。"));
        if (task.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            throw authRecoveryPending(task, null);
        }
        if (task.getStatus() == NoonPullTaskStatus.RUNNING) {
            throw taskAlreadyRunning(task);
        }
        return execute(task, access, true);
    }

    public NoonPullTaskStatus executeScheduled(NoonPullTaskRecord task) {
        try {
            execute(task, accessForTask(task), false);
        } catch (RuntimeException ignored) {
            // The durable task record is the scheduled worker's result contract.
        }
        return foundationService.getTask(task.getId()).getStatus();
    }

    private AsnListSyncView execute(
            NoonPullTaskRecord task,
            BusinessAccessContext access,
            boolean propagateFailure
    ) {
        NoonPullTaskRecord running = foundationService.markRunning(
                task.getId(),
                "official-warehouse-asn-list-sync"
        );
        if (running.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
            throw authRecoveryPending(running, null);
        }
        try {
            AsnListSyncView result = executor().syncNoonAsnListForTask(
                    access,
                    task.getStoreCode(),
                    task.getSiteCode()
            );
            foundationService.markSucceeded(
                    task.getId(),
                    "official-warehouse-asn-" + task.getId(),
                    "official warehouse ASN list synced; fetched=" + result.fetched
                            + "; created=" + result.created
                            + "; updated=" + result.updated
                            + "; pages=" + result.pages
            );
            return result;
        } catch (RuntimeException failure) {
            NoonPullTaskRecord failed = foundationService.markFailedWithPolicy(
                    task.getId(),
                    failureEvidence(failure),
                    foundationService.attemptNumber(task)
            );
            if (failed.getStatus() == NoonPullTaskStatus.BLOCKED_AUTH) {
                throw authRecoveryPending(failed, failure);
            }
            if (propagateFailure) {
                throw failure;
            }
            throw new ScheduledExecutionFailure(failure);
        }
    }

    private OfficialWarehouseAsnListTaskExecutor executor() {
        OfficialWarehouseAsnListTaskExecutor executor = executorProvider.getIfAvailable();
        if (executor == null) {
            throw new IllegalStateException("Noon ASN 列表同步执行器未配置。");
        }
        return executor;
    }

    private Long ownerUserId(BusinessAccessContext access, String storeCode) {
        if (access == null || !access.canAccessStore(storeCode)) {
            throw new IllegalArgumentException("当前账号不能操作该店铺。");
        }
        Long ownerUserId = access.resolveOwnerUserIdForStore(storeCode);
        if (ownerUserId == null) {
            ownerUserId = access.getBusinessOwnerUserId();
        }
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("无法识别店铺所属老板。");
        }
        return ownerUserId;
    }

    private NoonPullPlanRecord findPlan(Long ownerUserId, String storeCode, String siteCode) {
        return foundationService.listPlans().stream()
                .filter(plan -> plan.isEnabled()
                        && !plan.isPaused()
                        && ownerUserId.equals(plan.getOwnerUserId())
                        && storeCode.equalsIgnoreCase(plan.getStoreCode())
                        && siteCode.equalsIgnoreCase(plan.getSiteCode())
                        && plan.getPullType() == NoonPullType.INTERFACE
                        && plan.getDataDomain() == NoonPullDataDomain.OFFICIAL_WAREHOUSE_ASN
                        && plan.getTriggerMode() == NoonPullTriggerMode.MANUAL_REFRESH)
                .findFirst()
                .orElse(null);
    }

    private BusinessAccessContext accessForTask(NoonPullTaskRecord task) {
        return BusinessAccessContext.builder()
                .sessionUserId(task.getOwnerUserId())
                .businessOwnerUserId(task.getOwnerUserId())
                .storeCodes(java.util.Set.of(task.getStoreCode()))
                .storeOwnerUserIds(Map.of(task.getStoreCode(), task.getOwnerUserId()))
                .build();
    }

    private String failureEvidence(Throwable failure) {
        StringBuilder details = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                details.append(' ').append(current.getMessage());
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        String message = details.toString().trim();
        if (NoonAuthenticationFailureClassifier.isAuthenticationFailure(failure)) {
            return "auth_required: " + message;
        }
        return StringUtils.hasText(message) ? message : failure.getClass().getSimpleName();
    }

    private ApiProblemException authRecoveryPending(NoonPullTaskRecord task, Throwable cause) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("taskId", task.getId());
        details.put("taskStatus", NoonPullTaskStatus.BLOCKED_AUTH.name());
        details.put("automaticRetry", true);
        if (task.getAuthRecoveryId() != null) {
            details.put("recoveryId", task.getAuthRecoveryId());
        }
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFICIAL_WAREHOUSE_AUTH_RECOVERY_PENDING",
                "AUTH_REQUIRED",
                "SYNC_ASN_LIST",
                "Noon 授权恢复中，本次 ASN 同步任务将在恢复后自动重试。",
                true,
                false,
                null,
                details,
                cause
        );
    }

    private ApiProblemException taskAlreadyRunning(NoonPullTaskRecord task) {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "OFFICIAL_WAREHOUSE_ASN_SYNC_IN_PROGRESS",
                "CONFLICT",
                "SYNC_ASN_LIST",
                "ASN 同步任务已在后台执行，无需重复提交。",
                true,
                false,
                null,
                Map.of(
                        "taskId", task.getId(),
                        "taskStatus", NoonPullTaskStatus.RUNNING.name(),
                        "automaticRetry", true
                ),
                null
        );
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static final class ScheduledExecutionFailure extends RuntimeException {
        private ScheduledExecutionFailure(Throwable cause) {
            super(cause);
        }
    }
}
