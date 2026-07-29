package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.store.NoonCatalogConnectionProbe;
import com.nuono.next.store.StoreSyncStoreRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductListingPersistedSessionReauthenticationServiceTest {
    private ProductListingEmailOtpRecoveryEnqueuer enqueuer;
    private NoonCatalogConnectionProbe catalogProbe;
    private ProductListingReauthenticationCommitter committer;
    private ProductListingEmailOtpReauthenticationService service;
    private BusinessAccessContext context;

    @BeforeEach
    void setUp() {
        enqueuer = mock(ProductListingEmailOtpRecoveryEnqueuer.class);
        catalogProbe = mock(NoonCatalogConnectionProbe.class);
        committer = mock(ProductListingReauthenticationCommitter.class);
        service = new ProductListingEmailOtpReauthenticationService(
                enqueuer,
                mock(ProductListingEmailOtpRecoveryFinalizer.class),
                new ProductListingReauthenticationAttemptProjector(),
                new ProductListingPersistedSessionReauthenticationService(
                        catalogProbe,
                        committer
                )
        );
        context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
    }

    @Test
    void validStoredProjectSessionResumesWithoutStartingOtpRecovery() {
        ProductListingTaskView task = task();
        ProductListingWorkflowView publishing = workflow(task);
        publishing.setPhase(ProductListingWorkflowView.Phase.PUBLISHING);
        publishing.setNextAction(ProductListingWorkflowView.NextAction.WAIT);
        when(committer.commit(
                org.mockito.ArgumentMatchers.eq(context),
                any(ProductListingReauthenticationCommitter
                        .ReauthenticationCommit.class)
        )).thenReturn(publishing);

        ProductListingWorkflowView result = service.enqueue(
                context,
                task,
                workflow(task),
                project("sid=still-valid"),
                site(),
                ProductListingReauthenticationCommitter.ResumeAction
                        .RETRY_CREATE
        );

        assertEquals(ProductListingWorkflowView.Phase.PUBLISHING, result.getPhase());
        verify(catalogProbe).verify(
                10002L,
                "verified-project-user",
                "sid=still-valid",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        );
        verify(enqueuer, never()).enqueue(any(), any(), any(), any());
    }

    @Test
    void expiredStoredSessionFallsBackToTheExistingOtpRecovery() {
        ProductListingTaskView task = task();
        ProductListingWorkflowView required = workflow(task);
        when(catalogProbe.verify(
                10002L,
                "verified-project-user",
                "sid=expired",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        )).thenThrow(new NoonHttpException(
                401,
                "unauthorized",
                "/offer/list/noon"
        ));

        ProductListingWorkflowView result = service.enqueue(
                context,
                task,
                required,
                project("sid=expired"),
                site(),
                ProductListingReauthenticationCommitter.ResumeAction
                        .RETRY_CREATE
        );

        assertEquals(
                ProductListingWorkflowView.NextAction
                        .WAIT_FOR_REAUTHENTICATION,
                result.getNextAction()
        );
        verify(enqueuer).enqueue(
                org.mockito.ArgumentMatchers.eq(task),
                org.mockito.ArgumentMatchers.any(StoreSyncStoreRecord.class),
                org.mockito.ArgumentMatchers.any(StoreSyncStoreRecord.class),
                org.mockito.ArgumentMatchers.eq(
                        ProductListingReauthenticationCommitter.ResumeAction
                                .RETRY_CREATE
                )
        );
    }

    @Test
    void nonAuthenticationProbeFailureDoesNotSendOtpOrAdvanceTheTask() {
        when(catalogProbe.verify(
                10002L,
                "verified-project-user",
                "sid=present",
                "PRJ245027",
                "STR245027-NAE",
                "AE",
                "245027"
        )).thenThrow(new NoonHttpException(
                503,
                "temporarily unavailable",
                "/offer/list/noon"
        ));

        ProductListingReauthenticationException error = assertThrows(
                ProductListingReauthenticationException.class,
                () -> service.enqueue(
                        context,
                        task(),
                        workflow(task()),
                        project("sid=present"),
                        site(),
                        ProductListingReauthenticationCommitter.ResumeAction
                                .RETRY_CREATE
                )
        );

        assertEquals(
                "Noon 已保存会话的只读验证失败；未发送新的验证码，原上架任务保持不变。",
                error.getMessage()
        );
        verify(enqueuer, never()).enqueue(any(), any(), any(), any());
        verify(committer, never()).commit(any(), any());
    }

    private ProductListingTaskView task() {
        ProductListingTaskView task = new ProductListingTaskView();
        task.setTaskId(20002L);
        task.setSourceTaskId(20001L);
        task.setDraftId(10001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode("REAL_RUN");
        task.setStatus("failed");
        task.setFailureCode("noon_auth_required");
        return task;
    }

    private ProductListingWorkflowView workflow(
            ProductListingTaskView task
    ) {
        ProductListingWorkflowView workflow =
                new ProductListingWorkflowView();
        workflow.setPhase(ProductListingWorkflowView.Phase.ACTION_REQUIRED);
        workflow.setWriteCertainty(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED
        );
        workflow.setNextAction(
                ProductListingWorkflowView.NextAction.REAUTHENTICATE
        );
        workflow.setRealRunTask(task);
        return workflow;
    }

    private StoreSyncStoreRecord project(String cookie) {
        StoreSyncStoreRecord project = new StoreSyncStoreRecord();
        project.setId(7000L);
        project.setProjectCode("PRJ245027");
        project.setNoonPartnerId("245027");
        project.setNoonPartnerUserCode("verified-project-user");
        project.setNoonPartnerProjectUser("verified-project-user");
        project.setNoonPartnerCookie(cookie);
        return project;
    }

    private StoreSyncStoreRecord site() {
        StoreSyncStoreRecord site = new StoreSyncStoreRecord();
        site.setProjectCode("PRJ245027");
        site.setStoreCode("STR245027-NAE");
        site.setSite("AE");
        return site;
    }
}
