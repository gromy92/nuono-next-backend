package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.nuono.next.noonpull.NoonPullDataDomain;
import com.nuono.next.noonpull.NoonPullFoundationService;
import com.nuono.next.noonpull.NoonPullPlanDraft;
import com.nuono.next.noonpull.NoonPullPlanRecord;
import com.nuono.next.noonpull.NoonPullRepository;
import com.nuono.next.noonpull.NoonPullTaskDraft;
import com.nuono.next.noonpull.NoonPullTaskLookupService;
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

class NoonCallStoreDataServiceTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-25T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void storeDataAggregatesFourCategoryCellsWithLatestTaskState() {
        InMemoryCompletenessRepository completenessRepository = new InMemoryCompletenessRepository();
        InMemoryPullRepository pullRepository = new InMemoryPullRepository();
        NoonPullFoundationService foundationService = new NoonPullFoundationService(pullRepository, FIXED_CLOCK);
        completenessRepository.insertCompleteness(row(900001L, NoonDataCategory.PRODUCT_LIST, NoonDataLatestStatus.READY, NoonDataHistoryStatus.NOT_REQUIRED));
        completenessRepository.insertCompleteness(row(900002L, NoonDataCategory.PRODUCT_DETAIL, NoonDataLatestStatus.INCOMPLETE, NoonDataHistoryStatus.INCOMPLETE));
        completenessRepository.insertCompleteness(row(900003L, NoonDataCategory.SALES_ORDER, NoonDataLatestStatus.FAILED, NoonDataHistoryStatus.INCOMPLETE));
        completenessRepository.insertCompleteness(row(900004L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataLatestStatus.PENDING_CONFIRMATION, NoonDataHistoryStatus.INCOMPLETE));
        completenessRepository.insertGapWindow(gap(910004L, NoonDataCategory.SALES_PRODUCT_VIEWS, NoonDataGapStatus.PENDING_CONFIRMATION));

        NoonPullTaskRecord salesTask = createTask(
                foundationService,
                NoonPullType.REPORT,
                NoonPullDataDomain.SALES,
                "sales-product-views:2026-05-24..2026-05-24"
        );
        foundationService.markRunning(salesTask.getId(), "worker-1");

        NoonCallStoreDataView view = new NoonCallStoreDataService(
                completenessRepository,
                new NoonPullTaskLookupService(foundationService)
        ).view(new NoonDataCompletenessQuery());

        assertEquals(1, view.getRows().size());
        NoonCallStoreDataView.Row store = view.getRows().get(0);
        assertEquals("STR108065-NAE", store.getStoreCode());
        assertEquals("AE", store.getSiteCode());
        assertEquals("SYNCING", store.getOverallMarker());
        assertEquals(4, store.getCategories().size());
        assertEquals("COMPLETE", cell(store, NoonDataCategory.PRODUCT_LIST).getMarker());
        assertEquals("PENDING_SYNC", cell(store, NoonDataCategory.PRODUCT_DETAIL).getMarker());
        assertEquals("FAILED", cell(store, NoonDataCategory.SALES_ORDER).getMarker());
        NoonCallStoreDataView.CategoryCell salesCell = cell(store, NoonDataCategory.SALES_PRODUCT_VIEWS);
        assertEquals("SYNCING", salesCell.getMarker());
        assertEquals(salesTask.getId(), salesCell.getLatestTaskId());
        assertEquals(NoonPullTaskStatus.RUNNING.name(), salesCell.getLatestTaskStatus());
        assertNotNull(salesCell.getLastSyncAt());
    }

    private NoonCallStoreDataView.CategoryCell cell(NoonCallStoreDataView.Row row, NoonDataCategory category) {
        return row.getCategories().stream()
                .filter((cell) -> category == cell.getCategory())
                .findFirst()
                .orElseThrow();
    }

    private static NoonDataCompletenessRecord row(
            Long id,
            NoonDataCategory category,
            NoonDataLatestStatus latestStatus,
            NoonDataHistoryStatus historyStatus
    ) {
        NoonDataCompletenessRecord row = new NoonDataCompletenessRecord();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setStoreCode("STR108065-NAE");
        row.setSiteCode("AE");
        row.setCategory(category);
        row.setLatestStatus(latestStatus);
        row.setHistoryStatus(historyStatus);
        row.setLatestDataDate(LocalDate.parse("2026-05-24"));
        row.setPatrolEnabled(true);
        row.setActiveGapCount(latestStatus == NoonDataLatestStatus.READY ? 0 : 1);
        row.setCreatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        row.setUpdatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        return row;
    }

    private static NoonDataGapWindowRecord gap(Long id, NoonDataCategory category, NoonDataGapStatus status) {
        NoonDataGapWindowRecord gap = new NoonDataGapWindowRecord();
        gap.setId(id);
        gap.setCompletenessId(900004L);
        gap.setOwnerUserId(307L);
        gap.setStoreCode("STR108065-NAE");
        gap.setSiteCode("AE");
        gap.setCategory(category);
        gap.setWindowType(NoonDataGapWindowType.LATEST_DAILY);
        gap.setDateFrom(LocalDate.parse("2026-05-24"));
        gap.setDateTo(LocalDate.parse("2026-05-24"));
        gap.setStatus(status);
        gap.setRetryable(Boolean.TRUE);
        gap.setRequiresManualAction(Boolean.FALSE);
        gap.setCreatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        gap.setUpdatedAt(LocalDateTime.parse("2026-05-25T00:00:00"));
        return gap;
    }

    private static NoonPullTaskRecord createTask(
            NoonPullFoundationService foundationService,
            NoonPullType pullType,
            NoonPullDataDomain dataDomain,
            String targetIdentity
    ) {
        NoonPullPlanRecord plan = foundationService.createPlan(NoonPullPlanDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .pullType(pullType)
                .dataDomain(dataDomain)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .scheduleExpression("test")
                .build());
        return foundationService.createTaskForPlan(plan.getId(), NoonPullTaskDraft.builder()
                .ownerUserId(307L)
                .storeCode("STR108065-NAE")
                .siteCode("AE")
                .pullType(pullType)
                .dataDomain(dataDomain)
                .triggerMode(NoonPullTriggerMode.SCHEDULED_DAILY)
                .targetIdentity(targetIdentity)
                .targetDateFrom(LocalDate.parse("2026-05-24"))
                .targetDateTo(LocalDate.parse("2026-05-24"))
                .build()).orElseThrow();
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
            gaps.add(record.copy());
        }

        @Override
        public List<NoonDataGapWindowRecord> listGapWindows(NoonDataGapQuery query) {
            return gaps.stream()
                    .filter((gap) -> query == null || query.getCategory() == null || gap.getCategory() == query.getCategory())
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
                    .filter((task) -> task.getStatus() == NoonPullTaskStatus.QUEUED || task.getStatus() == NoonPullTaskStatus.RUNNING)
                    .findFirst()
                    .map(NoonPullTaskRecord::copy)
                    .orElse(null);
        }

        @Override
        public NoonPullTaskRecord selectLatestTaskByLockKey(String activeLockKey) {
            return tasks.values().stream()
                    .filter((task) -> Objects.equals(activeLockKey, task.getActiveLockKey()))
                    .max(Comparator.comparing(NoonPullTaskRecord::getId))
                    .map(NoonPullTaskRecord::copy)
                    .orElse(null);
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
