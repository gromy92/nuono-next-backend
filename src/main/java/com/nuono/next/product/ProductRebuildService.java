package com.nuono.next.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.productlisting.ProductListingDraftCommand;
import com.nuono.next.productlisting.ProductListingDraftView;
import com.nuono.next.productlisting.ProductListingRealRunSubmission;
import com.nuono.next.productlisting.ProductListingService;
import com.nuono.next.productlisting.ProductListingTaskRecord;
import com.nuono.next.productlisting.ProductListingTaskView;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Profile("local-db")
public class ProductRebuildService {

    private static final Logger log = LoggerFactory.getLogger(ProductRebuildService.class);
    private static final String SOURCE_TYPE_PRODUCT_REBUILD = "PRODUCT_REBUILD";
    private static final long LISTING_CLAIM_LEASE_MINUTES = 5L;

    private final ProductManagementMapper productManagementMapper;
    private final ProductListingMapper productListingMapper;
    private final ProductListingService productListingService;
    private final ObjectMapper objectMapper;
    private final ProductRebuildListingState listingState;
    private final ProductRebuildWorkflowStore workflowStore;

    @Value("${nuono.product-management.rebuild-listing.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${nuono.product-management.rebuild-listing.scheduler.max-items-per-tick:2}")
    private int maxItemsPerTick;

    public ProductRebuildService(
            ProductManagementMapper productManagementMapper,
            ProductListingMapper productListingMapper,
            ProductListingService productListingService,
            ObjectMapper objectMapper
    ) {
        this.productManagementMapper = productManagementMapper;
        this.productListingMapper = productListingMapper;
        this.productListingService = productListingService;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.listingState = new ProductRebuildListingState(this.objectMapper);
        this.workflowStore = new ProductRebuildWorkflowStore(
                this.productManagementMapper,
                this.objectMapper
        );
    }
    @Scheduled(
            initialDelayString = "${nuono.product-management.rebuild-listing.scheduler.initial-delay-ms:5000}",
            fixedDelayString = "${nuono.product-management.rebuild-listing.scheduler.fixed-delay-ms:5000}"
    )
    public void productRebuildListingSchedulerTick() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            processReadyRebuildDeletes(maxItemsPerTick);
            reconcileSubmittedRebuildListings(maxItemsPerTick);
        } catch (RuntimeException exception) {
            if (isMissingProductRebuildTable(exception)) {
                log.debug("product-rebuild listing scheduler skipped because product tables are not initialized yet");
                return;
            }
            log.warn("product-rebuild listing scheduler failed: {}", shrink(exception.getMessage()), exception);
        }
    }

    int processReadyRebuildDeletes(int limit) {
        int submitted = 0;
        for (ProductPublishTaskRecord task : productManagementMapper.selectProductRebuildDeleteTasksReadyForListing(
                staleClaimBefore(),
                Math.max(1, limit)
        )) {
            try {
                if (processReadyRebuildDeleteTask(task)) {
                    submitted++;
                }
            } catch (RuntimeException exception) {
                recordRebuildListingFailure(task, exception);
                log.warn(
                        "product-rebuild listing submission failed deleteTaskId={} owner={} store={} error={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getOwnerUserId(),
                        task == null ? null : task.getStoreCode(),
                        shrink(exception.getMessage()),
                        exception
                );
            }
        }
        return submitted;
    }
    int reconcileSubmittedRebuildListings(int limit) {
        int reconciled = 0;
        for (ProductPublishTaskRecord task : productManagementMapper.selectProductRebuildDeleteTasksPendingListingReconciliation(
                Math.max(1, limit)
        )) {
            try {
                if (reconcileSubmittedRebuildListingTask(task)) {
                    reconciled++;
                }
            } catch (RuntimeException exception) {
                recordRebuildListingFailure(task, exception);
                log.warn(
                        "product-rebuild listing reconciliation failed deleteTaskId={} owner={} store={} error={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getOwnerUserId(),
                        task == null ? null : task.getStoreCode(),
                        shrink(exception.getMessage()),
                        exception
                );
            }
        }
        return reconciled;
    }
    private boolean processReadyRebuildDeleteTask(ProductPublishTaskRecord task) {
        if (task == null || !"synced".equalsIgnoreCase(task.getStatus())) {
            return false;
        }
        ProductListingDraftCommand draft = readRebuildListingDraft(task);
        String storeCode = requireText(firstNonBlank(draft.getStoreCode(), task.getStoreCode()), "重建上架缺少店铺编码。");
        Long ownerUserId = requireOwnerUserId(task);
        draft.setStoreCode(storeCode);
        if (!StringUtils.hasText(draft.getSourceType())) {
            draft.setSourceType(SOURCE_TYPE_PRODUCT_REBUILD);
        }
        if (draft.getSourceRefId() == null) {
            draft.setSourceRefId(firstNonNull(draft.getRebuildSourceProductMasterId(), task.getProductMasterId()));
        }
        if (draft.getRebuildSourceProductMasterId() == null) {
            draft.setRebuildSourceProductMasterId(firstNonNull(task.getProductMasterId(), draft.getSourceRefId()));
        }
        applyRebuildListingDraftDefaults(draft);
        requireText(draft.getInheritedListingStartedAt(), "重建上架缺少旧 PSKU 上架时间。");
        ProductListingTaskRecord existingRealRun = productListingMapper.selectLatestRealRunTaskByDraftSource(
                ownerUserId,
                storeCode,
                draft.getSourceType(),
                draft.getSourceRefId()
        );
        if (existingRealRun != null) {
            recordExistingRealRunState(task, ownerUserId, storeCode, existingRealRun);
            return false;
        }
        String claimToken = workflowStore.claim(
                task,
                staleClaimBefore(),
                token -> listingState.claimed(LISTING_CLAIM_LEASE_MINUTES, token)
        );
        if (claimToken == null) {
            return false;
        }
        existingRealRun = productListingMapper.selectLatestRealRunTaskByDraftSource(
                ownerUserId,
                storeCode,
                draft.getSourceType(),
                draft.getSourceRefId()
        );
        try {
            if (existingRealRun != null) {
                recordExistingRealRunState(task, ownerUserId, storeCode, existingRealRun, claimToken);
                return false;
            }
            if (!workflowStore.renew(
                    task,
                    claimToken,
                    listingState.claimed(LISTING_CLAIM_LEASE_MINUTES, claimToken)
            )) {
                return false;
            }

            BusinessAccessContext context = businessAccessContext(ownerUserId, storeCode);
            ProductListingRealRunSubmission submission = productListingService.submitConfirmedRealRunFromDraft(
                    context,
                    draft,
                    "confirmed by product rebuild after delete task " + task.getId()
            );
            ProductListingDraftView draftView = submission == null ? null : submission.getDraft();
            ProductListingTaskView dryRun = submission == null ? null : submission.getDryRun();
            if (dryRun == null || !"validated".equalsIgnoreCase(dryRun.getStatus())) {
                workflowStore.complete(task, claimToken, listingState.create(
                        "listing_validation_failed",
                        draftView == null ? null : draftView.getDraftId(),
                        dryRun == null ? null : dryRun.getTaskId(),
                        null,
                        dryRun == null ? null : dryRun.getStatus(),
                        dryRun == null ? "dry_run_missing" : dryRun.getFailureCode(),
                        dryRun == null ? "重建上架 dry-run 没有返回任务。" : dryRun.getFailureMessage()
                ));
                return false;
            }

            ProductListingTaskView realRun = submission.getRealRun();
            ProductListingTaskRecord latestRealRun = loadSubmittedRealRun(ownerUserId, realRun);
            String listingStatus = firstNonBlank(
                    latestRealRun == null ? null : latestRealRun.getStatus(),
                    realRun == null ? null : realRun.getStatus()
            );
            boolean completed = workflowStore.complete(task, claimToken, listingState.create(
                    listingState.statusForListing(
                            listingStatus,
                            firstNonBlank(
                                    latestRealRun == null ? null : latestRealRun.getFailureCode(),
                                    realRun == null ? null : realRun.getFailureCode()
                            )
                    ),
                    draftView == null ? null : draftView.getDraftId(),
                    dryRun.getTaskId(),
                    realRun == null ? null : realRun.getTaskId(),
                    listingStatus,
                    firstNonBlank(
                            latestRealRun == null ? null : latestRealRun.getFailureCode(),
                            realRun == null ? null : realRun.getFailureCode()
                    ),
                    firstNonBlank(
                            latestRealRun == null ? null : latestRealRun.getFailureMessage(),
                            realRun == null ? null : realRun.getFailureMessage()
                    ),
                    latestRealRun == null ? null : latestRealRun.getNoonResultJson()
            ));
            return completed
                    && realRun != null
                    && realRun.getTaskId() != null
                    && !"rejected".equalsIgnoreCase(realRun.getStatus());
        } catch (RuntimeException exception) {
            try {
                workflowStore.complete(task, claimToken, listingState.create(
                        "listing_failed",
                        null,
                        null,
                        null,
                        null,
                        "product_rebuild_listing_failed",
                        shrink(exception.getMessage())
                ));
            } catch (RuntimeException persistenceFailure) {
                log.warn(
                        "product-rebuild failed to persist fenced listing failure deleteTaskId={} claimToken={}",
                        task.getId(),
                        claimToken,
                        persistenceFailure
                );
            }
            log.warn(
                    "product-rebuild claimed listing submission failed deleteTaskId={} claimToken={} error={}",
                    task.getId(),
                    claimToken,
                    shrink(exception.getMessage()),
                    exception
            );
            return false;
        }
    }
    private boolean reconcileSubmittedRebuildListingTask(ProductPublishTaskRecord task) {
        ProductListingDraftCommand draft = readRebuildListingDraft(task);
        String storeCode = requireText(firstNonBlank(draft.getStoreCode(), task.getStoreCode()), "重建上架缺少店铺编码。");
        Long ownerUserId = requireOwnerUserId(task);
        ProductListingTaskRecord existingRealRun = productListingMapper.selectLatestRealRunTaskByDraftSource(
                ownerUserId,
                storeCode,
                firstNonBlank(draft.getSourceType(), SOURCE_TYPE_PRODUCT_REBUILD),
                firstNonNull(
                        firstNonNull(draft.getSourceRefId(), draft.getRebuildSourceProductMasterId()),
                        task.getProductMasterId()
                )
        );
        if (existingRealRun == null) {
            return false;
        }
        recordExistingRealRunState(task, ownerUserId, storeCode, existingRealRun);
        return true;
    }
    private void recordExistingRealRunState(
            ProductPublishTaskRecord task,
            Long ownerUserId,
            String storeCode,
            ProductListingTaskRecord existingRealRun
    ) {
        recordExistingRealRunState(task, ownerUserId, storeCode, existingRealRun, null);
    }

    private void recordExistingRealRunState(
            ProductPublishTaskRecord task,
            Long ownerUserId,
            String storeCode,
            ProductListingTaskRecord existingRealRun,
            String claimToken
    ) {
        String existingListingStatus = existingRealRun.getStatus();
        if ("succeeded".equalsIgnoreCase(existingListingStatus)) {
            productListingService.replaySuccessfulProjectionBackfill(
                    businessAccessContext(ownerUserId, storeCode),
                    existingRealRun.getId()
            );
        }
        Map<String, Object> state = listingState.create(
                listingState.statusForExisting(existingListingStatus, existingRealRun.getFailureCode()),
                null,
                null,
                existingRealRun.getId(),
                existingListingStatus,
                existingRealRun.getFailureCode(),
                existingRealRun.getFailureMessage(),
                existingRealRun.getNoonResultJson()
        );
        if (StringUtils.hasText(claimToken)) {
            workflowStore.complete(task, claimToken, state);
        } else {
            workflowStore.record(task, state);
        }
    }

    private ProductListingTaskRecord loadSubmittedRealRun(Long ownerUserId, ProductListingTaskView realRun) {
        if (ownerUserId == null || realRun == null || realRun.getTaskId() == null) {
            return null;
        }
        return productListingMapper.selectTaskById(realRun.getTaskId(), ownerUserId);
    }

    private void applyRebuildListingDraftDefaults(ProductListingDraftCommand draft) {
        if (draft == null) {
            return;
        }
        if (draft.getPurchasePrice() == null && draft.getPrice() != null) {
            draft.setPurchasePrice(draft.getPrice());
        }
        if (!StringUtils.hasText(draft.getSupplyEvidenceType())) {
            draft.setSupplyEvidenceType(SOURCE_TYPE_PRODUCT_REBUILD);
        }
    }

    private ProductListingDraftCommand readRebuildListingDraft(ProductPublishTaskRecord task) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("重建删除任务不存在。");
        }
        JsonNode request = readJson(task.getRequestJson());
        if (!"product-rebuild".equalsIgnoreCase(text(request, "rebuildAction"))) {
            throw new IllegalArgumentException("删除任务不是商品重建任务。");
        }
        JsonNode draftNode = request.path("rebuildListingDraft");
        if (draftNode == null || draftNode.isMissingNode() || draftNode.isNull()) {
            throw new IllegalArgumentException("重建删除任务缺少上架草稿。");
        }
        try {
            return objectMapper.treeToValue(draftNode, ProductListingDraftCommand.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("重建上架草稿读取失败：" + exception.getMessage(), exception);
        }
    }

    private void recordRebuildListingFailure(ProductPublishTaskRecord task, RuntimeException exception) {
        if (task == null || task.getId() == null || task.getOwnerUserId() == null) {
            return;
        }
        workflowStore.record(task, listingState.create(
                "listing_failed",
                null,
                null,
                null,
                null,
                "product_rebuild_listing_failed",
                shrink(exception.getMessage())
        ));
    }

    private BusinessAccessContext businessAccessContext(Long ownerUserId, String storeCode) {
        return BusinessAccessContext.builder()
                .sessionUserId(ownerUserId)
                .businessOwnerUserId(ownerUserId)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of(storeCode))
                .storeOwnerUserIds(Map.of(storeCode, ownerUserId))
                .menuPaths(Set.of("/purchase/listing"))
                .build();
    }

    private JsonNode readJson(String json) {
        if (!StringUtils.hasText(json)) {
            return MissingNode.getInstance();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception exception) {
            return MissingNode.getInstance();
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.isValueNode() ? value.asText() : value.toString();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private Long requireOwnerUserId(ProductPublishTaskRecord task) {
        Long ownerUserId = task == null ? null : task.getOwnerUserId();
        if (ownerUserId == null) {
            throw new IllegalArgumentException("重建删除任务缺少老板上下文。");
        }
        return ownerUserId;
    }

    private String requireText(String value, String message) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private Long firstNonNull(Long first, Long second) {
        return first != null ? first : second;
    }

    private LocalDateTime staleClaimBefore() {
        return LocalDateTime.now().minusMinutes(LISTING_CLAIM_LEASE_MINUTES);
    }

    private boolean isMissingProductRebuildTable(RuntimeException exception) {
        String message = shrink(exception == null ? null : exception.getMessage()).toLowerCase();
        return message.contains("product_publish_task")
                || message.contains("product_listing_task")
                || message.contains("product_listing_draft")
                || message.contains("doesn't exist")
                || message.contains("does not exist");
    }

    private String shrink(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
