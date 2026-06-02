package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noonpull.NoonProviderAvailability;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullRepository;
import com.nuono.next.noonpull.NoonPullScheduledExecutionResult;
import com.nuono.next.noonpull.NoonPullScheduledExecutionService;
import com.nuono.next.noonpull.NoonPullTaskRecord;
import com.nuono.next.noonpull.NoonPullTaskStatus;
import com.nuono.next.noonpull.NoonPullTriggerMode;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoonGapPatrolActionServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryCompletenessRepository completenessRepository;
    private InMemoryPullRepository pullRepository;
    private NoonDataAuditService auditService;
    private NoonGapPatrolActionService service;

    @BeforeEach
    void setUp() {
        completenessRepository = new InMemoryCompletenessRepository();
        pullRepository = new InMemoryPullRepository();
        auditService = mock(NoonDataAuditService.class);
        NoonGapPatrolPlanner planner = new NoonGapPatrolPlanner(
                completenessRepository,
                new NoonPullFoundationService(pullRepository, FIXED_CLOCK),
                FIXED_CLOCK,
                (NoonProviderAvailability) (plan) -> true
        );
        service = new NoonGapPatrolActionService(completenessRepository, planner, auditService, FIXED_CLOCK);
    }

    @Test
    void retryableGapCreatesSafePlannerWorkAndLinksTask() {
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_PRODUCT_VIEWS));
        NoonDataGapWindowRecord gap = gap(910001L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapStatus.WAITING_RETRY);
        gap.setRetryable(Boolean.TRUE);
        gap.setNextRetryAt(LocalDateTime.parse("2026-05-25T02:00:00"));
        completenessRepository.insertGapWindow(gap);

        NoonGapPatrolActionResult result = service.retry(910001L);

        assertEquals(1, result.getPlannedTaskCount());
        NoonDataGapWindowRecord updated = gap();
        assertEquals(NoonDataGapStatus.PENDING, updated.getStatus());
        assertEquals(Boolean.TRUE, updated.getRetryable());
        assertEquals(1001L, updated.getLinkedPullTaskId());
        assertEquals(1, pullRepository.tasks.size());
    }

    @Test
    void manualActionGapCannotRetry() {
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_PRODUCT_VIEWS));
        NoonDataGapWindowRecord gap = gap(910001L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapStatus.FAILED);
        gap.setRetryable(Boolean.FALSE);
        gap.setRequiresManualAction(Boolean.TRUE);
        completenessRepository.insertGapWindow(gap);

        assertThrows(IllegalStateException.class, () -> service.retry(910001L));
        assertEquals(0, pullRepository.tasks.size());
    }

    @Test
    void pauseAndResumeUpdateGapAndCompletenessPatrolState() {
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_ORDER));
        completenessRepository.insertGapWindow(gap(910001L, NoonDataCategory.SALES_ORDER, NoonDataGapStatus.PENDING));

        service.pause(910001L, "noisy retry");

        NoonDataGapWindowRecord paused = gap();
        assertEquals(NoonDataGapStatus.PAUSED, paused.getStatus());
        assertEquals(Boolean.FALSE, paused.getRetryable());
        NoonDataCompletenessRecord pausedRow = row();
        assertEquals(false, pausedRow.isPatrolEnabled());
        assertEquals(0, pausedRow.getActiveGapCount());

        service.resume(910001L);

        NoonDataGapWindowRecord resumed = gap();
        assertEquals(NoonDataGapStatus.PENDING, resumed.getStatus());
        assertEquals(Boolean.TRUE, resumed.getRetryable());
        NoonDataCompletenessRecord resumedRow = row();
        assertEquals(true, resumedRow.isPatrolEnabled());
        assertEquals(1, resumedRow.getActiveGapCount());
    }

    @Test
    void reAuditDelegatesToSalesOrProductAuditWithoutCreatingDirectProviderWork() {
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_ORDER));
        completenessRepository.insertGapWindow(gap(910001L, NoonDataCategory.SALES_ORDER, NoonDataGapStatus.PENDING));

        service.reAudit(910001L);

        verify(auditService).auditSalesCompleteness(any(NoonDataAuditCommand.class));
        assertEquals(0, pullRepository.tasks.size());
    }

    @Test
    void syncCategoryCreatesOneSafeTaskForEachDataType() {
        for (NoonDataCategory category : List.of(
                NoonDataCategory.PRODUCT_LIST,
                NoonDataCategory.PRODUCT_DETAIL,
                NoonDataCategory.SALES_ORDER,
                NoonDataCategory.SALES_PRODUCT_VIEWS
        )) {
            setUp();
            completenessRepository.insertCompleteness(row(category));
            completenessRepository.insertGapWindow(gap(910001L, category, NoonDataGapStatus.PENDING));

            NoonGapPatrolActionResult result = service.syncCategory(
                    307L,
                    "STR108065-NAE",
                    "AE",
                    category
            );

            assertEquals(1, result.getPlannedTaskCount());
            assertEquals(1, result.getPlannedTaskIds().size());
            assertEquals(result.getPlannedTaskIds().get(0), gap().getLinkedPullTaskId());
            assertEquals(1, pullRepository.tasks.size());
        }
    }

    @Test
    void syncCategoryUsesManualReportTriggersForClickedOrderData() {
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_ORDER));
        completenessRepository.insertGapWindow(gap(910001L, NoonDataCategory.SALES_ORDER, NoonDataGapStatus.PENDING));
        NoonDataGapWindowRecord historyGap = gap(910002L, NoonDataCategory.SALES_ORDER, NoonDataGapStatus.PENDING);
        historyGap.setWindowType(NoonDataGapWindowType.HISTORY_BACKFILL);
        historyGap.setDateFrom(LocalDate.parse("2026-04-25"));
        historyGap.setDateTo(LocalDate.parse("2026-05-24"));
        completenessRepository.insertGapWindow(historyGap);

        NoonGapPatrolActionResult result = service.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                NoonDataCategory.SALES_ORDER
        );

        assertEquals(2, result.getPlannedTaskCount());
        List<NoonPullTaskRecord> tasks = pullRepository.listTasks();
        assertEquals(NoonPullTriggerMode.MANUAL_REFRESH, tasks.get(0).getTriggerMode());
        assertEquals("orders:2026-05-24..2026-05-24", tasks.get(0).getTargetIdentity());
        assertEquals(NoonPullTriggerMode.MANUAL_BACKFILL, tasks.get(1).getTriggerMode());
        assertEquals("orders:2026-04-25..2026-05-24", tasks.get(1).getTargetIdentity());
        for (NoonDataGapWindowRecord gap : completenessRepository.listGapWindows(new NoonDataGapQuery())) {
            assertNull(gap.getNextRetryAt());
        }
    }

    @Test
    void syncCategoryCreatesManualRefreshGapWhenCompleteRowHasNoActiveGap() {
        NoonDataCompletenessRecord complete = row(NoonDataCategory.SALES_PRODUCT_VIEWS);
        complete.setLatestStatus(NoonDataLatestStatus.READY);
        complete.setHistoryStatus(NoonDataHistoryStatus.COMPLETE);
        complete.setActiveGapCount(0);
        completenessRepository.insertCompleteness(complete);

        NoonGapPatrolActionResult result = service.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                NoonDataCategory.SALES_PRODUCT_VIEWS
        );

        assertEquals(1, result.getPlannedTaskCount());
        assertEquals(1, completenessRepository.listGapWindows(new NoonDataGapQuery()).size());
        NoonDataGapWindowRecord refreshGap = gap();
        assertEquals(NoonDataGapWindowType.LATEST_DAILY, refreshGap.getWindowType());
        assertEquals(NoonDataGapStatus.PENDING, refreshGap.getStatus());
        assertEquals(LocalDate.parse("2026-05-24"), refreshGap.getDateFrom());
        assertEquals(result.getPlannedTaskIds().get(0), refreshGap.getLinkedPullTaskId());
    }

    @Test
    void syncCategoryExecutesPlannedTasksImmediately() {
        NoonPullScheduledExecutionService immediateExecutor = mock(NoonPullScheduledExecutionService.class);
        NoonPullScheduledExecutionResult executionResult = new NoonPullScheduledExecutionResult();
        executionResult.executed();
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_PRODUCT_VIEWS));
        completenessRepository.insertGapWindow(gap(910001L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapStatus.PENDING));
        NoonGapPatrolPlanner planner = new NoonGapPatrolPlanner(
                completenessRepository,
                new NoonPullFoundationService(pullRepository, FIXED_CLOCK),
                FIXED_CLOCK,
                (NoonProviderAvailability) (plan) -> true
        );
        NoonGapPatrolActionService immediateService = new NoonGapPatrolActionService(
                completenessRepository,
                planner,
                auditService,
                immediateExecutor,
                FIXED_CLOCK
        );

        when(immediateExecutor.executeTaskIds(any())).thenReturn(executionResult);

        NoonGapPatrolActionResult result = immediateService.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                NoonDataCategory.SALES_PRODUCT_VIEWS
        );

        assertEquals(1, result.getPlannedTaskCount());
        assertEquals(1, result.getExecutedTaskCount());
        assertEquals(0, result.getFailedTaskCount());
        assertEquals(0, result.getSkippedTaskCount());
        assertTrue(result.getMessage().contains("立即执行"));
        verify(immediateExecutor).executeTaskIds(eq(result.getPlannedTaskIds()));
    }

    @Test
    void syncCategoryReducesImmediateAuthFailureToManualActionGap() {
        NoonPullScheduledExecutionService immediateExecutor = mock(NoonPullScheduledExecutionService.class);
        completenessRepository.insertCompleteness(row(NoonDataCategory.SALES_ORDER));
        completenessRepository.insertGapWindow(gap(910001L, NoonDataCategory.SALES_ORDER, NoonDataGapStatus.PENDING));
        NoonGapPatrolPlanner planner = new NoonGapPatrolPlanner(
                completenessRepository,
                new NoonPullFoundationService(pullRepository, FIXED_CLOCK),
                FIXED_CLOCK,
                (NoonProviderAvailability) (plan) -> true
        );
        NoonGapPatrolActionService immediateService = new NoonGapPatrolActionService(
                completenessRepository,
                planner,
                auditService,
                immediateExecutor,
                new NoonGapTaskOutcomeReducer(completenessRepository, FIXED_CLOCK),
                FIXED_CLOCK
        );

        when(immediateExecutor.executeTaskIds(any())).thenAnswer((invocation) -> {
            List<Long> taskIds = invocation.getArgument(0);
            NoonPullTaskRecord failed = pullRepository.selectTask(taskIds.get(0));
            failed.setStatus(NoonPullTaskStatus.FAILED);
            failed.setFailureType("auth_required");
            failed.setRetryable(Boolean.FALSE);
            failed.setRequiresManualAction(Boolean.TRUE);
            failed.setDiagnosticSummary("auth required: invalid username or password");
            pullRepository.updateTask(failed);

            NoonPullScheduledExecutionResult executionResult = new NoonPullScheduledExecutionResult();
            executionResult.failed();
            executionResult.addTaskOutcome(pullRepository.selectTask(failed.getId()));
            return executionResult;
        });

        NoonGapPatrolActionResult result = immediateService.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                NoonDataCategory.SALES_ORDER
        );

        assertEquals(1, result.getFailedTaskCount());
        NoonDataGapWindowRecord updatedGap = gap();
        assertEquals(NoonDataGapStatus.FAILED, updatedGap.getStatus());
        assertEquals("auth_required", updatedGap.getFailureType());
        assertFalse(updatedGap.getRetryable());
        assertTrue(updatedGap.getRequiresManualAction());
        assertEquals("auth_required", row().getLastFailureType());
        assertThrows(IllegalStateException.class, () -> immediateService.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                NoonDataCategory.SALES_ORDER
        ));
    }

    private NoonDataGapWindowRecord gap() {
        return completenessRepository.listGapWindows(new NoonDataGapQuery()).get(0);
    }

    private NoonDataCompletenessRecord row() {
        return completenessRepository.listCompleteness(new NoonDataCompletenessQuery()).get(0);
    }

    private static NoonDataCompletenessRecord row(NoonDataCategory category) {
        NoonDataCompletenessRecord row = new NoonDataCompletenessRecord();
        row.setId(900001L);
        row.setOwnerUserId(307L);
        row.setStoreCode("STR108065-NAE");
        row.setSiteCode("AE");
        row.setCategory(category);
        row.setLatestStatus(NoonDataLatestStatus.INCOMPLETE);
        row.setHistoryStatus(NoonDataHistoryStatus.INCOMPLETE);
        row.setPatrolEnabled(true);
        row.setActiveGapCount(1);
        row.setCreatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        row.setUpdatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        return row;
    }

    private static NoonDataGapWindowRecord gap(Long id, NoonDataCategory category, NoonDataGapStatus status) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(id);
        gap.setCompletenessId(900001L);
        gap.setOwnerUserId(307L);
        gap.setStoreCode("STR108065-NAE");
        gap.setSiteCode("AE");
        gap.setCategory(category);
        gap.setWindowType(NoonDataGapWindowType.LATEST_DAILY);
        gap.setDateFrom(LocalDate.parse("2026-05-24"));
        gap.setDateTo(LocalDate.parse("2026-05-24"));
        gap.setStatus(status);
        gap.setAttempts(1);
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setCreatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        gap.setUpdatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
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
            for (int index = 0; index < completeness.size(); index++) {
                if (sameCompleteness(completeness.get(index), record)) {
                    completeness.set(index, record.copy());
                    return;
                }
            }
            completeness.add(record.copy());
        }

        @Override
        public List<NoonDataCompletenessRecord> listCompleteness(NoonDataCompletenessQuery query) {
            return completeness.stream()
                    .filter((row) -> query.getCategory() == null || row.getCategory() == query.getCategory())
                    .map(NoonDataCompletenessRecord::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public void insertGapWindow(NoonDataGapWindowRecord record) {
            for (int index = 0; index < gaps.size(); index++) {
                if (sameGap(gaps.get(index), record) || Objects.equals(gaps.get(index).getId(), record.getId())) {
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
                    .sorted(Comparator.comparing(NoonDataGapWindowRecord::getId))
                    .map(NoonDataGapWindowRecord::copy)
                    .collect(Collectors.toList());
        }

        private boolean sameCompleteness(NoonDataCompletenessRecord left, NoonDataCompletenessRecord right) {
            return Objects.equals(left.getOwnerUserId(), right.getOwnerUserId())
                    && Objects.equals(left.getStoreCode(), right.getStoreCode())
                    && Objects.equals(left.getSiteCode(), right.getSiteCode())
                    && left.getCategory() == right.getCategory();
        }

        private boolean sameGap(NoonDataGapWindowRecord left, NoonDataGapWindowRecord right) {
            return Objects.equals(left.getCompletenessId(), right.getCompletenessId())
                    && left.getCategory() == right.getCategory()
                    && left.getWindowType() == right.getWindowType()
                    && Objects.equals(left.getDateFrom(), right.getDateFrom())
                    && Objects.equals(left.getDateTo(), right.getDateTo());
        }
    }

    private static final class InMemoryPullRepository implements NoonPullRepository {
        private long nextId = 1000L;
        private final Map<Long, com.nuono.next.noonpull.NoonPullPlanRecord> plans = new LinkedHashMap<>();
        private final Map<Long, NoonPullTaskRecord> tasks = new LinkedHashMap<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return nextId++;
        }

        @Override
        public void insertPlan(com.nuono.next.noonpull.NoonPullPlanRecord plan) {
            plans.put(plan.getId(), plan.copy());
        }

        @Override
        public com.nuono.next.noonpull.NoonPullPlanRecord selectPlan(Long planId) {
            com.nuono.next.noonpull.NoonPullPlanRecord plan = plans.get(planId);
            return plan == null ? null : plan.copy();
        }

        @Override
        public void updatePlan(com.nuono.next.noonpull.NoonPullPlanRecord plan) {
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
            return null;
        }

        @Override
        public void updateTask(NoonPullTaskRecord task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public List<com.nuono.next.noonpull.NoonPullPlanRecord> listPlans() {
            return plans.values().stream().map(com.nuono.next.noonpull.NoonPullPlanRecord::copy).collect(Collectors.toList());
        }

        @Override
        public List<NoonPullTaskRecord> listTasks() {
            return tasks.values().stream().map(NoonPullTaskRecord::copy).collect(Collectors.toList());
        }
    }
}
