package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskRepository;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisRefreshServiceTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    private InMemoryOperationalTaskRepository taskRepository;
    private List<Runnable> submittedTasks;
    private CompetitorAnalysisRefreshService service;

    @BeforeEach
    void setUp() {
        taskRepository = new InMemoryOperationalTaskRepository();
        submittedTasks = new ArrayList<>();
        service = new CompetitorAnalysisRefreshService(
                mapper,
                new OperationalTaskService(
                        taskRepository,
                        Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
                ),
                (accountKey, task) -> submittedTasks.add(task),
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void refreshRejectsWatchProductWithoutActiveKeywords() {
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.requestRefresh(operatorContext(), 180123L)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("COMPETITOR_NO_ACTIVE_KEYWORD", error.getReason());
        assertTrue(taskRepository.tasks.isEmpty());
        verify(mapper, never()).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshReusesCurrentRunningTaskAndRun() {
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220123L);
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(searchRun(220123L, 150000L, "RUNNING"));

        CompetitorRefreshRunView first = service.requestRefresh(operatorContext(), 180123L);
        CompetitorRefreshRunView second = service.requestRefresh(operatorContext(), 180123L);

        assertEquals(first.getTaskId(), second.getTaskId());
        assertEquals(first.getRunId(), second.getRunId());
        assertEquals(1, taskRepository.tasks.size());
        assertEquals(1, submittedTasks.size());
        verify(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshFailsStaleTaskAndCreatesNewTaskRun() {
        OperationalTask stale = runningTask(150000L);
        stale.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        taskRepository.insert(stale);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct());
        when(mapper.selectSearchRunByTaskId(150000L)).thenReturn(searchRun(220000L, 150000L, "RUNNING"));
        when(mapper.listActiveKeywordsByWatchProductId(180123L)).thenReturn(List.of(keyword(190001L, "laundry basket")));
        when(mapper.nextSearchRunId()).thenReturn(220124L);

        CompetitorRefreshRunView view = service.requestRefresh(operatorContext(), 180123L);

        assertEquals(150001L, view.getTaskId());
        assertEquals(220124L, view.getRunId());
        assertEquals(OperationalTaskStatus.FAILED, taskRepository.selectById(150000L).getStatus());
        assertEquals("FAILED_STALE", taskRepository.selectById(150000L).getErrorCode());
        verify(mapper).markSearchRunFailed(220000L, "FAILED_STALE", "刷新任务超过 30 分钟未完成，已自动释放。");
        verify(mapper).insertSearchRun(org.mockito.ArgumentMatchers.any());
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode("ZSELF001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRow keyword(Long id, String keyword) {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setKeyword(keyword);
        row.setKeywordNorm(keyword);
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorSearchRunRow searchRun(Long runId, Long taskId, String status) {
        CompetitorSearchRunRow row = new CompetitorSearchRunRow();
        row.setId(runId);
        row.setWatchProductId(180123L);
        row.setTaskId(taskId);
        row.setTriggerMode("MANUAL_REFRESH");
        row.setStatus(status);
        row.setKeywordTotal(1);
        return row;
    }

    private static OperationalTask runningTask(Long taskId) {
        OperationalTask task = new OperationalTask();
        task.setId(taskId);
        task.setTaskType(CompetitorAnalysisRefreshService.TASK_TYPE);
        task.setNaturalKey("watchProduct:180123");
        task.setOwnerUserId(501L);
        task.setStoreCode("STR108065-NSA");
        task.setSiteCode("SA");
        task.setStatus(OperationalTaskStatus.RUNNING);
        task.setProgressPercent(0);
        task.setMessage("竞品刷新正在后台执行。");
        task.setStartedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        task.setCreatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        task.setUpdatedAt(LocalDateTime.parse("2026-06-06T07:20:00"));
        return task;
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/operations/competitor-analysis"))
                .build();
    }

    private static final class InMemoryOperationalTaskRepository implements OperationalTaskRepository {
        private long nextId = 150000L;
        private final Map<Long, OperationalTask> tasks = new LinkedHashMap<>();

        @Override
        public Long nextId(String sequenceName, Long initialValue) {
            return nextId++;
        }

        @Override
        public void insert(OperationalTask task) {
            tasks.put(task.getId(), task.copy());
            if (task.getId() != null) {
                nextId = Math.max(nextId, task.getId() + 1);
            }
        }

        @Override
        public OperationalTask selectById(Long taskId) {
            OperationalTask task = tasks.get(taskId);
            return task == null ? null : task.copy();
        }

        @Override
        public OperationalTask selectActiveByNaturalKey(String taskType, String naturalKey) {
            return tasks.values().stream()
                    .filter((task) -> taskType.equals(task.getTaskType()))
                    .filter((task) -> naturalKey.equals(task.getNaturalKey()))
                    .filter((task) -> task.getStatus() != null && task.getStatus().isActive())
                    .findFirst()
                    .map(OperationalTask::copy)
                    .orElse(null);
        }

        @Override
        public OperationalTask selectLatestByNaturalKey(String taskType, String naturalKey) {
            return tasks.values().stream()
                    .filter((task) -> taskType.equals(task.getTaskType()))
                    .filter((task) -> naturalKey.equals(task.getNaturalKey()))
                    .max(Comparator.comparing(OperationalTask::getId))
                    .map(OperationalTask::copy)
                    .orElse(null);
        }

        @Override
        public void update(OperationalTask task) {
            tasks.put(task.getId(), task.copy());
        }

        @Override
        public List<OperationalTask> listRecent(String taskType, int limit) {
            return tasks.values().stream()
                    .filter((task) -> taskType == null || taskType.equals(task.getTaskType()))
                    .sorted(Comparator.comparing(OperationalTask::getId).reversed())
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .collect(Collectors.toList());
        }
    }
}
