package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noonauth.NoonProjectAuthStateSynchronizer;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListingReauthenticationCommitter {

    private final ProductListingMapper listingMapper;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductListingWorkflowService workflowService;
    private final NoonProjectAuthStateSynchronizer projectAuthStateSynchronizer;

    @Autowired
    public ProductListingReauthenticationCommitter(
            ProductListingMapper listingMapper,
            StoreSyncMapper storeSyncMapper,
            ProductListingWorkflowService workflowService,
            ObjectProvider<NoonProjectAuthStateSynchronizer> synchronizerProvider
    ) {
        this.listingMapper = listingMapper;
        this.storeSyncMapper = storeSyncMapper;
        this.workflowService = workflowService;
        this.projectAuthStateSynchronizer = synchronizerProvider == null
                ? NoonProjectAuthStateSynchronizer.noop()
                : synchronizerProvider.getIfAvailable(
                        NoonProjectAuthStateSynchronizer::noop
                );
    }

    public ProductListingReauthenticationCommitter(
            ProductListingMapper listingMapper,
            StoreSyncMapper storeSyncMapper,
            ProductListingWorkflowService workflowService
    ) {
        this(listingMapper, storeSyncMapper, workflowService, null);
    }

    @Transactional
    public ProductListingWorkflowView commit(
            BusinessAccessContext context,
            ReauthenticationCommit command
    ) {
        ProductListingTaskRecord realRun = listingMapper.selectTaskByIdForUpdate(
                command.realRunTaskId,
                command.ownerUserId
        );
        requireSameAttempt(realRun, command);
        ProductListingWorkflowView current = workflowService.loadWorkflow(
                context,
                command.draftId
        );
        if (!isReauthenticationTarget(
                current,
                command.realRunTaskId,
                command.resumeAction
        )) {
            throw conflict("上架任务状态已变化，请刷新后再处理。");
        }

        ProductListingTaskRecord source = null;
        if (command.resumeAction == ResumeAction.REOPEN_REVIEW) {
            source = listingMapper.selectTaskByIdForUpdate(
                    command.sourceTaskId,
                    command.ownerUserId
            );
            if (source == null
                    || !"DRY_RUN".equalsIgnoreCase(source.getMode())
                    || !"validated".equalsIgnoreCase(source.getStatus())
                    || !command.draftId.equals(source.getDraftId())
                    || !sameText(command.storeCode, source.getStoreCode())) {
                throw conflict("原上架检查已变化，不能自动返回编辑。");
            }
        }
        if (storeSyncMapper.updateProjectReauthenticationSuccess(
                command.projectId,
                command.ownerUserId,
                command.noonUserCode,
                command.cookie,
                command.ownerUserId
        ) != 1) {
            throw conflict("Noon 会话已验证，但店铺授权状态已变化，请刷新后重试。");
        }
        projectAuthStateSynchronizer.recordVerifiedProjectSession(
                command.ownerUserId,
                command.projectCode
        );
        if (command.resumeAction == ResumeAction.REOPEN_REVIEW) {
            if (listingMapper.markValidatedDryRunSuperseded(
                    source.getId(),
                    command.ownerUserId
            ) != 1) {
                throw conflict("原上架检查已变化，不能自动返回编辑。");
            }
        } else {
            advanceRecoveryTask(realRun, command.resumeAction);
        }
        return workflowService.loadWorkflow(context, command.draftId);
    }

    private void requireSameAttempt(
            ProductListingTaskRecord realRun,
            ReauthenticationCommit command
    ) {
        if (realRun == null
                || !"REAL_RUN".equalsIgnoreCase(realRun.getMode())
                || !command.draftId.equals(realRun.getDraftId())
                || !command.sourceTaskId.equals(realRun.getSourceTaskId())
                || !sameText(command.storeCode, realRun.getStoreCode())
                || !"noon_auth_required".equalsIgnoreCase(
                        realRun.getFailureCode()
                )) {
            throw conflict("上架任务状态已变化，请刷新后再处理。");
        }
    }

    private boolean isReauthenticationTarget(
            ProductListingWorkflowView workflow,
            Long realRunTaskId,
            ResumeAction resumeAction
    ) {
        return workflow != null
                && workflow.getNextAction()
                == ProductListingWorkflowView.NextAction.REAUTHENTICATE
                && expectedCertainty(resumeAction) == workflow.getWriteCertainty()
                && workflow.getRealRunTask() != null
                && realRunTaskId.equals(workflow.getRealRunTask().getTaskId());
    }

    private ProductListingWorkflowView.WriteCertainty expectedCertainty(
            ResumeAction resumeAction
    ) {
        if (resumeAction == ResumeAction.REOPEN_REVIEW
                || resumeAction == ResumeAction.RETRY_CREATE) {
            return ProductListingWorkflowView.WriteCertainty.NOT_STARTED;
        }
        if (resumeAction == ResumeAction.CHECK_CREATE_RESULT) {
            return ProductListingWorkflowView.WriteCertainty.UNKNOWN;
        }
        return ProductListingWorkflowView.WriteCertainty.WRITTEN;
    }

    private void advanceRecoveryTask(
            ProductListingTaskRecord realRun,
            ResumeAction resumeAction
    ) {
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
            realRun.setFailureMessage(
                    "Noon 授权已恢复，请继续完成商品创建后的剩余步骤。"
            );
        } else if (resumeAction == ResumeAction.VERIFY_READBACK) {
            realRun.setFailureCategory("noon_readback");
            realRun.setFailureCode("noon_listing_readback_failed");
            realRun.setFailureMessage(
                    "Noon 授权已恢复，请重新执行只读回读核对。"
            );
        } else {
            throw conflict("上架任务恢复动作不匹配，请刷新后重试。");
        }
        if (listingMapper.updateTaskResult(realRun) != 1) {
            throw conflict("上架任务状态已变化，请刷新后重试。");
        }
    }

    private boolean sameText(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    private ProductListingReauthenticationException conflict(String message) {
        return new ProductListingReauthenticationException(message);
    }

    public enum ResumeAction {
        REOPEN_REVIEW,
        RETRY_CREATE,
        CHECK_CREATE_RESULT,
        CONTINUE_AFTER_CREATE,
        VERIFY_READBACK
    }

    public static final class ReauthenticationCommit {
        private final Long realRunTaskId;
        private final Long sourceTaskId;
        private final Long draftId;
        private final Long ownerUserId;
        private final String storeCode;
        private final Long projectId;
        private final String projectCode;
        private final String noonUserCode;
        private final String cookie;
        private final ResumeAction resumeAction;

        public ReauthenticationCommit(
                Long realRunTaskId,
                Long sourceTaskId,
                Long draftId,
                Long ownerUserId,
                String storeCode,
                Long projectId,
                String projectCode,
                String noonUserCode,
                String cookie,
                ResumeAction resumeAction
        ) {
            this.realRunTaskId = realRunTaskId;
            this.sourceTaskId = sourceTaskId;
            this.draftId = draftId;
            this.ownerUserId = ownerUserId;
            this.storeCode = storeCode;
            this.projectId = projectId;
            this.projectCode = projectCode;
            this.noonUserCode = noonUserCode;
            this.cookie = cookie;
            this.resumeAction = resumeAction;
        }

        ResumeAction getResumeAction() {
            return resumeAction;
        }
    }
}
