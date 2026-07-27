package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListingWorkflowService {

    private final ProductListingMapper mapper;
    private final ProductListingService listingService;
    private final ProductListingWorkflowProjector projector;
    private final ProductListingDryRunFreshness freshness;
    private final ProductListingReauthenticationAttemptMapper
            reauthenticationAttemptMapper;
    private final ProductListingReauthenticationAttemptProjector
            reauthenticationAttemptProjector;

    @Autowired
    public ProductListingWorkflowService(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ObjectMapper objectMapper,
            ObjectProvider<ProductListingReauthenticationAttemptMapper>
                    attemptMapperProvider,
            ObjectProvider<ProductListingReauthenticationAttemptProjector>
                    attemptProjectorProvider
    ) {
        this(
                mapper,
                listingService,
                new ProductListingWorkflowProjector(),
                new ProductListingDryRunFreshness(objectMapper),
                attemptMapperProvider == null
                        ? null
                        : attemptMapperProvider.getIfAvailable(),
                attemptProjectorProvider == null
                        ? null
                        : attemptProjectorProvider.getIfAvailable()
        );
    }

    public ProductListingWorkflowService(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ObjectMapper objectMapper
    ) {
        this(
                mapper,
                listingService,
                new ProductListingWorkflowProjector(),
                new ProductListingDryRunFreshness(objectMapper),
                null,
                null
        );
    }

    ProductListingWorkflowService(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ProductListingWorkflowProjector projector,
            ProductListingDryRunFreshness freshness
    ) {
        this(mapper, listingService, projector, freshness, null, null);
    }

    ProductListingWorkflowService(
            ProductListingMapper mapper,
            ProductListingService listingService,
            ProductListingWorkflowProjector projector,
            ProductListingDryRunFreshness freshness,
            ProductListingReauthenticationAttemptMapper attemptMapper,
            ProductListingReauthenticationAttemptProjector attemptProjector
    ) {
        this.mapper = mapper;
        this.listingService = listingService;
        this.projector = projector;
        this.freshness = freshness;
        this.reauthenticationAttemptMapper = attemptMapper;
        this.reauthenticationAttemptProjector = attemptProjector;
    }

    public ProductListingWorkflowView loadWorkflow(BusinessAccessContext context, Long draftId) {
        ProductListingDraftView draft = listingService.loadDraft(context, draftId);
        return loadWorkflow(context, draft);
    }

    private ProductListingWorkflowView loadWorkflow(
            BusinessAccessContext context,
            ProductListingDraftView draft
    ) {
        Long draftId = draft.getDraftId();
        Long ownerUserId = draft.getOwnerUserId();
        ProductListingDraftRecord draftRecord = mapper.selectDraftById(draftId, ownerUserId);
        if (draftRecord == null) {
            throw new IllegalArgumentException("Product listing draft not found.");
        }

        ProductListingTaskRecord blockingRealRun =
                mapper.selectCurrentRealRunTaskByDraftId(ownerUserId, draftId);
        ProductListingTaskRecord dryRunRecord =
                sourceDryRun(ownerUserId, draftId, blockingRealRun);
        ProductListingTaskRecord realRunRecord = blockingRealRun;
        if (blockingRealRun == null) {
            dryRunRecord = mapper.selectLatestDryRunTaskByDraftId(ownerUserId, draftId);
            realRunRecord = dryRunRecord == null
                    ? null
                    : mapper.selectRealWriteAttemptTaskBySourceTaskId(
                            ownerUserId, dryRunRecord.getId());
        }

        ProductListingTaskView dryRun = loadTask(context, dryRunRecord);
        boolean persistedSuperseded = dryRun != null
                && "superseded".equalsIgnoreCase(dryRun.getStatus());
        boolean draftChanged = dryRunRecord != null
                && !freshness.matches(
                        draftRecord.getDraftJson(), dryRunRecord.getInputSnapshotJson());
        if (dryRun != null
                && "validated".equalsIgnoreCase(dryRun.getStatus())
                && draftChanged) {
            dryRun.setStatus("superseded");
            dryRun.setFailureCategory("workflow");
            dryRun.setFailureCode("draft_changed_after_validation");
            dryRun.setFailureMessage("草稿已修改，请重新执行上架检查。");
        }
        ProductListingTaskView realRun = loadTask(context, realRunRecord);
        ProductListingWorkflowView projected = projector.project(draft, dryRun, realRun);
        if (terminalAttemptIsHistory(projected, realRun, persistedSuperseded)) {
            ProductListingWorkflowView editable = projector.project(draft, dryRun, null);
            editable.setRealRunTask(realRun);
            return editable;
        }
        return overlayReauthenticationAttempt(projected, realRun);
    }

    private ProductListingWorkflowView overlayReauthenticationAttempt(
            ProductListingWorkflowView workflow,
            ProductListingTaskView realRun
    ) {
        if (reauthenticationAttemptMapper == null
                || reauthenticationAttemptProjector == null
                || realRun == null
                || realRun.getTaskId() == null
                || realRun.getOwnerUserId() == null
                || workflow.getNextAction()
                != ProductListingWorkflowView.NextAction.REAUTHENTICATE) {
            return workflow;
        }
        return reauthenticationAttemptProjector.overlay(
                workflow,
                reauthenticationAttemptMapper.selectAttemptState(
                        realRun.getTaskId(),
                        realRun.getOwnerUserId()
                )
        );
    }

    public List<ProductListingDraftView> attachWorkflowSummaries(
            BusinessAccessContext context,
            List<ProductListingDraftView> drafts
    ) {
        if (drafts == null || drafts.isEmpty()) {
            return drafts;
        }
        for (ProductListingDraftView draft : drafts) {
            if (draft != null && draft.getDraftId() != null) {
                draft.setWorkflow(ProductListingWorkflowSummaryView.from(
                        loadWorkflow(context, draft)
                ));
            }
        }
        return drafts;
    }

    @Transactional
    public ProductListingWorkflowView reopenReview(
            BusinessAccessContext context,
            Long dryRunTaskId
    ) {
        ProductListingTaskView authorized = listingService.loadTask(context, dryRunTaskId);
        ProductListingTaskRecord dryRun = mapper.selectTaskByIdForUpdate(
                dryRunTaskId, authorized.getOwnerUserId());
        if (dryRun == null || !"DRY_RUN".equalsIgnoreCase(dryRun.getMode())) {
            throw new IllegalArgumentException("Only a validated dry-run can return to draft editing.");
        }
        boolean validated = "validated".equalsIgnoreCase(dryRun.getStatus());
        boolean validationFailed =
                "validation_failed".equalsIgnoreCase(dryRun.getStatus());
        boolean superseded = "superseded".equalsIgnoreCase(dryRun.getStatus());
        if (!validated && !validationFailed && !superseded) {
            throw new IllegalArgumentException("Only a validated dry-run can return to draft editing.");
        }
        ProductListingTaskRecord attemptRecord =
                mapper.selectRealWriteAttemptTaskBySourceTaskId(
                        dryRun.getOwnerUserId(), dryRun.getId());
        ProductListingTaskView attempt = loadTask(context, attemptRecord);
        ProductListingWorkflowView attemptWorkflow =
                projector.project(null, null, attempt);
        boolean recoverableAttempt =
                isTerminalNotStarted(attemptWorkflow, attempt);
        if (superseded) {
            if (recoverableAttempt) {
                return loadWorkflow(context, dryRun.getDraftId());
            }
            throw new IllegalArgumentException(
                    "This dry-run cannot return to editing from its current write state."
            );
        }
        if (validationFailed && !recoverableAttempt) {
            throw new IllegalArgumentException(
                    "A validation-failed dry-run can reopen only after a safe terminal attempt."
            );
        }
        if (attempt != null && !recoverableAttempt) {
            throw new IllegalArgumentException(
                    "This dry-run already has a write attempt and cannot return to editing."
            );
        }
        if (mapper.markValidatedDryRunSuperseded(
                dryRun.getId(), dryRun.getOwnerUserId()) != 1) {
            throw new IllegalArgumentException(
                    "The dry-run changed or already has a real-run attempt; reload the workflow."
            );
        }
        return loadWorkflow(context, dryRun.getDraftId());
    }

    private boolean terminalAttemptIsHistory(
            ProductListingWorkflowView projected,
            ProductListingTaskView attempt,
            boolean persistedSuperseded
    ) {
        return persistedSuperseded && isTerminalNotStarted(projected, attempt);
    }

    private boolean isTerminalNotStarted(
            ProductListingWorkflowView projected,
            ProductListingTaskView attempt
    ) {
        return attempt != null
                && ("failed".equalsIgnoreCase(attempt.getStatus())
                || "rejected".equalsIgnoreCase(attempt.getStatus()))
                && projected.getWriteCertainty()
                == ProductListingWorkflowView.WriteCertainty.NOT_STARTED;
    }

    private ProductListingTaskRecord sourceDryRun(
            Long ownerUserId,
            Long draftId,
            ProductListingTaskRecord realRun
    ) {
        if (realRun == null || realRun.getSourceTaskId() == null) {
            return null;
        }
        ProductListingTaskRecord source = mapper.selectTaskById(realRun.getSourceTaskId(), ownerUserId);
        if (source == null
                || !draftId.equals(source.getDraftId())
                || !"DRY_RUN".equalsIgnoreCase(source.getMode())) {
            return null;
        }
        return source;
    }

    private ProductListingTaskView loadTask(
            BusinessAccessContext context,
            ProductListingTaskRecord record
    ) {
        return record == null ? null : listingService.loadTask(context, record.getId());
    }
}
