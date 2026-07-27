package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


abstract class ProductListingReauthenticationCommitterTestSupport {


    protected ProductListingMapper listingMapper;
    protected StoreSyncMapper storeSyncMapper;
    protected ProductListingWorkflowService workflowService;
    protected ProductListingReauthenticationCommitter committer;
    protected BusinessAccessContext context;
    protected ProductListingReauthenticationCommitter.ReauthenticationCommit command;

    @BeforeEach
    void setUp() {
        listingMapper = mock(ProductListingMapper.class);
        storeSyncMapper = mock(StoreSyncMapper.class);
        workflowService = mock(ProductListingWorkflowService.class);
        committer = new ProductListingReauthenticationCommitter(
                listingMapper,
                storeSyncMapper,
                workflowService
        );
        context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        command = new ProductListingReauthenticationCommitter.ReauthenticationCommit(
                20002L,
                20001L,
                10001L,
                10002L,
                "STR245027-NAE",
                7000L,
                "PRJ240053",
                "verified-user-code",
                "sid=refreshed",
                ProductListingReauthenticationCommitter.ResumeAction
                        .REOPEN_REVIEW
        );
    }

    protected ProductListingTaskRecord task(
            Long id,
            String mode,
            String status,
            Long sourceTaskId
    ) {
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(id);
        task.setDraftId(10001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode(mode);
        task.setStatus(status);
        task.setSourceTaskId(sourceTaskId);
        if ("REAL_RUN".equals(mode)) {
            task.setFailureCode("noon_auth_required");
        }
        return task;
    }

    protected ProductListingWorkflowView workflow(
            ProductListingWorkflowView.NextAction nextAction,
            Long realRunTaskId
    ) {
        return workflow(
                nextAction,
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                realRunTaskId
        );
    }

    protected ProductListingWorkflowView workflow(
            ProductListingWorkflowView.NextAction nextAction,
            ProductListingWorkflowView.WriteCertainty certainty,
            Long realRunTaskId
    ) {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(realRunTaskId);
        ProductListingWorkflowView workflow = new ProductListingWorkflowView();
        workflow.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        workflow.setWriteCertainty(certainty);
        workflow.setNextAction(nextAction);
        workflow.setRealRunTask(task);
        return workflow;
    }

    protected ProductListingReauthenticationCommitter.ReauthenticationCommit
            command(
                    ProductListingReauthenticationCommitter.ResumeAction
                            resumeAction
            ) {
        return new ProductListingReauthenticationCommitter
                .ReauthenticationCommit(
                20002L,
                20001L,
                10001L,
                10002L,
                "STR245027-NAE",
                7000L,
                "PRJ240053",
                "verified-user-code",
                "sid=refreshed",
                resumeAction
        );
    }
}
