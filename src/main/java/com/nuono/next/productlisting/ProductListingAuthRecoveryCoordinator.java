package com.nuono.next.productlisting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingAuthRecoveryMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Profile("local-db")
public class ProductListingAuthRecoveryCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            ProductListingAuthRecoveryCoordinator.class);
    private static final String MANUAL_REVIEW_MESSAGE =
            "Noon 授权已恢复，但此前写入状态无法由只读回读完整确认；请人工核对后再决定是否继续，禁止重新创建商品。";
    private static final String SUPERSEDED_MESSAGE =
            "同一 dry-run、店铺 PSKU 或店铺 Barcode 已有更新的权威上架任务，本授权挂起任务不再恢复。";
    private static final int AUTH_RECOVERY_SCAN_MULTIPLIER = 4;

    private final ProductListingAuthRecoveryMapper authRecoveryMapper;
    private final ProductListingMapper listingMapper;
    private final ProductListingNoonWriteAdapter noonWriteAdapter;
    private final ProductListingService listingService;
    private final ProductListingReauthenticationCommitter reauthenticationCommitter;
    private final ProductListingCreateOutcomeService createOutcomeService;
    private final ObjectMapper objectMapper;
    private final AtomicLong pendingTaskScanCursor = new AtomicLong(0L);

    public ProductListingAuthRecoveryCoordinator(
            ProductListingAuthRecoveryMapper authRecoveryMapper,
            ProductListingMapper listingMapper,
            ProductListingNoonWriteAdapter noonWriteAdapter,
            ProductListingService listingService,
            ProductListingReauthenticationCommitter reauthenticationCommitter,
            ProductListingCreateOutcomeService createOutcomeService,
            ObjectMapper objectMapper
    ) {
        this.authRecoveryMapper = authRecoveryMapper;
        this.listingMapper = listingMapper;
        this.noonWriteAdapter = noonWriteAdapter;
        this.listingService = listingService;
        this.reauthenticationCommitter = reauthenticationCommitter;
        this.createOutcomeService = createOutcomeService;
        this.objectMapper = objectMapper;
    }

    public boolean resumeIfAuthorizationRestored(
            BusinessAccessContext context,
            ProductListingTaskRecord task
    ) {
        if (task == null
                || !"REAL_RUN".equals(task.getMode())
                || !ProductListingWriteAuthRecovery.FAILURE_CODE.equals(task.getFailureCode())) {
            return false;
        }
        ProductListingNoonWriteResult previous = readResult(task.getNoonResultJson());
        if (previous.getRecoveryId() == null) {
            return false;
        }
        ProductListingDraftCommand draft = readDraft(task.getInputSnapshotJson());
        if (!Boolean.TRUE.equals(previous.getWriteMayHaveOccurred())
                && supersedeIfNotAuthoritative(task, draft, previous)) {
            return true;
        }
        ProductListingNoonWriteRequest request = recoveryProbeRequest(task, draft);
        if (noonWriteAdapter.isAuthorizationRecoveryPending(request)) {
            return false;
        }
        if (!Boolean.TRUE.equals(previous.getWriteMayHaveOccurred())) {
            return resumePreWriteTask(context, task, draft, previous);
        }
        if (ProductListingCreateContinuationPolicy.needsReadOnlyReferenceResolution(
                previous, task.getId(), task.getStoreCode(), draft.getPsku())) {
            advance(
                    context,
                    task,
                    previous,
                    ProductListingReauthenticationCommitter.ResumeAction
                            .CHECK_CREATE_RESULT
            );
            createOutcomeService.verify(context, task.getId());
            return true;
        }
        if (ProductListingCreateContinuationPolicy.hasDurableCreateReference(
                previous, task.getId(), task.getStoreCode(), draft.getPsku())) {
            if (ProductListingWorkflowEvidence.hasFailedWriteStep(previous)) {
                advance(
                        context,
                        task,
                        previous,
                        ProductListingReauthenticationCommitter.ResumeAction
                                .CONTINUE_AFTER_CREATE
                );
                return true;
            }
            advance(
                    context,
                    task,
                    previous,
                    ProductListingReauthenticationCommitter.ResumeAction
                            .VERIFY_READBACK
            );
            listingService.verifyRealRunReadBack(context, task.getId());
            return true;
        }
        return markManualReview(task, previous) == 1;
    }

    public int resumePendingTasks(int limit) {
        int resumeLimit = Math.max(1, limit);
        int candidateLimit = (int) Math.min(
                Integer.MAX_VALUE,
                (long) resumeLimit * AUTH_RECOVERY_SCAN_MULTIPLIER
        );
        int resumed = 0;
        for (ProductListingTaskRecord task :
                authRecoveryMapper.selectPendingAuthRecoveryTasks(
                        pendingTaskScanCursor.get(), candidateLimit)) {
            if (task != null && task.getId() != null) {
                pendingTaskScanCursor.set(task.getId());
            }
            try {
                if (resumeIfAuthorizationRestored(businessContext(task), task)) {
                    resumed++;
                    if (resumed >= resumeLimit) {
                        break;
                    }
                }
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "product-listing auth recovery failed: taskId={}, store={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getStoreCode(),
                        exception
                );
            }
        }
        return resumed;
    }

    private boolean resumePreWriteTask(
            BusinessAccessContext context,
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            ProductListingNoonWriteResult previous
    ) {
        if (!"failed".equals(task.getStatus())
                || ProductListingCreateContinuationPolicy.hasDurableCreateReference(
                previous, task.getId(), task.getStoreCode(), draft.getPsku())
                || ProductListingCreateContinuationPolicy.needsReadOnlyReferenceResolution(
                previous, task.getId(), task.getStoreCode(), draft.getPsku())) {
            throw new IllegalStateException(
                    "Pre-write listing authorization recovery contains unexpected Noon create evidence."
            );
        }
        advance(
                context,
                task,
                previous,
                ProductListingReauthenticationCommitter.ResumeAction.RETRY_CREATE
        );
        listingService.executeSubmittedRealRunTask(task.getId());
        return true;
    }

    private void advance(
            BusinessAccessContext context,
            ProductListingTaskRecord task,
            ProductListingNoonWriteResult previous,
            ProductListingReauthenticationCommitter.ResumeAction action
    ) {
        ProductListingWorkflowView advanced = reauthenticationCommitter
                .advanceSharedRecovery(
                        context,
                        task.getId(),
                        task.getOwnerUserId(),
                        task.getNoonResultJson(),
                        previous.getRecoveryId(),
                        action
                );
        if (advanced == null || advanced.getNextAction() != nextAction(action)) {
            throw new IllegalStateException(
                    "Listing authorization recovery advanced to an unexpected workflow action."
            );
        }
    }

    private ProductListingWorkflowView.NextAction nextAction(
            ProductListingReauthenticationCommitter.ResumeAction action
    ) {
        if (action == ProductListingReauthenticationCommitter.ResumeAction.RETRY_CREATE) {
            return ProductListingWorkflowView.NextAction.WAIT;
        }
        if (action == ProductListingReauthenticationCommitter.ResumeAction.CHECK_CREATE_RESULT) {
            return ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT;
        }
        if (action == ProductListingReauthenticationCommitter.ResumeAction.CONTINUE_AFTER_CREATE) {
            return ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE;
        }
        return ProductListingWorkflowView.NextAction.VERIFY_READBACK;
    }

    private int markManualReview(ProductListingTaskRecord task, ProductListingNoonWriteResult result) {
        return authRecoveryMapper.markAuthRecoveryManualReview(
                task.getId(), task.getOwnerUserId(), task.getNoonResultJson(),
                result.getRecoveryId(),
                MANUAL_REVIEW_MESSAGE
        );
    }

    private boolean supersedeIfNotAuthoritative(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft,
            ProductListingNoonWriteResult result
    ) {
        ProductListingTaskRecord bySource = listingMapper
                .selectRealWriteAttemptTaskBySourceTaskId(
                        task.getOwnerUserId(), task.getSourceTaskId());
        ProductListingTaskRecord byPartnerSku = StringUtils.hasText(draft.getPsku())
                ? listingMapper.selectListedPartnerSkuTask(
                task.getOwnerUserId(), task.getStoreCode(), draft.getPsku())
                : null;
        ProductListingTaskRecord byBarcode = StringUtils.hasText(draft.getBarcode())
                ? listingMapper.selectReservedBarcodeTask(
                task.getOwnerUserId(), task.getStoreCode(), draft.getBarcode())
                : null;
        if (sameTask(task, bySource) && sameTask(task, byPartnerSku) && sameTask(task, byBarcode)) {
            return false;
        }
        return authRecoveryMapper.markPreWriteAuthRecoverySuperseded(
                task.getId(), task.getOwnerUserId(), task.getNoonResultJson(),
                result.getRecoveryId(), SUPERSEDED_MESSAGE) == 1;
    }

    private boolean sameTask(
            ProductListingTaskRecord expected,
            ProductListingTaskRecord authoritative
    ) {
        return authoritative == null || expected.getId().equals(authoritative.getId());
    }

    private ProductListingNoonWriteRequest recoveryProbeRequest(
            ProductListingTaskRecord task,
            ProductListingDraftCommand draft
    ) {
        ProductListingNoonWriteRequest request = new ProductListingNoonWriteRequest();
        request.setOwnerUserId(task.getOwnerUserId());
        request.setStoreCode(task.getStoreCode());
        request.setDraftId(task.getDraftId());
        request.setDryRunTaskId(task.getSourceTaskId());
        request.setRealRunTaskId(task.getId());
        request.setSubmittedBy(task.getSubmittedBy());
        request.setDraft(draft);
        return request;
    }

    private ProductListingDraftCommand readDraft(String json) {
        try {
            return objectMapper.readValue(json, ProductListingDraftCommand.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse listing auth-recovery draft.", exception);
        }
    }

    private BusinessAccessContext businessContext(ProductListingTaskRecord task) {
        return BusinessAccessContext.builder()
                .sessionUserId(task.getOwnerUserId())
                .businessOwnerUserId(task.getOwnerUserId())
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of(task.getStoreCode()))
                .storeOwnerUserIds(Map.of(task.getStoreCode(), task.getOwnerUserId()))
                .menuPaths(Set.of("/purchase/listing"))
                .build();
    }

    private ProductListingNoonWriteResult readResult(String json) {
        try {
            ProductListingNoonWriteResult result =
                    objectMapper.readValue(json, ProductListingNoonWriteResult.class);
            if (result == null) {
                throw new IllegalStateException("Listing auth-recovery result is missing.");
            }
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse listing auth-recovery result.", exception);
        }
    }
}
