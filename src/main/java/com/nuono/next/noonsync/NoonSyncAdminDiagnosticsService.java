package com.nuono.next.noonsync;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
@Deprecated // 未接入：仅由前端 0 引用的 /api/noon-sync-admin 驱动。保留待删除（#9）
public class NoonSyncAdminDiagnosticsService {

    private final NoonSyncFoundationService foundationService;

    public NoonSyncAdminDiagnosticsService(NoonSyncFoundationService foundationService) {
        this.foundationService = foundationService;
    }

    public NoonSyncAdminDiagnosticsView overview(
            BusinessAccessContext context,
            NoonSyncAdminDiagnosticFilter filter
    ) {
        requireSystemAdmin(context);
        NoonSyncAdminDiagnosticFilter safeFilter = filter == null ? NoonSyncAdminDiagnosticFilter.empty() : filter;
        List<NoonSyncAdminPlanSummary> plans = foundationService.listPlans()
                .stream()
                .map(NoonSyncAdminPlanSummary::new)
                .collect(Collectors.toList());
        List<NoonSyncTask> allTasks = foundationService.listTasks();
        List<NoonSyncAdminTaskSummary> tasks = allTasks
                .stream()
                .filter(task -> matches(task, safeFilter))
                .map(NoonSyncAdminTaskSummary::new)
                .collect(Collectors.toList());
        return new NoonSyncAdminDiagnosticsView(plans, tasks, health(foundationService.listPlans(), allTasks));
    }

    public NoonSyncAdminTaskSummary taskDetail(
            BusinessAccessContext context,
            long taskId,
            Long expectedOwnerUserId
    ) {
        requireSystemAdmin(context);
        NoonSyncTask task = requireTask(taskId);
        if (expectedOwnerUserId == null || !expectedOwnerUserId.equals(task.getScope().getOwnerUserId())) {
            throw new IllegalStateException("Sync task detail requires explicit matching owner scope.");
        }
        return new NoonSyncAdminTaskSummary(task);
    }

    public NoonSyncPlan pausePlan(BusinessAccessContext context, long planId, String reason) {
        requireSystemAdmin(context);
        return foundationService.pausePlan(planId, reason);
    }

    public NoonSyncPlan resumePlan(BusinessAccessContext context, long planId) {
        requireSystemAdmin(context);
        return foundationService.resumePlan(planId);
    }

    public NoonSyncTask retryTask(BusinessAccessContext context, long taskId) {
        requireSystemAdmin(context);
        NoonSyncTask task = requireTask(taskId);
        if (!task.isRetryableFailure()) {
            throw new IllegalStateException("Only retryable failed Noon sync tasks can be retried.");
        }
        return foundationService.createTask(new NoonSyncTaskRequest(
                task.getDataDomain(),
                task.getTriggerMode(),
                task.getScope(),
                task.getTarget(),
                task.getRetryPolicy()
        ));
    }

    private NoonSyncAdminHealthSummary health(List<NoonSyncPlan> plans, List<NoonSyncTask> tasks) {
        int active = 0;
        int failed = 0;
        int retryable = 0;
        for (NoonSyncTask task : tasks) {
            if (task.isActive()) {
                active++;
            }
            if (task.getStatus() == NoonSyncTaskStatus.FAILED) {
                failed++;
            }
            if (task.isRetryableFailure()) {
                retryable++;
            }
        }
        int paused = (int) plans.stream().filter(NoonSyncPlan::isPaused).count();
        return new NoonSyncAdminHealthSummary(active, failed, retryable, paused);
    }

    private boolean matches(NoonSyncTask task, NoonSyncAdminDiagnosticFilter filter) {
        if (filter.getDataDomain() != null && task.getDataDomain() != filter.getDataDomain()) {
            return false;
        }
        if (filter.getStatus() != null && task.getStatus() != filter.getStatus()) {
            return false;
        }
        if (filter.getTriggerMode() != null && task.getTriggerMode() != filter.getTriggerMode()) {
            return false;
        }
        return overlaps(task.getTarget(), filter.getDateFrom(), filter.getDateTo());
    }

    private boolean overlaps(NoonSyncTarget target, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null && dateTo == null) {
            return true;
        }
        if (target == null || target.getDateFrom() == null || target.getDateTo() == null) {
            return true;
        }
        LocalDate filterFrom = dateFrom == null ? LocalDate.MIN : dateFrom;
        LocalDate filterTo = dateTo == null ? LocalDate.MAX : dateTo;
        return !target.getDateTo().isBefore(filterFrom) && !filterTo.isBefore(target.getDateFrom());
    }

    private NoonSyncTask requireTask(long taskId) {
        return foundationService.listTasks()
                .stream()
                .filter(task -> task.getId() == taskId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Noon sync task not found: " + taskId));
    }

    private void requireSystemAdmin(BusinessAccessContext context) {
        if (context == null || !context.isSystemAdmin()) {
            throw new IllegalStateException("Noon sync diagnostics require system administrator access.");
        }
    }
}
