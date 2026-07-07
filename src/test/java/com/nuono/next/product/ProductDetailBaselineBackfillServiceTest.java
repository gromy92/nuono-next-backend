package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskRepository;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductDetailBaselineBackfillServiceTest {
    private InMemoryOperationalTaskRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryOperationalTaskRepository();
    }

    @Test
    void shouldKeepRunningTaskInspectableAfterServiceRestart() {
        List<Runnable> submittedTasks = new ArrayList<>();
        ProductDetailBaselineBackfillService service = newService(
                (accountKey, task) -> submittedTasks.add(task)
        );

        String status = service.enqueue(command(), "open-missing-baseline", (command, reason) -> readySnapshot());

        assertEquals("preparing", status);
        assertEquals(1, submittedTasks.size());

        ProductDetailBaselineBackfillService restartedService = newService(
                (accountKey, task) -> submittedTasks.add(task)
        );
        ProductDetailBaselineBackfillService.BackfillState state = restartedService.state(
                307L,
                "canman",
                "PAPERSAYSB132"
        );

        assertEquals("preparing", state.getStatus());
        assertEquals("正在后台补齐详情基线。", state.getMessage());
    }

    @Test
    void shouldNotSubmitDuplicateTaskWhileOneIsActive() {
        List<Runnable> submittedTasks = new ArrayList<>();
        ProductDetailBaselineBackfillService service = newService(
                (accountKey, task) -> submittedTasks.add(task)
        );

        service.enqueue(command(), "open-missing-baseline", (command, reason) -> readySnapshot());
        service.enqueue(command(), "open-missing-baseline", (command, reason) -> readySnapshot());

        assertEquals(1, submittedTasks.size());
        assertEquals(1, repository.tasks.size());
    }

    @Test
    void shouldDeduplicateActiveBackfillAcrossSiteStoresInSameLogicalStore() {
        List<Runnable> submittedTasks = new ArrayList<>();
        ProductDetailBaselineBackfillService service = newService(
                (ownerUserId, storeCode) -> 50003L,
                (accountKey, task) -> submittedTasks.add(task)
        );

        ProductMasterFetchCommand saCommand = command("STR108065-NSA", "ZEA2BC495A97B0328CF53Z", "PAPERSAYS065");
        ProductMasterFetchCommand aeCommand = command("STR108065-NAE", "ZEA2BC495A97B0328CF53Z", "PAPERSAYS065");

        service.enqueue(saCommand, "open-missing-baseline", (command, reason) -> readySnapshot());
        service.enqueue(aeCommand, "open-missing-baseline", (command, reason) -> readySnapshot());

        assertEquals(1, submittedTasks.size());
        assertEquals(1, repository.tasks.size());
        ProductDetailBaselineBackfillService.BackfillState aeState = service.state(
                307L,
                "STR108065-NAE",
                "ZEA2BC495A97B0328CF53Z"
        );
        assertEquals("preparing", aeState.getStatus());
    }

    @Test
    void stateCanUseResolvedLogicalStoreWithoutResolvingStoreAgain() {
        ProductDetailBaselineBackfillService service = newService(
                (ownerUserId, storeCode) -> 50003L,
                (accountKey, task) -> {}
        );
        ProductMasterFetchCommand saCommand = command("STR108065-NSA", "ZEA2BC495A97B0328CF53Z", "PAPERSAYS065");
        service.enqueue(saCommand, "open-missing-baseline", (command, reason) -> readySnapshot());

        ProductDetailBaselineBackfillService.BackfillState state =
                service.stateForLogicalStore(307L, 50003L, "ZEA2BC495A97B0328CF53Z");

        assertEquals("preparing", state.getStatus());
    }

    @Test
    void shouldKeepBackfillSeparateAcrossDifferentLogicalStores() {
        List<Runnable> submittedTasks = new ArrayList<>();
        ProductDetailBaselineBackfillService service = newService(
                (ownerUserId, storeCode) -> storeCode.endsWith("-NSA") ? 50003L : 50004L,
                (accountKey, task) -> submittedTasks.add(task)
        );

        ProductMasterFetchCommand canmanCommand = command("STR108065-NSA", "ZEA2BC495A97B0328CF53Z", "PAPERSAYS065");
        ProductMasterFetchCommand sggrCommand = command("STR108066-NAE", "ZEA2BC495A97B0328CF53Z", "PAPERSAYS065");

        service.enqueue(canmanCommand, "open-missing-baseline", (command, reason) -> readySnapshot());
        service.enqueue(sggrCommand, "open-missing-baseline", (command, reason) -> readySnapshot());

        assertEquals(2, submittedTasks.size());
        assertEquals(2, repository.tasks.size());
    }

    @Test
    void shouldCompleteSuccessfulBackfill() {
        ProductDetailBaselineBackfillService service = newService((accountKey, task) -> task.run());

        service.enqueue(command(), "open-missing-baseline", (command, reason) -> readySnapshot());

        OperationalTask latest = latestTask();
        assertEquals(OperationalTaskStatus.SUCCEEDED, latest.getStatus());
        assertEquals(100, latest.getProgressPercent());
        assertEquals("详情基线已准备。", latest.getMessage());
        assertNull(service.state(307L, "canman", "PAPERSAYSB132"));
    }

    @Test
    void shouldPersistFailedBackfillMessage() {
        ProductDetailBaselineBackfillService service = newService((accountKey, task) -> task.run());

        service.enqueue(command(), "open-missing-baseline", (command, reason) -> {
            throw new IllegalStateException("Noon timeout");
        });

        OperationalTask latest = latestTask();
        ProductDetailBaselineBackfillService.BackfillState state = service.state(
                307L,
                "canman",
                "PAPERSAYSB132"
        );

        assertEquals(OperationalTaskStatus.FAILED, latest.getStatus());
        assertEquals("DETAIL_BASELINE_BACKFILL_FAILED", latest.getErrorCode());
        assertEquals("failed", state.getStatus());
        assertEquals("Noon timeout", state.getMessage());
    }

    private ProductDetailBaselineBackfillService newService(
            ProductDetailBaselineBackfillService.TaskSubmitter taskSubmitter
    ) {
        return newService((ownerUserId, storeCode) -> null, taskSubmitter);
    }

    private ProductDetailBaselineBackfillService newService(
            BiFunction<Long, String, Long> storeScopeResolver,
            ProductDetailBaselineBackfillService.TaskSubmitter taskSubmitter
    ) {
        return new ProductDetailBaselineBackfillService(
                new OperationalTaskService(
                        repository,
                        Clock.fixed(Instant.parse("2026-06-04T05:00:00Z"), ZoneOffset.UTC)
                ),
                taskSubmitter,
                storeScopeResolver
        );
    }

    private OperationalTask latestTask() {
        assertTrue(!repository.tasks.isEmpty());
        return repository.tasks.values().stream()
                .max(Comparator.comparing(OperationalTask::getId))
                .map(OperationalTask::copy)
                .orElseThrow();
    }

    private ProductMasterFetchCommand command() {
        return command("canman", "PAPERSAYSB132", "PAPERSAYSB132");
    }

    private ProductMasterFetchCommand command(String storeCode, String skuParent, String partnerSku) {
        ProductMasterFetchCommand command = new ProductMasterFetchCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode(storeCode);
        command.setSkuParent(skuParent);
        command.setPartnerSku(partnerSku);
        command.setPskuCode("Z9A");
        return command;
    }

    private ProductMasterSnapshotView readySnapshot() {
        ProductMasterSnapshotView view = new ProductMasterSnapshotView();
        view.setReady(true);
        return view;
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
