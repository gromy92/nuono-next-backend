package com.nuono.next.productlisting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Advances the original listing task after the shared Project authorization worker recovers it. */
@Service
public class ProductListingAuthContinuationService {
    private final ProductListingMapper listingMapper;
    private final ProductListingWorkflowService workflowService;
    private final ProductListingSharedRecoveryEvidence sharedRecoveryEvidence;

    public ProductListingAuthContinuationService(
            ProductListingMapper listingMapper,
            ProductListingWorkflowService workflowService,
            ObjectMapper objectMapper
    ) {
        this.listingMapper = listingMapper;
        this.workflowService = workflowService;
        this.sharedRecoveryEvidence = new ProductListingSharedRecoveryEvidence(objectMapper);
    }

    @Transactional
    public ProductListingWorkflowView advance(
            BusinessAccessContext context,
            Long realRunTaskId,
            Long ownerUserId,
            String expectedNoonResultJson,
            Long expectedRecoveryId,
            ResumeAction resumeAction
    ) {
        ProductListingTaskRecord realRun = listingMapper.selectTaskByIdForUpdate(
                realRunTaskId,
                ownerUserId
        );
        sharedRecoveryEvidence.requireExact(
                realRun,
                ownerUserId,
                expectedNoonResultJson,
                expectedRecoveryId
        );
        ProductListingWorkflowView current = workflowService.loadWorkflow(
                context,
                realRun.getDraftId()
        );
        if (!isContinuationTarget(current, realRunTaskId, resumeAction)) {
            throw conflict("上架任务状态已变化，请刷新后再处理。");
        }
        advanceTask(realRun, resumeAction);
        return workflowService.loadWorkflow(context, realRun.getDraftId());
    }

    private boolean isContinuationTarget(
            ProductListingWorkflowView workflow,
            Long realRunTaskId,
            ResumeAction resumeAction
    ) {
        return workflow != null
                && workflow.getNextAction() == ProductListingWorkflowView.NextAction.WAIT_FOR_AUTHORIZATION
                && expectedCertainty(resumeAction) == workflow.getWriteCertainty()
                && workflow.getRealRunTask() != null
                && realRunTaskId.equals(workflow.getRealRunTask().getTaskId());
    }

    private ProductListingWorkflowView.WriteCertainty expectedCertainty(ResumeAction resumeAction) {
        if (resumeAction == ResumeAction.RETRY_CREATE) {
            return ProductListingWorkflowView.WriteCertainty.NOT_STARTED;
        }
        if (resumeAction == ResumeAction.CHECK_CREATE_RESULT) {
            return ProductListingWorkflowView.WriteCertainty.UNKNOWN;
        }
        return ProductListingWorkflowView.WriteCertainty.WRITTEN;
    }

    private void advanceTask(ProductListingTaskRecord realRun, ResumeAction resumeAction) {
        if (resumeAction == ResumeAction.RETRY_CREATE) {
            realRun.setStatus("submitted");
            realRun.setFailureCategory(null);
            realRun.setFailureCode(null);
            realRun.setFailureMessage(null);
            realRun.setCompletedAt(null);
        } else if (resumeAction == ResumeAction.CHECK_CREATE_RESULT) {
            realRun.setFailureCategory("noon_uncertain_write");
            realRun.setFailureCode("noon_create_outcome_unknown");
            realRun.setFailureMessage(
                    "Noon 授权已恢复，系统将自动执行只读创建结果核对；禁止重复创建。"
            );
        } else if (resumeAction == ResumeAction.CONTINUE_AFTER_CREATE) {
            realRun.setFailureCategory("noon_api");
            realRun.setFailureCode("noon_write_continuation_failed");
            realRun.setFailureMessage("Noon 授权已恢复，请继续完成商品创建后的剩余步骤。");
        } else if (resumeAction == ResumeAction.VERIFY_READBACK) {
            realRun.setFailureCategory("noon_readback");
            realRun.setFailureCode("noon_listing_readback_failed");
            realRun.setFailureMessage("Noon 授权已恢复，请重新执行只读回读核对。");
        } else {
            throw conflict("上架任务恢复动作不匹配，请刷新后重试。");
        }
        if (listingMapper.updateTaskResult(realRun) != 1) {
            throw conflict("上架任务状态已变化，请刷新后重试。");
        }
    }

    private ProductListingAuthContinuationException conflict(String message) {
        return new ProductListingAuthContinuationException(message);
    }

    public enum ResumeAction {
        RETRY_CREATE,
        CHECK_CREATE_RESULT,
        CONTINUE_AFTER_CREATE,
        VERIFY_READBACK
    }
}
