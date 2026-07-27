package com.nuono.next.productlisting;

import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProductListingReauthenticationService {

    private final ProductListingService listingService;
    private final ProductListingWorkflowService workflowService;
    private final StoreSyncMapper storeSyncMapper;
    private final ProductListingEmailOtpReauthenticationService
            emailOtpReauthenticationService;
    private final ProductListingPasswordReauthenticationService
            passwordReauthenticationService;

    public ProductListingReauthenticationService(
            ProductListingService listingService,
            ProductListingWorkflowService workflowService,
            StoreSyncMapper storeSyncMapper,
            ProductListingEmailOtpReauthenticationService
                    emailOtpReauthenticationService,
            ProductListingPasswordReauthenticationService
                    passwordReauthenticationService
    ) {
        this.listingService = listingService;
        this.workflowService = workflowService;
        this.storeSyncMapper = storeSyncMapper;
        this.emailOtpReauthenticationService =
                emailOtpReauthenticationService;
        this.passwordReauthenticationService =
                passwordReauthenticationService;
    }

    public ProductListingWorkflowView reauthenticate(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskView task = listingService.loadTask(context, realRunTaskId);
        ProductListingWorkflowView workflow =
                requireCurrentReauthenticationTarget(context, task);
        ProductListingReauthenticationCommitter.ResumeAction resumeAction =
                resumeAction(workflow, task);

        StoreSyncStoreRecord project = storeSyncMapper.selectOwnerProject(
                task.getOwnerUserId(),
                task.getStoreCode()
        );
        if (project == null || project.getId() == null
                || !StringUtils.hasText(project.getProjectCode())) {
            throw conflict("当前上架任务找不到对应的 Noon Project 授权。");
        }
        StoreSyncStoreRecord site = resolveSite(task, project);
        if (emailOtpReauthenticationService.applies(project)) {
            return emailOtpReauthenticationService.enqueue(
                    context,
                    task,
                    workflow,
                    project,
                    site,
                    resumeAction
            );
        }
        return passwordReauthenticationService.reauthenticate(
                context,
                task,
                project,
                site,
                resumeAction
        );
    }

    public ProductListingWorkflowView reauthenticationStatus(
            BusinessAccessContext context,
            Long realRunTaskId
    ) {
        ProductListingTaskView task = listingService.loadTask(
                context,
                realRunTaskId
        );
        ProductListingWorkflowView workflow =
                requirePollingTarget(context, task);
        StoreSyncStoreRecord project = storeSyncMapper.selectOwnerProject(
                task.getOwnerUserId(),
                task.getStoreCode()
        );
        if (project == null || project.getId() == null
                || !StringUtils.hasText(project.getProjectCode())
                || !emailOtpReauthenticationService.applies(project)) {
            throw conflict(
                    "当前任务没有可轮询的 Noon Email-OTP 授权恢复。"
            );
        }
        return emailOtpReauthenticationService.poll(
                context,
                task,
                workflow
        );
    }

    private ProductListingWorkflowView requireCurrentReauthenticationTarget(
            BusinessAccessContext context,
            ProductListingTaskView task
    ) {
        if (task == null
                || !"REAL_RUN".equalsIgnoreCase(task.getMode())
                || task.getSourceTaskId() == null) {
            throw conflict("只有需要重新授权的真实上架任务可以执行此操作。");
        }
        ProductListingWorkflowView workflow = workflowService.loadWorkflow(
                context,
                task.getDraftId()
        );
        boolean expectedAction = workflow.getNextAction()
                == ProductListingWorkflowView.NextAction.REAUTHENTICATE
                || workflow.getNextAction()
                == ProductListingWorkflowView.NextAction
                .WAIT_FOR_REAUTHENTICATION;
        if (!expectedAction || !isRecoverableCertainty(workflow.getWriteCertainty())
                || workflow.getRealRunTask() == null
                || !task.getTaskId().equals(workflow.getRealRunTask().getTaskId())) {
            throw conflict("该上架任务当前不需要重新授权，请刷新流程状态。");
        }
        return workflow;
    }

    private ProductListingWorkflowView requirePollingTarget(
            BusinessAccessContext context,
            ProductListingTaskView task
    ) {
        if (task == null
                || !"REAL_RUN".equalsIgnoreCase(task.getMode())
                || task.getSourceTaskId() == null) {
            throw conflict("只有真实上架任务可以查询授权恢复状态。");
        }
        ProductListingWorkflowView workflow = workflowService.loadWorkflow(
                context,
                task.getDraftId()
        );
        boolean expectedAction = workflow.getNextAction()
                == ProductListingWorkflowView.NextAction
                .WAIT_FOR_REAUTHENTICATION
                || workflow.getNextAction()
                == ProductListingWorkflowView.NextAction.REAUTHENTICATE;
        if (!expectedAction
                || !isRecoverableCertainty(workflow.getWriteCertainty())
                || workflow.getRealRunTask() == null
                || !task.getTaskId().equals(
                        workflow.getRealRunTask().getTaskId()
                )) {
            throw conflict("该上架任务当前没有待收口的授权恢复。");
        }
        return workflow;
    }

    private boolean isRecoverableCertainty(
            ProductListingWorkflowView.WriteCertainty certainty
    ) {
        return certainty == ProductListingWorkflowView.WriteCertainty.NOT_STARTED
                || certainty == ProductListingWorkflowView.WriteCertainty.UNKNOWN
                || certainty == ProductListingWorkflowView.WriteCertainty.WRITTEN;
    }

    private ProductListingReauthenticationCommitter.ResumeAction resumeAction(
            ProductListingWorkflowView workflow,
            ProductListingTaskView task
    ) {
        if (workflow.getWriteCertainty()
                == ProductListingWorkflowView.WriteCertainty.NOT_STARTED) {
            return ProductListingReauthenticationCommitter.ResumeAction
                    .RETRY_CREATE;
        }
        if (workflow.getWriteCertainty()
                == ProductListingWorkflowView.WriteCertainty.UNKNOWN) {
            return ProductListingReauthenticationCommitter.ResumeAction
                    .CHECK_CREATE_RESULT;
        }
        if (ProductListingWorkflowEvidence.hasFailedWriteStep(
                task.getNoonResult()
        )) {
            return ProductListingReauthenticationCommitter.ResumeAction
                    .CONTINUE_AFTER_CREATE;
        }
        return ProductListingReauthenticationCommitter.ResumeAction
                .VERIFY_READBACK;
    }

    private StoreSyncStoreRecord resolveSite(
            ProductListingTaskView task,
            StoreSyncStoreRecord project
    ) {
        StoreSyncStoreRecord site = firstNonNull(
                storeSyncMapper.selectOwnerStore(task.getOwnerUserId(), task.getStoreCode()),
                storeSyncMapper.selectOwnerProjectionStore(task.getOwnerUserId(), task.getStoreCode())
        );
        if (site == null) {
            List<StoreSyncStoreRecord> sites = storeSyncMapper.listOwnerProjectSites(
                    task.getOwnerUserId(),
                    List.of(project.getProjectCode())
            );
            site = sites.isEmpty() ? null : sites.get(0);
        }
        if (site == null || !StringUtils.hasText(site.getStoreCode())) {
            throw conflict("当前 Noon Project 没有可验证的站点店铺。");
        }
        return site;
    }

    private StoreSyncStoreRecord firstNonNull(StoreSyncStoreRecord... values) {
        for (StoreSyncStoreRecord value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ProductListingReauthenticationException conflict(String message) {
        return new ProductListingReauthenticationException(message);
    }
}
