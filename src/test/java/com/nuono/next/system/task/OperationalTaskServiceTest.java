package com.nuono.next.system.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationalTaskServiceTest {
    private InMemoryOperationalTaskRepository repository;
    private OperationalTaskService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOperationalTaskRepository();
        service = newService();
    }

    @Test
    void shouldReturnExistingActiveTaskForSameNaturalKey() {
        OperationalTask first = service.start(
                "product.initialization",
                "owner:307|store:canman|site:SA",
                payload()
        );
        OperationalTask duplicate = service.start(
                "product.initialization",
                "owner:307|store:canman|site:SA",
                payload()
        );

        assertEquals(first.getId(), duplicate.getId());
        assertEquals(1, repository.tasks.size());
        assertEquals(OperationalTaskStatus.RUNNING, duplicate.getStatus());
    }

    @Test
    void shouldKeepTaskReadableAcrossServiceInstances() {
        OperationalTask created = service.start(
                "product.detail-baseline",
                "owner:307|store:canman|site:SA|sku:PAPERSAYSB132",
                payload()
        );

        OperationalTaskService restartedService = newService();
        OperationalTask progressed = restartedService.progress(created.getId(), 112, "loaded baseline");
        OperationalTask completed = restartedService.complete(created.getId(), "{\"imported\":1}", "done");

        assertEquals(100, progressed.getProgressPercent());
        assertEquals(OperationalTaskStatus.SUCCEEDED, completed.getStatus());
        assertEquals(100, completed.getProgressPercent());
        assertEquals("{\"imported\":1}", completed.getResultJson());
        assertNotNull(completed.getFinishedAt());
        assertTrue(restartedService.find(created.getId()).isPresent());
    }

    @Test
    void shouldKeepQueuedTaskPendingUntilOneWorkerClaimsIt() {
        OperationalTask queued = service.queue(
                "operations.competitor.refresh",
                "watch-product:180123:rank",
                payload()
        );

        assertEquals(OperationalTaskStatus.QUEUED, queued.getStatus());
        assertNull(queued.getStartedAt());
        assertTrue(service.claimQueued(queued.getId(), "running"));
        assertFalse(service.claimQueued(queued.getId(), "duplicate"));

        OperationalTask claimed = service.find(queued.getId()).orElseThrow();
        assertEquals(OperationalTaskStatus.RUNNING, claimed.getStatus());
        assertEquals("running", claimed.getMessage());
        assertNotNull(claimed.getStartedAt());
    }

    @Test
    void shouldAllowNewTaskAfterPreviousTaskFinished() {
        OperationalTask first = service.start(
                "file.parse.retry",
                "document-group:abc",
                payload()
        );
        service.complete(first.getId(), null, "done");

        OperationalTask second = service.start(
                "file.parse.retry",
                "document-group:abc",
                payload()
        );

        assertNotEquals(first.getId(), second.getId());
        assertEquals(2, repository.tasks.size());
        assertEquals(OperationalTaskStatus.RUNNING, second.getStatus());
    }

    @Test
    void shouldRecordFailureAndRejectTerminalMutation() {
        OperationalTask task = service.start(
                "store.initialization",
                "owner:307|store:canman|site:AE",
                payload()
        );

        OperationalTask failed = service.fail(task.getId(), "NOON_AUTH_FAILED", "invalid username or password");

        assertEquals(OperationalTaskStatus.FAILED, failed.getStatus());
        assertEquals("NOON_AUTH_FAILED", failed.getErrorCode());
        assertEquals("invalid username or password", failed.getMessage());
        assertNotNull(failed.getFinishedAt());
        assertEquals(failed.getId(), service.findLatest(
                "store.initialization",
                "owner:307|store:canman|site:AE"
        ).orElseThrow().getId());
        assertThrows(IllegalStateException.class, () -> service.progress(task.getId(), 30, "retrying"));
    }

    @Test
    void shouldRecordFailureResultJsonWhenProvided() {
        OperationalTask task = service.start(
                "logistics-auto-sync",
                "owner:307|source:CHIC|account:180000",
                payload()
        );

        OperationalTask failed = service.fail(
                task.getId(),
                "PREVIEW_BLOCKED",
                "preview blocked",
                "{\"previewIssueCount\":1}"
        );

        assertEquals(OperationalTaskStatus.FAILED, failed.getStatus());
        assertEquals("PREVIEW_BLOCKED", failed.getErrorCode());
        assertEquals("{\"previewIssueCount\":1}", failed.getResultJson());
        assertEquals(failed.getResultJson(), service.find(task.getId()).orElseThrow().getResultJson());
    }

    @Test
    void shouldNormalizeScopeAndRejectBlankIdentity() {
        OperationalTask task = service.start(
                " product.initialization ",
                " owner:307|store:canman|site:SA ",
                OperationalTaskPayload.builder()
                        .ownerUserId(307L)
                        .storeCode(" canman ")
                        .siteCode(" SA ")
                        .message(" boot ")
                        .build()
        );

        assertEquals("product.initialization", task.getTaskType());
        assertEquals("owner:307|store:canman|site:SA", task.getNaturalKey());
        assertEquals("canman", task.getStoreCode());
        assertEquals("SA", task.getSiteCode());
        assertEquals("boot", task.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.start(" ", "key", payload()));
        assertThrows(IllegalArgumentException.class, () -> service.start("task", " ", payload()));
    }

    private OperationalTaskPayload payload() {
        return OperationalTaskPayload.builder()
                .ownerUserId(307L)
                .storeCode("canman")
                .siteCode("SA")
                .payloadJson("{\"mode\":\"manual\"}")
                .message("starting")
                .build();
    }

    private OperationalTaskService newService() {
        return new OperationalTaskService(
                repository,
                Clock.fixed(Instant.parse("2026-06-04T05:00:00Z"), ZoneOffset.UTC)
        );
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
        public List<OperationalTask> listActiveByTaskType(String taskType, int limit) {
            return new ArrayList<>(tasks.values()).stream()
                    .filter((task) -> taskType.equals(task.getTaskType()))
                    .filter((task) -> task.getStatus() != null && task.getStatus().isActive())
                    .sorted(Comparator.comparing(OperationalTask::getId))
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationalTask> listRecent(String taskType, int limit) {
            return new ArrayList<>(tasks.values()).stream()
                    .filter((task) -> taskType == null || taskType.equals(task.getTaskType()))
                    .sorted(Comparator.comparing(OperationalTask::getId).reversed())
                    .limit(limit)
                    .map(OperationalTask::copy)
                    .collect(Collectors.toList());
        }
    }
}
