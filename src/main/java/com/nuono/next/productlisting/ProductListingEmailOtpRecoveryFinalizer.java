package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.ProductListingReauthenticationAttemptMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductListingEmailOtpRecoveryFinalizer {
    private final ProductListingReauthenticationAttemptMapper attemptMapper;
    private final ProductListingMapper listingMapper;
    private final ProductListingWorkflowService workflowService;
    private final ProductListingReauthenticationAttemptProjector projector;

    public ProductListingEmailOtpRecoveryFinalizer(
            ProductListingReauthenticationAttemptMapper attemptMapper,
            ProductListingMapper listingMapper,
            ProductListingWorkflowService workflowService,
            ProductListingReauthenticationAttemptProjector projector
    ) {
        this.attemptMapper = attemptMapper;
        this.listingMapper = listingMapper;
        this.workflowService = workflowService;
        this.projector = projector;
    }

    @Transactional
    public ProductListingWorkflowView poll(
            BusinessAccessContext context,
            ProductListingTaskView task,
            ProductListingWorkflowView workflow
    ) {
        ProductListingReauthenticationAttemptRecord attempt =
                attemptMapper.selectAttemptState(
                        task.getTaskId(),
                        task.getOwnerUserId()
                );
        requireExactAttempt(attempt, task);
        if ("COMPLETED".equals(attempt.getStatus())) {
            return workflow;
        }
        if ("FAILED".equals(attempt.getStatus())) {
            return projector.failed(workflow, attempt.getFailureCode());
        }
        String failureCode = projector.terminalFailureCode(attempt);
        if (failureCode != null) {
            attemptMapper.markAttemptFailed(
                    task.getTaskId(),
                    task.getOwnerUserId(),
                    attempt.getRecoveryId(),
                    attempt.getRecoveryItemId(),
                    attempt.getVersionNo(),
                    failureCode
            );
            return projector.failed(workflow, failureCode);
        }
        if (!projector.isRecovered(attempt)) {
            return projector.pending(workflow, attempt.getRecoveryStatus());
        }
        if (attemptMapper.claimRecoveredAttempt(
                task.getTaskId(),
                task.getOwnerUserId(),
                attempt.getRecoveryId(),
                attempt.getRecoveryItemId(),
                attempt.getVersionNo()
        ) != 1) {
            return projector.overlay(
                    workflow,
                    attemptMapper.selectAttemptState(
                            task.getTaskId(),
                            task.getOwnerUserId()
                    )
            );
        }
        ProductListingReauthenticationCommitter.ResumeAction resumeAction =
                parseResumeAction(attempt.getResumeAction());
        ProductListingTaskRecord realRun = listingMapper.selectTaskByIdForUpdate(
                task.getTaskId(),
                task.getOwnerUserId()
        );
        requireSameAttempt(realRun, attempt, task);
        if (resumeAction
                == ProductListingReauthenticationCommitter.ResumeAction
                .REOPEN_REVIEW) {
            reopenReview(realRun, attempt);
        } else {
            advanceRecoveryTask(realRun, resumeAction);
        }
        if (attemptMapper.completeClaimedAttempt(
                task.getTaskId(),
                task.getOwnerUserId(),
                attempt.getRecoveryId(),
                attempt.getRecoveryItemId(),
                attempt.getVersionNo() + 1L
        ) != 1) {
            throw conflict(
                    "授权恢复证据在收口时发生变化，Listing 状态未推进。"
            );
        }
        return workflowService.loadWorkflow(context, task.getDraftId());
    }

    private void reopenReview(
            ProductListingTaskRecord realRun,
            ProductListingReauthenticationAttemptRecord attempt
    ) {
        ProductListingTaskRecord source = listingMapper.selectTaskByIdForUpdate(
                realRun.getSourceTaskId(),
                realRun.getOwnerUserId()
        );
        if (source == null
                || !"DRY_RUN".equalsIgnoreCase(source.getMode())
                || !"validated".equalsIgnoreCase(source.getStatus())
                || !attempt.getDraftId().equals(source.getDraftId())
                || !sameText(attempt.getStoreCode(), source.getStoreCode())
                || listingMapper.markValidatedDryRunSuperseded(
                        source.getId(),
                        source.getOwnerUserId()
                ) != 1) {
            throw conflict("原上架检查已变化，不能返回编辑。");
        }
    }

    private void advanceRecoveryTask(
            ProductListingTaskRecord realRun,
            ProductListingReauthenticationCommitter.ResumeAction resumeAction
    ) {
        if (resumeAction
                == ProductListingReauthenticationCommitter.ResumeAction
                .RETRY_CREATE) {
            realRun.setStatus("submitted");
            realRun.setFailureCategory(null);
            realRun.setFailureCode(null);
            realRun.setFailureMessage(null);
            realRun.setCompletedAt(null);
        } else if (resumeAction
                == ProductListingReauthenticationCommitter.ResumeAction
                .CHECK_CREATE_RESULT) {
            realRun.setFailureCategory("noon_uncertain_write");
            realRun.setFailureCode("noon_create_outcome_unknown");
            realRun.setFailureMessage(
                    "Noon 授权已恢复，系统将自动只读核对创建结果；禁止重复创建。"
            );
        } else if (resumeAction
                == ProductListingReauthenticationCommitter.ResumeAction
                .CONTINUE_AFTER_CREATE) {
            realRun.setFailureCategory("noon_api");
            realRun.setFailureCode("noon_write_continuation_failed");
            realRun.setFailureMessage(
                    "Noon 授权已恢复，请继续商品创建后的剩余步骤。"
            );
        } else if (resumeAction
                == ProductListingReauthenticationCommitter.ResumeAction
                .VERIFY_READBACK) {
            realRun.setFailureCategory("noon_readback");
            realRun.setFailureCode("noon_listing_readback_failed");
            realRun.setFailureMessage(
                    "Noon 授权已恢复，请重新执行只读回读核对。"
            );
        } else {
            throw conflict("上架任务恢复动作不匹配。");
        }
        if (listingMapper.updateTaskResult(realRun) != 1) {
            throw conflict("上架任务状态已变化，请刷新后重试。");
        }
    }

    private void requireExactAttempt(
            ProductListingReauthenticationAttemptRecord attempt,
            ProductListingTaskView task
    ) {
        if (attempt == null
                || !same(attempt.getDraftId(), task.getDraftId())
                || !sameText(attempt.getStoreCode(), task.getStoreCode())
                || attempt.getRecoveryId() == null
                || attempt.getRecoveryItemId() == null
                || attempt.getRequestedAuthVersion() == null
                || attempt.getVersionNo() == null) {
            throw conflict(
                    "当前上架任务没有可轮询的精确授权批次，请重新发起授权。"
            );
        }
    }

    private void requireSameAttempt(
            ProductListingTaskRecord realRun,
            ProductListingReauthenticationAttemptRecord attempt,
            ProductListingTaskView task
    ) {
        if (realRun == null
                || !"REAL_RUN".equalsIgnoreCase(realRun.getMode())
                || !attempt.getDraftId().equals(realRun.getDraftId())
                || !task.getSourceTaskId().equals(realRun.getSourceTaskId())
                || !sameText(attempt.getStoreCode(), realRun.getStoreCode())
                || !"noon_auth_required".equalsIgnoreCase(
                        realRun.getFailureCode()
                )) {
            throw conflict("上架任务状态已变化，请刷新后再处理。");
        }
    }

    private ProductListingReauthenticationCommitter.ResumeAction
            parseResumeAction(String value) {
        try {
            return ProductListingReauthenticationCommitter.ResumeAction.valueOf(
                    value
            );
        } catch (RuntimeException exception) {
            throw conflict("上架授权恢复动作无效，请重新发起授权。");
        }
    }

    private boolean same(Object left, Object right) {
        return left != null && left.equals(right);
    }

    private boolean sameText(String left, String right) {
        return left != null
                && right != null
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private ProductListingReauthenticationException conflict(String message) {
        return new ProductListingReauthenticationException(message);
    }
}
