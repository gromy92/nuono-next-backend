package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.noonpull.NoonProviderAvailability;
import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullRepository;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.noonpull.NoonPullTriggerMode;
import com.nuono.next.noonpull.NoonPullType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class NoonGapPatrolPlannerTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void selectsDueEligibleGapsAndPrioritizesOnboardingThenLatestThenHistory() {
        InMemoryCompletenessRepository completenessRepository = new InMemoryCompletenessRepository();
        InMemoryPullRepository pullRepository = new InMemoryPullRepository();
        NoonGapPatrolPlanner planner = planner(completenessRepository, pullRepository, (plan) -> true);
        completenessRepository.insertGapWindow(gap(
                1L,
                NoonDataCategory.SALES_PRODUCT_VIEWS,
                NoonDataGapWindowType.HISTORY_BACKFILL,
                NoonDataGapStatus.PENDING,
                LocalDate.parse("2026-02-24"),
                LocalDate.parse("2026-03-26")
        ));
        completenessRepository.insertGapWindow(gap(
                2L,
                NoonDataCategory.SALES_ORDER,
                NoonDataGapWindowType.LATEST_DAILY,
                NoonDataGapStatus.PENDING,
                LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-24")
        ));
        completenessRepository.insertGapWindow(gap(
                3L,
                NoonDataCategory.PRODUCT_LIST,
                NoonDataGapWindowType.PRODUCT_BASELINE,
                NoonDataGapStatus.PENDING,
                LocalDate.parse("2026-05-25"),
                LocalDate.parse("2026-05-25")
        ));

        NoonGapPatrolPlanner.Result result = planner.planDueGaps(new NoonDataGapQuery(), 10);

        assertEquals(3, result.getPlannedTasks().size());
        NoonPullTaskRecord productTask = result.getPlannedTasks().get(0);
        assertEquals(NoonPullDataDomain.PRODUCT, productTask.getDataDomain());
        assertEquals(NoonPullType.INTERFACE, productTask.getPullType());
        assertEquals(NoonPullTriggerMode.ONBOARDING, productTask.getTriggerMode());
        assertTrue(productTask.getTargetIdentity().contains("product-list"));

        NoonPullTaskRecord orderLatestTask = result.getPlannedTasks().get(1);
        assertEquals(NoonPullDataDomain.ORDER, orderLatestTask.getDataDomain());
        assertEquals(NoonPullType.REPORT, orderLatestTask.getPullType());
        assertEquals(NoonPullTriggerMode.SCHEDULED_DAILY, orderLatestTask.getTriggerMode());
        assertEquals(LocalDate.parse("2026-05-24"), orderLatestTask.getTargetDateFrom());

        NoonPullTaskRecord salesHistoryTask = result.getPlannedTasks().get(2);
        assertEquals(NoonPullDataDomain.SALES, salesHistoryTask.getDataDomain());
        assertEquals(NoonPullType.REPORT, salesHistoryTask.getPullType());
        assertEquals(NoonPullTriggerMode.GAP_BACKFILL, salesHistoryTask.getTriggerMode());
        assertEquals(LocalDate.parse("2026-02-24"), salesHistoryTask.getTargetDateFrom());
        assertEquals(LocalDate.parse("2026-03-26"), salesHistoryTask.getTargetDateTo());
    }

    @Test
    void skipsTerminalPausedRetentionManualAndNotDueGaps() {
        InMemoryCompletenessRepository completenessRepository = new InMemoryCompletenessRepository();
        InMemoryPullRepository pullRepository = new InMemoryPullRepository();
        NoonGapPatrolPlanner planner = planner(completenessRepository, pullRepository, (plan) -> true);
        completenessRepository.insertGapWindow(gap(1L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, NoonDataGapStatus.SUCCEEDED));
        completenessRepository.insertGapWindow(gap(2L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, NoonDataGapStatus.CONFIRMED_EMPTY));
        completenessRepository.insertGapWindow(gap(3L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, NoonDataGapStatus.PAUSED));
        completenessRepository.insertGapWindow(gap(4L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, NoonDataGapStatus.PROVIDER_RETENTION_LIMIT));
        NoonDataGapWindowRecord manual = gap(5L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.HISTORY_BACKFILL, NoonDataGapStatus.FAILED);
        manual.setRetryable(Boolean.FALSE);
        manual.setRequiresManualAction(Boolean.TRUE);
        completenessRepository.insertGapWindow(manual);
        NoonDataGapWindowRecord futureRetry = gap(6L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.LATEST_DAILY, NoonDataGapStatus.WAITING_RETRY);
        futureRetry.setRetryable(Boolean.TRUE);
        futureRetry.setNextRetryAt(LocalDateTime.parse("2026-05-25T04:00:00"));
        completenessRepository.insertGapWindow(futureRetry);
        NoonDataGapWindowRecord dueRetry = gap(7L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.LATEST_DAILY, NoonDataGapStatus.WAITING_RETRY);
        dueRetry.setRetryable(Boolean.TRUE);
        dueRetry.setNextRetryAt(LocalDateTime.parse("2026-05-25T02:00:00"));
        completenessRepository.insertGapWindow(dueRetry);

        NoonGapPatrolPlanner.Result result = planner.planDueGaps(new NoonDataGapQuery(), 10);

        assertEquals(1, result.getPlannedTasks().size());
        assertEquals("sales-product-views:2026-05-24..2026-05-24", result.getPlannedTasks().get(0).getTargetIdentity());
        assertEquals(6, result.getSkippedCount());
    }

    @Test
    void activeLocksAndSafetyGatesSuppressUnsafeTaskCreation() {
        InMemoryCompletenessRepository activeCompleteness = new InMemoryCompletenessRepository();
        InMemoryPullRepository activePull = new InMemoryPullRepository();
        NoonGapPatrolPlanner activePlanner = planner(activeCompleteness, activePull, (plan) -> true);
        NoonDataGapWindowRecord activeGap = gap(
                1L,
                NoonDataCategory.SALES_ORDER,
                NoonDataGapWindowType.LATEST_DAILY,
                NoonDataGapStatus.PENDING,
                LocalDate.parse("2026-05-24"),
                LocalDate.parse("2026-05-24")
        );
        activeCompleteness.insertGapWindow(activeGap);
        activePlanner.planDueGaps(new NoonDataGapQuery(), 10);

        NoonGapPatrolPlanner.Result duplicate = activePlanner.planDueGaps(new NoonDataGapQuery(), 10);

        assertEquals(0, duplicate.getPlannedTasks().size());
        assertEquals(1, activePull.tasks.size());
        assertEquals(1, duplicate.getSkippedCount());

        InMemoryCompletenessRepository unsafeCompleteness = new InMemoryCompletenessRepository();
        InMemoryPullRepository unsafePull = new InMemoryPullRepository();
        NoonGapPatrolPlanner unsafePlanner = planner(unsafeCompleteness, unsafePull, (plan) -> false);
        unsafeCompleteness.insertGapWindow(gap(2L, NoonDataCategory.SALES_ORDER, NoonDataGapWindowType.LATEST_DAILY, NoonDataGapStatus.PENDING));
        unsafeCompleteness.insertGapWindow(gap(3L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.LATEST_DAILY, NoonDataGapStatus.PENDING));
        unsafeCompleteness.insertGapWindow(gap(4L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapWindowType.HISTORY_BACKFILL, NoonDataGapStatus.PENDING));

        NoonGapPatrolPlanner.Result unsafe = unsafePlanner.planDueGaps(new NoonDataGapQuery(), 10);

        assertEquals(0, unsafe.getPlannedTasks().size());
        assertEquals(3, unsafe.getSkippedCount());
        assertEquals(0, unsafePull.tasks.size());
    }

    private static NoonGapPatrolPlanner planner(
            InMemoryCompletenessRepository completenessRepository,
            InMemoryPullRepository pullRepository,
            NoonProviderAvailability providerAvailability
    ) {
        return new NoonGapPatrolPlanner(
                completenessRepository,
                new NoonPullFoundationService(pullRepository, FIXED_CLOCK),
                FIXED_CLOCK,
                providerAvailability
        );
    }

    private static NoonDataGapWindowRecord gap(
            Long id,
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            NoonDataGapStatus status
    ) {
        return gap(id, category, windowType, status, LocalDate.parse("2026-05-24"), LocalDate.parse("2026-05-24"));
    }

    private static NoonDataGapWindowRecord gap(
            Long id,
            NoonDataCategory category,
            NoonDataGapWindowType windowType,
            NoonDataGapStatus status,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(id);
        gap.setCompletenessId(900000L + id);
        gap.setOwnerUserId(307L);
        gap.setStoreCode("STR108065-NAE");
        gap.setSiteCode("AE");
        gap.setCategory(category);
        gap.setWindowType(windowType);
        gap.setDateFrom(dateFrom);
        gap.setDateTo(dateTo);
        gap.setStatus(status);
        gap.setAttempts(0);
        gap.setRetryable(status == NoonDataGapStatus.PENDING
                || status == NoonDataGapStatus.WAITING_RETRY
                || status == NoonDataGapStatus.PENDING_CONFIRMATION);
        gap.setRequiresManualAction(Boolean.FALSE);
        return gap;
    }

    private static final class InMemoryCompletenessRepository implements NoonDataCompletenessRepository {
        private final AtomicLong ids = new AtomicLong(900000);
        private final List<NoonDataCompletenessRecord> completeness = new ArrayList<>();
        private final List<NoonDataGapWindowRecord> gaps = new ArrayList<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return ids.incrementAndGet();
        }

        @Override
        public void insertCompleteness(NoonDataCompletenessRecord record) {
            completeness.add(record.copy());
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            return completeness.stream().map(NoonDataCompletenessRecord::copy).collect(Collectors.toList());
        }

        @Override
        public void insertGapWindow(NoonDataGapWindowRecord record) {
            for (int index = 0; index < gaps.size(); index++) {
                NoonDataGapWindowRecord existing = gaps.get(index);
                if (Objects.equals(existing.getId(), record.getId())) {
                    gaps.set(index, record.copy());
                    return;
                }
            }
            gaps.add(record.copy());
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            return gaps.stream()
                    .filter((gap) -> query.getOwnerUserId() == null || query.getOwnerUserId().equals(gap.getOwnerUserId()))
                    .filter((gap) -> query.getStoreCode() == null || query.getStoreCode().equals(gap.getStoreCode()))
                    .filter((gap) -> query.getSiteCode() == null || query.getSiteCode().equals(gap.getSiteCode()))
                    .filter((gap) -> query.getCategory() == null || query.getCategory() == gap.getCategory())
                    .filter((gap) -> query.getStatus() == null || query.getStatus() == gap.getStatus())
                    .filter((gap) -> query.getFailureType() == null || query.getFailureType().equals(gap.getFailureType()))
                    .filter((gap) -> query.getRetryable() == null || query.getRetryable().equals(gap.getRetryable()))
                    .sorted(Comparator
                            .comparing(NoonDataGapWindowRecord::getOwnerUserId)
                            .thenComparing(NoonDataGapWindowRecord::getStoreCode)
                            .thenComparing(NoonDataGapWindowRecord::getSiteCode)
                            .thenComparing(NoonDataGapWindowRecord::getCategory)
                            .thenComparing(NoonDataGapWindowRecord::getWindowType)
                            .thenComparing(NoonDataGapWindowRecord::getDateFrom))
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }
    }

    private static final class InMemoryPullRepository implements NoonPullRepository {
        private long nextId = 1000L;
        private final Map<Long, NoonPullPlanRecord> plans = new LinkedHashMap<>();
        private final Map<Long, NoonPullTaskRecord> tasks = new LinkedHashMap<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return nextId++;
        }

        @Override
        public void insertPlan(NoonPullPlanRecord plan) {
            plans.put(plan.getId(), plan.copy());
        }

        @Override
        public NoonPullPlanRecord selectPlan(Long planId) {
            NoonPullPlanRecord plan = plans.get(planId);
            return plan == null ? null : plan.copy();
        }

        @Override
        public void updatePlan(NoonPullPlanRecord plan) {
            plans.put(plan.getId(), plan.copy());
        }

        @Override
        public void insertTask(NoonPullTaskRecord task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public NoonPullTaskRecord selectTask(Long taskId) {
            NoonPullTaskRecord task = tasks.get(taskId);
            return task == null ? null : task.copy();
        }

        @Override
        public NoonPullTaskRecord selectActiveTaskByLockKey(String activeLockKey) {
            return tasks.values().stream()
                    .filter((task) -> activeLockKey.equals(task.getActiveLockKey()))
                    .filter((task) -> task.getStatus() == NoonPullTaskStatus.QUEUED
                            || task.getStatus() == NoonPullTaskStatus.RUNNING)
                    .findFirst()
                    .map(NoonPullTaskRecord::copy)
                    .orElse(null);
        }

        @Override
        public NoonPullTaskRecord selectLatestTaskByLockKey(String activeLockKey) {
            NoonPullTaskRecord latest = null;
            for (NoonPullTaskRecord task : tasks.values()) {
                if (!activeLockKey.equals(task.getActiveLockKey())) {
                    continue;
                }
                if (latest == null || task.getId() > latest.getId()) {
                    latest = task;
                }
            }
            return latest == null ? null : latest.copy();
        }

        @Override
        public void updateTask(NoonPullTaskRecord task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public List<NoonPullPlanRecord> listPlans() {
            return plans.values().stream().map(NoonPullPlanRecord::copy).collect(Collectors.toList());
        }

        @Override
        public List<NoonPullTaskRecord> listTasks() {
            return tasks.values().stream().map(NoonPullTaskRecord::copy).collect(Collectors.toList());
        }
    }
}
