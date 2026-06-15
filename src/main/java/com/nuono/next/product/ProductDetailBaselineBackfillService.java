package com.nuono.next.product;

import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductDetailBaselineBackfillService {
    static final String TASK_TYPE = "product.detail-baseline-backfill";

    private static final Logger log = LoggerFactory.getLogger(ProductDetailBaselineBackfillService.class);
    private static final String PREPARING_MESSAGE = "正在后台补齐详情基线。";
    private static final String FAILED_MESSAGE = "详情基线补齐失败，请稍后重试。";

    private final OperationalTaskService operationalTaskService;
    private final TaskSubmitter taskSubmitter;

    @Autowired
    public ProductDetailBaselineBackfillService(
            OperationalTaskService operationalTaskService,
            NoonAccountTaskQueue noonAccountTaskQueue
    ) {
        this(
                operationalTaskService,
                noonAccountTaskQueue == null ? null : noonAccountTaskQueue::submit
        );
    }

    ProductDetailBaselineBackfillService(
            OperationalTaskService operationalTaskService,
            TaskSubmitter taskSubmitter
    ) {
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter == null ? (accountKey, task) -> task.run() : taskSubmitter;
    }

    public String enqueue(
            ProductMasterFetchCommand command,
            String reason,
            DetailBaselineBackfillRunner runner
    ) {
        if (operationalTaskService == null || runner == null) {
            return "disabled";
        }
        String naturalKey = naturalKey(command);
        if (!StringUtils.hasText(naturalKey)) {
            return "skipped";
        }
        Optional<OperationalTask> active = operationalTaskService.findActive(TASK_TYPE, naturalKey);
        if (active.isPresent()) {
            return "preparing";
        }

        ProductMasterFetchCommand backfillCommand = copyFetchCommand(command);
        OperationalTask task = operationalTaskService.start(
                TASK_TYPE,
                naturalKey,
                OperationalTaskPayload.builder()
                        .ownerUserId(backfillCommand.getOwnerUserId())
                        .storeCode(backfillCommand.getStoreCode())
                        .payloadJson(payloadJson(backfillCommand, reason))
                        .message(PREPARING_MESSAGE)
                        .build()
        );
        taskSubmitter.submit(
                accountKey(backfillCommand),
                () -> runBackfill(task.getId(), backfillCommand, reason, runner)
        );
        return "preparing";
    }

    public BackfillState state(Long ownerUserId, String storeCode, String skuParent) {
        if (operationalTaskService == null) {
            return null;
        }
        String naturalKey = naturalKey(ownerUserId, storeCode, skuParent);
        if (!StringUtils.hasText(naturalKey)) {
            return null;
        }
        Optional<OperationalTask> latest = operationalTaskService.findLatest(TASK_TYPE, naturalKey);
        if (latest.isEmpty()) {
            return null;
        }
        OperationalTask task = latest.get();
        OperationalTaskStatus status = task.getStatus();
        if (status != null && status.isActive()) {
            return new BackfillState("preparing", firstNonBlank(task.getMessage(), PREPARING_MESSAGE));
        }
        if (status == OperationalTaskStatus.FAILED) {
            return new BackfillState("failed", firstNonBlank(task.getMessage(), FAILED_MESSAGE));
        }
        return null;
    }

    public void cancel(ProductMasterFetchCommand command, String message) {
        if (operationalTaskService == null) {
            return;
        }
        String naturalKey = naturalKey(command);
        if (!StringUtils.hasText(naturalKey)) {
            return;
        }
        operationalTaskService.findActive(TASK_TYPE, naturalKey)
                .ifPresent((task) -> operationalTaskService.cancel(task.getId(), message));
    }

    private void runBackfill(
            Long taskId,
            ProductMasterFetchCommand command,
            String reason,
            DetailBaselineBackfillRunner runner
    ) {
        try {
            operationalTaskService.progress(taskId, 10, PREPARING_MESSAGE);
            ProductMasterSnapshotView snapshot = runner.fetch(
                    command,
                    "detail-baseline-backfill." + normalizeReason(reason)
            );
            if (snapshot != null && snapshot.isReady()) {
                operationalTaskService.complete(taskId, "{\"ready\":true}", "详情基线已准备。");
                return;
            }
            operationalTaskService.fail(
                    taskId,
                    "DETAIL_BASELINE_NOT_READY",
                    firstNonBlank(snapshot == null ? null : snapshot.getMessage(), FAILED_MESSAGE)
            );
        } catch (RuntimeException exception) {
            safeFail(taskId, "DETAIL_BASELINE_BACKFILL_FAILED", firstNonBlank(exception.getMessage(), FAILED_MESSAGE));
            log.warn(
                    "product-management detail baseline backfill failed owner={} store={} skuParent={} reason={} error={}",
                    command == null ? null : command.getOwnerUserId(),
                    command == null ? null : command.getStoreCode(),
                    command == null ? null : command.getSkuParent(),
                    reason,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void safeFail(Long taskId, String errorCode, String message) {
        try {
            operationalTaskService.fail(taskId, errorCode, message);
        } catch (RuntimeException ignored) {
            log.debug("operational task {} was already terminal while recording detail baseline failure", taskId);
        }
    }

    private String naturalKey(ProductMasterFetchCommand command) {
        if (command == null) {
            return null;
        }
        return naturalKey(command.getOwnerUserId(), command.getStoreCode(), command.getSkuParent());
    }

    private String naturalKey(Long ownerUserId, String storeCode, String skuParent) {
        if (ownerUserId == null || !StringUtils.hasText(storeCode) || !StringUtils.hasText(skuParent)) {
            return null;
        }
        return "owner:" + ownerUserId
                + "|store:" + normalize(storeCode)
                + "|skuParent:" + normalize(skuParent);
    }

    private String accountKey(ProductMasterFetchCommand command) {
        if (command == null) {
            return null;
        }
        return command.getOwnerUserId() + "::" + normalize(command.getStoreCode());
    }

    private ProductMasterFetchCommand copyFetchCommand(ProductMasterFetchCommand source) {
        ProductMasterFetchCommand copy = new ProductMasterFetchCommand();
        if (source == null) {
            return copy;
        }
        copy.setOwnerUserId(source.getOwnerUserId());
        copy.setStoreCode(source.getStoreCode());
        copy.setNoonUser(source.getNoonUser());
        copy.setNoonPassword(source.getNoonPassword());
        copy.setSkuParent(source.getSkuParent());
        copy.setPartnerSku(source.getPartnerSku());
        copy.setPskuCode(source.getPskuCode());
        return copy;
    }

    private String payloadJson(ProductMasterFetchCommand command, String reason) {
        return "{"
                + "\"ownerUserId\":" + command.getOwnerUserId()
                + ",\"storeCode\":\"" + jsonEscape(normalize(command.getStoreCode())) + "\""
                + ",\"skuParent\":\"" + jsonEscape(normalize(command.getSkuParent())) + "\""
                + ",\"reason\":\"" + jsonEscape(normalizeReason(reason)) + "\""
                + "}";
    }

    private String normalizeReason(String reason) {
        String normalized = normalize(reason);
        if (!StringUtils.hasText(normalized)) {
            return "default";
        }
        return normalized.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    @FunctionalInterface
    interface DetailBaselineBackfillRunner {
        ProductMasterSnapshotView fetch(ProductMasterFetchCommand command, String reason);
    }

    @FunctionalInterface
    interface TaskSubmitter {
        void submit(String accountKey, Runnable task);
    }

    public static final class BackfillState {
        private final String status;
        private final String message;

        BackfillState(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }
    }
}
