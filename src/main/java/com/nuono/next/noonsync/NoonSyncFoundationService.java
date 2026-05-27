package com.nuono.next.noonsync;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class NoonSyncFoundationService {

    private static final List<NoonSyncPlanDefinition> PLAN_CATALOG = List.of(
            new NoonSyncPlanDefinition(
                    "product_initialization",
                    NoonSyncDataDomain.PRODUCT,
                    NoonSyncTriggerMode.ONBOARDING,
                    "Initialize local product workspace from Noon read data for a newly onboarded scope."
            ),
            new NoonSyncPlanDefinition(
                    "product_explicit_refresh",
                    NoonSyncDataDomain.PRODUCT,
                    NoonSyncTriggerMode.MANUAL_REFRESH,
                    "Refresh product data only after an explicit user action or guarded readback."
            ),
            new NoonSyncPlanDefinition(
                    "sales_daily_sync",
                    NoonSyncDataDomain.SALES,
                    NoonSyncTriggerMode.SCHEDULED_DAILY,
                    "Pull the latest available productviewsandsalesdata sales day."
            ),
            new NoonSyncPlanDefinition(
                    "sales_backfill",
                    NoonSyncDataDomain.SALES,
                    NoonSyncTriggerMode.GAP_BACKFILL,
                    "Backfill missing sales history for onboarding or detected gaps."
            ),
            new NoonSyncPlanDefinition(
                    "sales_low_frequency_correction",
                    NoonSyncDataDomain.SALES,
                    NoonSyncTriggerMode.LOW_FREQUENCY_CORRECTION,
                    "Correct a configured recent historical sales window at low frequency."
            )
    );

    private final List<NoonSyncTask> tasks = new ArrayList<>();
    private final List<NoonSyncPlan> plans = new ArrayList<>();
    private long nextTaskId = 1L;
    private long nextPlanId = 1L;

    public List<NoonSyncPlanDefinition> planCatalog() {
        return PLAN_CATALOG;
    }

    public List<NoonSyncPlan> listPlans() {
        return List.copyOf(plans);
    }

    public List<NoonSyncTask> listTasks() {
        return List.copyOf(tasks);
    }

    public NoonSyncTask createTask(NoonSyncTaskRequest request) {
        rejectDuplicateActiveTask(request);
        NoonSyncTask task = new NoonSyncTask(
                nextTaskId++,
                request.getDataDomain(),
                request.getTriggerMode(),
                request.getScope(),
                request.getTarget(),
                NoonSyncTaskStatus.QUEUED,
                request.getRetryPolicy()
        );
        tasks.add(task);
        return task;
    }

    public NoonSyncPlan createPlan(String planKey, NoonSyncScope scope) {
        NoonSyncPlan plan = new NoonSyncPlan(nextPlanId++, requireDefinition(planKey), scope, false, null);
        plans.add(plan);
        return plan;
    }

    public NoonSyncPlan pausePlan(long planId, String reason) {
        return replacePlan(planId, requirePlan(planId).pause(reason));
    }

    public NoonSyncPlan resumePlan(long planId) {
        return replacePlan(planId, requirePlan(planId).resume());
    }

    public Optional<NoonSyncTask> createScheduledTaskFromPlan(long planId, NoonSyncTarget target) {
        NoonSyncPlan plan = requirePlan(planId);
        if (plan.isPaused()) {
            return Optional.empty();
        }
        NoonSyncPlanDefinition definition = plan.getDefinition();
        return Optional.of(createTask(new NoonSyncTaskRequest(
                definition.getDataDomain(),
                definition.getTriggerMode(),
                plan.getScope(),
                target,
                NoonSyncRetryPolicy.RETRYABLE
        )));
    }

    private void rejectDuplicateActiveTask(NoonSyncTaskRequest request) {
        tasks.stream()
                .filter(NoonSyncTask::isActive)
                .filter(task -> task.hasSameLockTarget(request))
                .findFirst()
                .ifPresent(task -> {
                    throw new NoonSyncDuplicateActiveTaskException(
                            "Duplicate active Noon sync task for " + request.getDataDomain()
                                    + " " + request.getScope().getStoreCode()
                    );
                });
    }

    public NoonSyncTask markRunning(NoonSyncTask task) {
        return replace(task.getId(), task.withStatus(NoonSyncTaskStatus.RUNNING, null));
    }

    public NoonSyncTask markSucceeded(long taskId) {
        NoonSyncTask task = requireTask(taskId);
        return replace(taskId, task.withStatus(NoonSyncTaskStatus.SUCCEEDED, null));
    }

    public NoonSyncTask markPartial(long taskId, String diagnosticSummary) {
        NoonSyncTask task = requireTask(taskId);
        return replace(taskId, task.withStatus(NoonSyncTaskStatus.PARTIAL, diagnosticSummary));
    }

    public NoonSyncTask markPartial(
            long taskId,
            NoonSyncFailureReason failureReason,
            NoonSyncRetryPolicy retryPolicy,
            NoonSyncDiagnostic diagnostic
    ) {
        NoonSyncTask task = requireTask(taskId);
        return replace(taskId, task.partial(failureReason, retryPolicy, diagnostic));
    }

    public NoonSyncTask markSkipped(long taskId, String diagnosticSummary) {
        NoonSyncTask task = requireTask(taskId);
        return replace(taskId, task.withStatus(NoonSyncTaskStatus.SKIPPED, diagnosticSummary));
    }

    public NoonSyncTask markCancelled(long taskId, String diagnosticSummary) {
        NoonSyncTask task = requireTask(taskId);
        return replace(taskId, task.withStatus(NoonSyncTaskStatus.CANCELLED, diagnosticSummary));
    }

    public NoonSyncTask markFailed(
            long taskId,
            NoonSyncFailureReason failureReason,
            NoonSyncRetryPolicy retryPolicy,
            NoonSyncDiagnostic diagnostic
    ) {
        NoonSyncTask task = requireTask(taskId);
        return replace(taskId, task.failed(failureReason, retryPolicy, diagnostic));
    }

    private NoonSyncTask requireTask(long taskId) {
        return tasks.stream()
                .filter(task -> task.getId() == taskId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Noon sync task not found: " + taskId));
    }

    private NoonSyncTask replace(long taskId, NoonSyncTask replacement) {
        for (int index = 0; index < tasks.size(); index++) {
            if (tasks.get(index).getId() == taskId) {
                tasks.set(index, replacement);
                return replacement;
            }
        }
        throw new IllegalArgumentException("Noon sync task not found: " + taskId);
    }

    private NoonSyncPlanDefinition requireDefinition(String planKey) {
        return PLAN_CATALOG.stream()
                .filter(definition -> definition.getKey().equals(planKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Noon sync plan definition not found: " + planKey));
    }

    private NoonSyncPlan requirePlan(long planId) {
        return plans.stream()
                .filter(plan -> plan.getId() == planId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Noon sync plan not found: " + planId));
    }

    private NoonSyncPlan replacePlan(long planId, NoonSyncPlan replacement) {
        for (int index = 0; index < plans.size(); index++) {
            if (plans.get(index).getId() == planId) {
                plans.set(index, replacement);
                return replacement;
            }
        }
        throw new IllegalArgumentException("Noon sync plan not found: " + planId);
    }
}
