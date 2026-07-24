package com.nuono.next.product;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.noon.NoonAccountTaskQueue;
import com.nuono.next.productpublicdetail.ProductPublicDetailFetchResult;
import com.nuono.next.productpublicdetail.ProductPublicDetailSyncStatus;
import com.nuono.next.system.task.OperationalTask;
import com.nuono.next.system.task.OperationalTaskPayload;
import com.nuono.next.system.task.OperationalTaskService;
import com.nuono.next.system.task.OperationalTaskStatus;
import java.util.Optional;
import java.util.function.BiFunction;
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
    private final BiFunction<Long, String, Long> storeScopeResolver;
    private final DetailFrontendFetcher frontendFetcher;
    @Autowired
    public ProductDetailBaselineBackfillService(
            OperationalTaskService operationalTaskService,
            NoonAccountTaskQueue noonAccountTaskQueue,
            ProductManagementMapper productManagementMapper,
            ProductDetailFrontendFetchService frontendFetchService
    ) {
        this(
                operationalTaskService,
                noonAccountTaskQueue == null ? null : noonAccountTaskQueue::submit,
                productManagementMapper == null ? null : productManagementMapper::selectLogicalStoreIdByOwnerStoreCode,
                frontendFetchService == null ? null : frontendFetchService::fetch
        );
    }
    ProductDetailBaselineBackfillService(
            OperationalTaskService operationalTaskService,
            TaskSubmitter taskSubmitter,
            BiFunction<Long, String, Long> storeScopeResolver,
            DetailFrontendFetcher frontendFetcher
    ) {
        this.operationalTaskService = operationalTaskService;
        this.taskSubmitter = taskSubmitter == null ? (accountKey, task) -> task.run() : taskSubmitter;
        this.storeScopeResolver = storeScopeResolver;
        this.frontendFetcher = frontendFetcher;
    }
    public String enqueue(ProductMasterFetchCommand command, String reason, DetailBaselineBackfillRunner runner) {
        return enqueue(command, reason, runner, true);
    }
    String enqueueInline(ProductMasterFetchCommand command, String reason, DetailBaselineBackfillRunner runner) {
        return enqueue(command, reason, runner, false);
    }
    private String enqueue(ProductMasterFetchCommand command, String reason, DetailBaselineBackfillRunner runner, boolean async) {
        if (operationalTaskService == null || runner == null) return "disabled";
        String naturalKey = naturalKey(command);
        if (!StringUtils.hasText(naturalKey)) return "skipped";
        Optional<OperationalTask> active = operationalTaskService.findActive(TASK_TYPE, naturalKey);
        if (active.isPresent()) return "preparing";
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
        if (async) {
            taskSubmitter.submit(
                    accountKey(backfillCommand),
                    () -> runBackfill(task.getId(), backfillCommand, reason, runner)
            );
        } else {
            runBackfill(task.getId(), backfillCommand, reason, runner);
        }
        return "preparing";
    }
    public BackfillState state(Long ownerUserId, String storeCode, String skuParent) {
        if (operationalTaskService == null) {
            return null;
        }
        String naturalKey = naturalKey(ownerUserId, storeCode, skuParent);
        return stateByNaturalKey(naturalKey);
    }
    public BackfillState stateForLogicalStore(Long ownerUserId, Long logicalStoreId, String skuParent) {
        if (operationalTaskService == null) {
            return null;
        }
        String naturalKey = naturalKeyForLogicalStore(ownerUserId, logicalStoreId, skuParent);
        return stateByNaturalKey(naturalKey);
    }
    private BackfillState stateByNaturalKey(String naturalKey) {
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
            ProductPublicDetailFetchResult frontendResult = frontendFetcher == null
                    ? ProductPublicDetailFetchResult.of(
                            ProductPublicDetailSyncStatus.FAILED,
                            "Noon 前台详情拉取服务不可用。"
                    )
                    : frontendFetcher.fetch(command, taskId);
            if (frontendResult != null && frontendResult.isUsable()) {
                operationalTaskService.complete(taskId, "{\"ready\":true,\"source\":\"NOON_FRONTEND\"}", "Noon 前台详情已准备。");
                return;
            }
            if (frontendResult == null || !frontendResult.isExplicitlyNotFound()) {
                operationalTaskService.fail(
                        taskId,
                        "PRODUCT_DETAIL_FRONTEND_FETCH_FAILED",
                        firstNonBlank(
                                frontendResult == null ? null : frontendResult.getMessage(),
                                "Noon 前台详情拉取失败；未回退商家后台。"
                        )
                );
                return;
            }
            operationalTaskService.progress(taskId, 50, "Noon 前台明确未找到商品，正在回退商家后台详情接口。");
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
        String storeScopeKey = storeScopeKey(ownerUserId, storeCode);
        return "owner:" + ownerUserId
                + "|" + storeScopeKey
                + "|skuParent:" + normalize(skuParent);
    }
    private String naturalKeyForLogicalStore(Long ownerUserId, Long logicalStoreId, String skuParent) {
        if (ownerUserId == null || logicalStoreId == null || !StringUtils.hasText(skuParent)) {
            return null;
        }
        return "owner:" + ownerUserId
                + "|logicalStore:" + logicalStoreId
                + "|skuParent:" + normalize(skuParent);
    }
    private String storeScopeKey(Long ownerUserId, String storeCode) {
        Long logicalStoreId = resolveLogicalStoreId(ownerUserId, storeCode);
        if (logicalStoreId != null) {
            return "logicalStore:" + logicalStoreId;
        }
        return "store:" + normalize(storeCode);
    }
    private Long resolveLogicalStoreId(Long ownerUserId, String storeCode) {
        if (storeScopeResolver == null || ownerUserId == null || !StringUtils.hasText(storeCode)) {
            return null;
        }
        try {
            return storeScopeResolver.apply(ownerUserId, normalize(storeCode));
        } catch (RuntimeException exception) {
            log.debug(
                    "failed to resolve logical store for product detail baseline backfill owner={} store={} error={}",
                    ownerUserId,
                    storeCode,
                    exception.getMessage()
            );
            return null;
        }
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
    interface DetailFrontendFetcher {
        ProductPublicDetailFetchResult fetch(ProductMasterFetchCommand command, Long taskId);
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
