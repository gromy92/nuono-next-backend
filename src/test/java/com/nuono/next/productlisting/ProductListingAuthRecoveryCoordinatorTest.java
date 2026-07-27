package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.ProductListingAuthRecoveryMapper;
import com.nuono.next.infrastructure.mapper.ProductListingMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingAuthRecoveryCoordinatorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preWriteRecoveryAdvancesThenExecutesTheSameTask() throws Exception {
        ProductListingTaskRecord task = authTask(false, false, 991L);
        Fixture fixture = fixture(task);
        when(fixture.adapter.isAuthorizationRecoveryPending(any()))
                .thenReturn(true, false);
        stubAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.RETRY_CREATE,
                ProductListingWorkflowView.NextAction.WAIT);

        assertFalse(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));
        assertTrue(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));

        verifyAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.RETRY_CREATE);
        verify(fixture.service).executeSubmittedRealRunTask(88003L);
        verify(fixture.outcomeService, never()).verify(any(), any());
    }

    @Test
    void readBackOnlyRecoveryAdvancesBeforeReadOnlyVerification() throws Exception {
        ProductListingTaskRecord task = authTask(true, true, 991L);
        Fixture fixture = fixture(task);
        when(fixture.adapter.isAuthorizationRecoveryPending(any())).thenReturn(false);
        stubAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.VERIFY_READBACK,
                ProductListingWorkflowView.NextAction.VERIFY_READBACK);

        assertTrue(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));

        verifyAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.VERIFY_READBACK);
        verify(fixture.service).verifyRealRunReadBack(fixture.context, 88003L);
        verify(fixture.service, never()).continueRealRunAfterCreate(any(), any());
    }

    @Test
    void uncertainCreateRecoveryAdvancesThenUsesReadOnlyOutcomeService() throws Exception {
        ProductListingTaskRecord task = authTask(true, false, 991L);
        Fixture fixture = fixture(task);
        when(fixture.adapter.isAuthorizationRecoveryPending(any())).thenReturn(false);
        stubAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.CHECK_CREATE_RESULT,
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT);

        assertTrue(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));

        verifyAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.CHECK_CREATE_RESULT);
        verify(fixture.outcomeService).verify(fixture.context, 88003L);
        verify(fixture.service, never()).continueRealRunAfterCreate(any(), any());
    }

    @Test
    void failedPostCreateWriteOnlyAdvancesToExplicitContinuation() throws Exception {
        ProductListingTaskRecord task = postCreateAuthTask();
        Fixture fixture = fixture(task);
        when(fixture.adapter.isAuthorizationRecoveryPending(any())).thenReturn(false);
        stubAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.CONTINUE_AFTER_CREATE,
                ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE);

        assertTrue(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));

        verifyAdvance(fixture, task,
                ProductListingReauthenticationCommitter.ResumeAction.CONTINUE_AFTER_CREATE);
        verify(fixture.service, never()).continueRealRunAfterCreate(any(), any());
        verify(fixture.service, never()).verifyRealRunReadBack(any(), any());
        verify(fixture.outcomeService, never()).verify(any(), any());
    }

    @Test
    void recoveryWithoutRecoveryIdNeverAdvancesOrChecksTheProvider() throws Exception {
        ProductListingTaskRecord task = authTask(false, false, null);
        Fixture fixture = fixture(task);

        assertFalse(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));

        verify(fixture.adapter, never()).isAuthorizationRecoveryPending(any());
        verify(fixture.committer, never()).advanceSharedRecovery(
                any(), any(), any(), anyString(), any(),
                any(ProductListingReauthenticationCommitter.ResumeAction.class));
    }

    @Test
    void olderPreWriteTaskIsSupersededBeforeAuthorizationChecks() throws Exception {
        ProductListingTaskRecord task = authTask(false, false, 991L);
        Fixture fixture = fixture(task);
        ProductListingTaskRecord newer = new ProductListingTaskRecord();
        newer.setId(99003L);
        when(fixture.listingMapper.selectRealWriteAttemptTaskBySourceTaskId(
                10002L, 66001L)).thenReturn(newer);
        when(fixture.authMapper.markPreWriteAuthRecoverySuperseded(
                eq(88003L), eq(10002L), eq(task.getNoonResultJson()),
                eq(991L), anyString())).thenReturn(1);

        assertTrue(fixture.coordinator.resumeIfAuthorizationRestored(
                fixture.context, task));

        verify(fixture.adapter, never()).isAuthorizationRecoveryPending(any());
        verify(fixture.committer, never()).advanceSharedRecovery(
                any(), any(), any(), anyString(), any(),
                any(ProductListingReauthenticationCommitter.ResumeAction.class));
    }

    @Test
    void blockedOldestStoresDoNotStarveALaterRestoredStore() throws Exception {
        List<ProductListingTaskRecord> blockedWindow = new java.util.ArrayList<>();
        for (int index = 1; index <= 8; index++) {
            ProductListingTaskRecord blocked = postCreateAuthTask();
            blocked.setId(88000L + index);
            blocked.setStoreCode("STR-BLOCKED-" + index);
            blockedWindow.add(blocked);
        }
        ProductListingTaskRecord restored = postCreateAuthTask();
        restored.setId(88009L);
        restored.setStoreCode("STR-RESTORED");
        Fixture fixture = fixture(restored);
        when(fixture.authMapper.selectPendingAuthRecoveryTasks(0L, 8))
                .thenReturn(blockedWindow);
        when(fixture.authMapper.selectPendingAuthRecoveryTasks(88008L, 8))
                .thenReturn(List.of(restored));
        when(fixture.adapter.isAuthorizationRecoveryPending(any()))
                .thenAnswer(invocation -> {
                    ProductListingNoonWriteRequest request = invocation.getArgument(0);
                    return !"STR-RESTORED".equals(request.getStoreCode());
                });
        when(fixture.committer.advanceSharedRecovery(
                any(BusinessAccessContext.class),
                eq(restored.getId()),
                eq(restored.getOwnerUserId()),
                eq(restored.getNoonResultJson()),
                eq(991L),
                eq(ProductListingReauthenticationCommitter.ResumeAction.CONTINUE_AFTER_CREATE)
        )).thenReturn(workflow(ProductListingWorkflowView.NextAction.CONTINUE_AFTER_CREATE));

        assertEquals(0, fixture.coordinator.resumePendingTasks(2));
        assertEquals(1, fixture.coordinator.resumePendingTasks(2));

        verify(fixture.service, never()).executeSubmittedRealRunTask(any());
        verify(fixture.service, never()).continueRealRunAfterCreate(any(), any());
        verify(fixture.service, never()).verifyRealRunReadBack(any(), any());
        verify(fixture.outcomeService, never()).verify(any(), any());
    }

    private Fixture fixture(ProductListingTaskRecord task) {
        return new Fixture(task);
    }

    private void stubAdvance(
            Fixture fixture,
            ProductListingTaskRecord task,
            ProductListingReauthenticationCommitter.ResumeAction action,
            ProductListingWorkflowView.NextAction nextAction
    ) {
        when(fixture.committer.advanceSharedRecovery(
                fixture.context, task.getId(), task.getOwnerUserId(),
                task.getNoonResultJson(), 991L, action
        )).thenReturn(workflow(nextAction));
    }

    private void verifyAdvance(
            Fixture fixture,
            ProductListingTaskRecord task,
            ProductListingReauthenticationCommitter.ResumeAction action
    ) {
        verify(fixture.committer).advanceSharedRecovery(
                fixture.context, task.getId(), task.getOwnerUserId(),
                task.getNoonResultJson(), 991L, action);
    }

    private ProductListingWorkflowView workflow(
            ProductListingWorkflowView.NextAction nextAction
    ) {
        ProductListingWorkflowView workflow = new ProductListingWorkflowView();
        workflow.setNextAction(nextAction);
        return workflow;
    }

    private ProductListingTaskRecord authTask(
            boolean writeMayHaveOccurred,
            boolean durableCreateReference,
            Long recoveryId
    ) throws Exception {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey(durableCreateReference ? "create_product" : "authorization_recovery");
        create.setStatus(durableCreateReference ? "succeeded" : "failed");
        create.setFailureCode(durableCreateReference
                ? null : ProductListingWriteAuthRecovery.FAILURE_CODE);
        create.setRecoveryId(recoveryId);
        create.setWriteMayHaveOccurred(writeMayHaveOccurred);
        if (durableCreateReference) {
            create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        } else if (writeMayHaveOccurred) {
            create.setStepKey("create_product");
            create.setFailureCode("noon_create_outcome_unknown");
        }
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "authorization", ProductListingWriteAuthRecovery.FAILURE_CODE,
                "Noon Project 授权恢复中",
                writeMayHaveOccurred && !durableCreateReference
                        ? List.of(preCreateAbsence(), create) : List.of(create));
        result.setRecoveryId(recoveryId);
        result.setWriteMayHaveOccurred(writeMayHaveOccurred);
        ProductListingTaskRecord task = new ProductListingTaskRecord();
        task.setId(88003L);
        task.setDraftId(77001L);
        task.setOwnerUserId(10002L);
        task.setStoreCode("STR245027-NAE");
        task.setMode("REAL_RUN");
        task.setStatus(writeMayHaveOccurred ? "written_verify_failed" : "failed");
        task.setSourceTaskId(66001L);
        task.setSubmittedBy(90001L);
        task.setFailureCode(ProductListingWriteAuthRecovery.FAILURE_CODE);
        task.setInputSnapshotJson(objectMapper.writeValueAsString(
                ProductListingTestFixtures.validCommand()));
        task.setNoonResultJson(objectMapper.writeValueAsString(result));
        return task;
    }

    private ProductListingTaskRecord postCreateAuthTask() throws Exception {
        ProductListingTaskRecord task = authTask(true, true, 991L);
        ProductListingNoonWriteResult result = objectMapper.readValue(
                task.getNoonResultJson(), ProductListingNoonWriteResult.class);
        ProductListingNoonWriteStepResult failed = new ProductListingNoonWriteStepResult();
        failed.setStepKey("upsert_zsku_base");
        failed.setStatus("failed");
        failed.setFailureCode(ProductListingWriteAuthRecovery.FAILURE_CODE);
        failed.setRecoveryId(991L);
        failed.setWriteMayHaveOccurred(false);
        result.getSteps().add(failed);
        task.setNoonResultJson(objectMapper.writeValueAsString(result));
        return task;
    }

    private ProductListingNoonWriteStepResult preCreateAbsence() {
        ProductListingNoonWriteStepResult absence = new ProductListingNoonWriteStepResult();
        absence.setStepKey("pre_create_absence_verified");
        absence.setStatus("succeeded");
        absence.setExternalReference(
                "storeCode=STR245027-NAE;partnerSku=NN-TEST-PSKU;realRunTaskId=88003"
                        + ";checkedAt=2026-07-27T10:15:30+08:00");
        absence.setWriteMayHaveOccurred(false);
        return absence;
    }

    private final class Fixture {
        private final ProductListingAuthRecoveryMapper authMapper =
                mock(ProductListingAuthRecoveryMapper.class);
        private final ProductListingMapper listingMapper =
                mock(ProductListingMapper.class);
        private final ProductListingNoonWriteAdapter adapter =
                mock(ProductListingNoonWriteAdapter.class);
        private final ProductListingService service = mock(ProductListingService.class);
        private final ProductListingReauthenticationCommitter committer =
                mock(ProductListingReauthenticationCommitter.class);
        private final ProductListingCreateOutcomeService outcomeService =
                mock(ProductListingCreateOutcomeService.class);
        private final BusinessAccessContext context;
        private final ProductListingAuthRecoveryCoordinator coordinator;

        private Fixture(ProductListingTaskRecord task) {
            context = ProductListingTestFixtures.businessContext(
                    10002L, 90001L, task.getStoreCode());
            coordinator = new ProductListingAuthRecoveryCoordinator(
                    authMapper, listingMapper, adapter, service,
                    committer, outcomeService, objectMapper);
        }
    }
}
