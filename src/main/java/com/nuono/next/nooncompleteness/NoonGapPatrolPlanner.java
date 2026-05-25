package com.nuono.next.nooncompleteness;

import com.nuono.next.noonpull.NoonProviderAvailability;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullPlanDraft;
import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullTaskDraft;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTriggerMode;
import com.nuono.next.noonpull.NoonPullType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonGapPatrolPlanner {
    private final NoonDataCompletenessRepository completenessRepository;
    private final NoonPullFoundationService foundationService;
    private final Clock clock;
    private final NoonProviderAvailability providerAvailability;

    @Autowired
    public NoonGapPatrolPlanner(
            NoonDataCompletenessRepository completenessRepository,
            NoonPullFoundationService foundationService
    ) {
        this(completenessRepository, foundationService, Clock.systemUTC(), (plan) -> true);
    }

    public NoonGapPatrolPlanner(
            NoonDataCompletenessRepository completenessRepository,
            NoonPullFoundationService foundationService,
            Clock clock,
            NoonProviderAvailability providerAvailability
    ) {
        this.completenessRepository = completenessRepository;
        this.foundationService = foundationService;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.providerAvailability = providerAvailability == null ? (plan) -> true : providerAvailability;
    }

    public Result planDueGaps(NoonDataGapQuery query, int limit) {
        int maxCount = limit <= 0 ? 50 : limit;
        NoonDataGapQuery safeQuery = query == null ? new NoonDataGapQuery() : query;
        Result result = new Result();
        List<NoonDataGapWindowRecord> candidates = completenessRepository.listGapWindows(safeQuery).stream()
                .filter((gap) -> {
                    boolean eligible = isEligible(gap);
                    if (!eligible) {
                        result.skipped();
                    }
                    return eligible;
                })
                .sorted(priorityOrder())
                .limit(maxCount)
                .collect(Collectors.toList());

        for (NoonDataGapWindowRecord gap : candidates) {
            WorkSpec spec = WorkSpec.from(gap);
            if (spec == null) {
                result.skipped();
                continue;
            }
            NoonPullPlanRecord plan = findOrCreatePlan(gap, spec);
            if (!isRunnable(plan)) {
                result.skipped();
                continue;
            }
            Set<Long> beforeTaskIds = foundationService.listTasks().stream()
                    .map(NoonPullTaskRecord::getId)
                    .collect(Collectors.toCollection(HashSet::new));
            Optional<NoonPullTaskRecord> task = foundationService.createTaskForPlan(plan.getId(), spec.taskDraft(gap));
            if (task.isPresent() && !beforeTaskIds.contains(task.get().getId())) {
                linkGapToTask(gap, plan, task.get());
                result.planned(task.get());
            } else {
                result.skipped();
            }
        }
        return result;
    }

    private boolean isEligible(NoonDataGapWindowRecord gap) {
        if (gap == null || Boolean.TRUE.equals(gap.getRequiresManualAction())) {
            return false;
        }
        if (gap.getNextRetryAt() != null && gap.getNextRetryAt().isAfter(now())) {
            return false;
        }
        if (gap.getStatus() == NoonDataGapStatus.PENDING || gap.getStatus() == NoonDataGapStatus.PENDING_CONFIRMATION) {
            return true;
        }
        if (gap.getStatus() == NoonDataGapStatus.WAITING_RETRY || gap.getStatus() == NoonDataGapStatus.FAILED) {
            return Boolean.TRUE.equals(gap.getRetryable());
        }
        return false;
    }

    private Comparator<NoonDataGapWindowRecord> priorityOrder() {
        return Comparator
                .comparingInt(this::priority)
                .thenComparing(NoonDataGapWindowRecord::getDateFrom, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(NoonDataGapWindowRecord::getId, Comparator.nullsLast(Long::compareTo));
    }

    private int priority(NoonDataGapWindowRecord gap) {
        if (gap.getWindowType() == NoonDataGapWindowType.PRODUCT_BASELINE
                || gap.getWindowType() == NoonDataGapWindowType.PRODUCT_DETAIL_BASELINE) {
            return 0;
        }
        if (gap.getWindowType() == NoonDataGapWindowType.LATEST_DAILY
                || gap.getStatus() == NoonDataGapStatus.PENDING_CONFIRMATION) {
            return 1;
        }
        if (gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL) {
            return 2;
        }
        return 3;
    }

    private NoonPullPlanRecord findOrCreatePlan(NoonDataGapWindowRecord gap, WorkSpec spec) {
        for (NoonPullPlanRecord plan : foundationService.listPlans()) {
            if (samePlanScope(plan, gap, spec)) {
                return plan;
            }
        }
        return foundationService.createPlan(spec.planDraft(gap));
    }

    private boolean samePlanScope(NoonPullPlanRecord plan, NoonDataGapWindowRecord gap, WorkSpec spec) {
        return Objects.equals(plan.getOwnerUserId(), gap.getOwnerUserId())
                && Objects.equals(normalize(plan.getStoreCode()), normalize(gap.getStoreCode()))
                && Objects.equals(normalize(plan.getSiteCode()), normalize(gap.getSiteCode()))
                && plan.getPullType() == spec.pullType
                && plan.getDataDomain() == spec.dataDomain
                && plan.getTriggerMode() == spec.triggerMode
                && Objects.equals(plan.getScheduleExpression(), spec.scheduleExpression(gap));
    }

    private boolean isRunnable(NoonPullPlanRecord plan) {
        if (plan == null || !plan.isEnabled() || plan.isPaused()) {
            return false;
        }
        LocalDateTime persistedNow = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (plan.getNextRetryAt() != null && plan.getNextRetryAt().isAfter(persistedNow)) {
            return false;
        }
        if (plan.getLatestSuccessAt() != null && plan.getCooldownSeconds() != null && plan.getCooldownSeconds() > 0
                && plan.getLatestSuccessAt().plusSeconds(plan.getCooldownSeconds()).isAfter(persistedNow)) {
            return false;
        }
        if (isUnsafeFailure(plan.getLatestFailureType())) {
            return false;
        }
        return providerAvailability.isAvailable(plan);
    }

    private boolean isUnsafeFailure(String failureType) {
        String normalized = normalize(failureType);
        return "provider_unavailable".equals(normalized)
                || "provider_not_configured".equals(normalized)
                || "auth_required".equals(normalized)
                || "rate_limited".equals(normalized)
                || "blocked_by_risk_control".equals(normalized)
                || "captcha_required".equals(normalized);
    }

    private void linkGapToTask(NoonDataGapWindowRecord gap, NoonPullPlanRecord plan, NoonPullTaskRecord task) {
        NoonDataGapWindowRecord linked = gap.copy();
        linked.setLinkedPullPlanId(plan.getId());
        linked.setLinkedPullTaskId(task.getId());
        linked.setUpdatedAt(now());
        completenessRepository.insertGapWindow(linked);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    public static class Result {
        private final List<NoonPullTaskRecord> plannedTasks = new ArrayList<>();
        private int skippedCount;

        private void planned(NoonPullTaskRecord task) {
            plannedTasks.add(task.copy());
        }

        private void skipped() {
            skippedCount++;
        }

        public List<NoonPullTaskRecord> getPlannedTasks() {
            return plannedTasks.stream().map(NoonPullTaskRecord::copy).collect(Collectors.toList());
        }

        public int getSkippedCount() {
            return skippedCount;
        }
    }

    private static class WorkSpec {
        private final NoonPullType pullType;
        private final NoonPullDataDomain dataDomain;
        private final NoonPullTriggerMode triggerMode;
        private final String targetPrefix;

        private WorkSpec(
                NoonPullType pullType,
                NoonPullDataDomain dataDomain,
                NoonPullTriggerMode triggerMode,
                String targetPrefix
        ) {
            this.pullType = pullType;
            this.dataDomain = dataDomain;
            this.triggerMode = triggerMode;
            this.targetPrefix = targetPrefix;
        }

        private static WorkSpec from(NoonDataGapWindowRecord gap) {
            if (gap.getCategory() == NoonDataCategory.PRODUCT_LIST) {
                return new WorkSpec(
                        NoonPullType.INTERFACE,
                        NoonPullDataDomain.PRODUCT,
                        NoonPullTriggerMode.ONBOARDING,
                        "product-list"
                );
            }
            if (gap.getCategory() == NoonDataCategory.PRODUCT_DETAIL) {
                return new WorkSpec(
                        NoonPullType.INTERFACE,
                        NoonPullDataDomain.PRODUCT,
                        NoonPullTriggerMode.ONBOARDING,
                        "product-detail"
                );
            }
            if (gap.getCategory() == NoonDataCategory.SALES_PRODUCT_VIEWS) {
                return new WorkSpec(
                        NoonPullType.REPORT,
                        NoonPullDataDomain.SALES,
                        historyOrLatestTrigger(gap),
                        "sales-product-views"
                );
            }
            if (gap.getCategory() == NoonDataCategory.SALES_ORDER) {
                return new WorkSpec(
                        NoonPullType.REPORT,
                        NoonPullDataDomain.ORDER,
                        historyOrLatestTrigger(gap),
                        "orders"
                );
            }
            return null;
        }

        private static NoonPullTriggerMode historyOrLatestTrigger(NoonDataGapWindowRecord gap) {
            if (gap.getWindowType() == NoonDataGapWindowType.HISTORY_BACKFILL) {
                return NoonPullTriggerMode.GAP_BACKFILL;
            }
            if (gap.getStatus() == NoonDataGapStatus.PENDING_CONFIRMATION) {
                return NoonPullTriggerMode.READBACK_CHECK;
            }
            return NoonPullTriggerMode.SCHEDULED_DAILY;
        }

        private NoonPullPlanDraft planDraft(NoonDataGapWindowRecord gap) {
            return NoonPullPlanDraft.builder()
                    .ownerUserId(gap.getOwnerUserId())
                    .storeCode(gap.getStoreCode())
                    .siteCode(gap.getSiteCode())
                    .pullType(pullType)
                    .dataDomain(dataDomain)
                    .triggerMode(triggerMode)
                    .scheduleExpression(scheduleExpression(gap))
                    .maxRequestsPerRun(50)
                    .cooldownSeconds(0)
                    .concurrencyLimit(1)
                    .build();
        }

        private NoonPullTaskDraft taskDraft(NoonDataGapWindowRecord gap) {
            return NoonPullTaskDraft.builder()
                    .ownerUserId(gap.getOwnerUserId())
                    .storeCode(gap.getStoreCode())
                    .siteCode(gap.getSiteCode())
                    .pullType(pullType)
                    .dataDomain(dataDomain)
                    .triggerMode(triggerMode)
                    .targetIdentity(targetIdentity(gap))
                    .targetDateFrom(gap.getDateFrom())
                    .targetDateTo(gap.getDateTo())
                    .build();
        }

        private String scheduleExpression(NoonDataGapWindowRecord gap) {
            if (triggerMode == NoonPullTriggerMode.GAP_BACKFILL) {
                return "backfill:" + gap.getDateFrom() + ".." + gap.getDateTo() + ";maxDays=31;maxWindows=1";
            }
            return "gap-patrol:" + targetIdentity(gap);
        }

        private String targetIdentity(NoonDataGapWindowRecord gap) {
            return targetPrefix + ":" + gap.getDateFrom() + ".." + gap.getDateTo();
        }
    }
}
