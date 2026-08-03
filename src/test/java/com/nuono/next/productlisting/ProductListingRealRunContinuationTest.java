package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;


class ProductListingRealRunContinuationTest extends ProductListingRealRunServiceTest {
    @Test
    void continueAfterCreateRecoveryWritesRemainingStepsWithoutRecreatingNoonProduct() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        imageUploadFailureAfterRemoteCreateResult(),
                        continuationSuccessResult(),
                        null
                );
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView writtenVerifyFailed = service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingTaskView recovered = service.continueRealRunAfterCreate(context, writtenVerifyFailed.getTaskId());

        assertEquals("succeeded", recovered.getStatus());
        assertEquals(1, adapter.callCount());
        assertEquals(1, adapter.continueAfterCreateCallCount());
        assertEquals(0, adapter.verifyReadBackCallCount());
        assertEquals("ZPARENT", adapter.lastContinueSkuParent());
        assertEquals("PSKU_CODE_1", adapter.lastContinuePskuCode());
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("upload_images"));
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("uploadedImagePaths=noon-uploaded/sku-main.jpg"));
    }

    @Test
    void latestSuccessfulContinuationStepAllowsReadBackRetryAndCompletion() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        imageUploadFailureAfterRemoteCreateResult(),
                        continuationReadBackFailureResult(),
                        successReadBackStep()
                );
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        ProductListingWorkflowService workflowService =
                new ProductListingWorkflowService(mapper, service, new ObjectMapper());
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView initialFailure =
                service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingTaskView readBackFailure =
                service.continueRealRunAfterCreate(context, initialFailure.getTaskId());
        ProductListingWorkflowView workflow =
                workflowService.loadWorkflow(context, dryRun.getDraftId());

        assertEquals("written_verify_failed", readBackFailure.getStatus());
        assertEquals(ProductListingWorkflowView.NextAction.VERIFY_READBACK, workflow.getNextAction());

        ProductListingTaskView recovered =
                service.verifyRealRunReadBack(context, readBackFailure.getTaskId());

        assertEquals("succeeded", recovered.getStatus());
        assertEquals(1, adapter.continueAfterCreateCallCount());
        assertEquals(1, adapter.verifyReadBackCallCount());
    }

    @Test
    void resolvedUnknownCreateCanContinueThenRetryReadBackToCompletion() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        unknownCreateOutcomeResult(),
                        continuationReadBackFailureResult(),
                        successReadBackStep()
                ).withCreateReferenceStep(successCreateReferenceLookupStep());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        ObjectMapper objectMapper = new ObjectMapper();
        ProductListingCreateOutcomeService outcomeService =
                new ProductListingCreateOutcomeService(mapper, service, adapter, objectMapper);
        ProductListingWorkflowService workflowService =
                new ProductListingWorkflowService(mapper, service, objectMapper);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView unknown =
                service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingCreateOutcomeVerificationView resolved =
                outcomeService.verify(context, unknown.getTaskId());
        ProductListingTaskView readBackFailure =
                service.continueRealRunAfterCreate(context, unknown.getTaskId());
        ProductListingWorkflowView workflow =
                workflowService.loadWorkflow(context, dryRun.getDraftId());

        assertEquals("found", resolved.getStatus());
        assertEquals("written_verify_failed", readBackFailure.getStatus());
        assertEquals(ProductListingWorkflowView.NextAction.VERIFY_READBACK, workflow.getNextAction());

        ProductListingTaskView recovered =
                service.verifyRealRunReadBack(context, readBackFailure.getTaskId());

        assertEquals("succeeded", recovered.getStatus());
        assertEquals(1, adapter.resolveCreateReferenceCallCount());
        assertEquals(1, adapter.continueAfterCreateCallCount());
        assertEquals(1, adapter.verifyReadBackCallCount());
    }

    @Test
    void directContinuationWithoutPersistedReferencesCannotLookupOrWrite() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        unknownCreateOutcomeResult(),
                        continuationSuccessResult(),
                        null
                ).withCreateReferenceStep(successCreateReferenceLookupStep());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView uncertain = service.executeSubmittedRealRunTask(submitted.getTaskId());
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context, uncertain.getTaskId())
        );

        assertEquals("written_verify_failed", uncertain.getStatus());
        assertEquals("noon_create_outcome_unknown", uncertain.getFailureCode());
        assertTrue(error.getMessage().contains("不允许执行该恢复操作"));
        assertEquals(1, adapter.callCount());
        assertEquals(0, adapter.resolveCreateReferenceCallCount());
        assertEquals(0, adapter.continueAfterCreateCallCount());
    }

    @Test
    void unknownCreateAuthenticationFailureStaysLockedForAuthorizationRecovery() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        unknownCreateAuthenticationResult()
                );
        ProductListingService service =
                ProductListingTestFixtures.service(mapper, true, adapter);
        ProductListingWorkflowService workflowService =
                new ProductListingWorkflowService(
                        mapper, service, new ObjectMapper());
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView failed =
                service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingWorkflowView workflow =
                workflowService.loadWorkflow(context, dryRun.getDraftId());

        assertEquals("written_verify_failed", failed.getStatus());
        assertEquals("noon_auth_required", failed.getFailureCode());
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.UNKNOWN,
                workflow.getWriteCertainty()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.WAIT_FOR_AUTHORIZATION,
                workflow.getNextAction()
        );
    }

    @Test
    void explicitCreateAuthenticationRejectionIsFailedAndNotStarted() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        explicitCreateAuthenticationRejectionResult()
                );
        ProductListingService service =
                ProductListingTestFixtures.service(mapper, true, adapter);
        ProductListingWorkflowService workflowService =
                new ProductListingWorkflowService(
                        mapper, service, new ObjectMapper());
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView failed =
                service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingWorkflowView workflow =
                workflowService.loadWorkflow(context, dryRun.getDraftId());

        assertEquals("failed", failed.getStatus());
        assertEquals("noon_auth_required", failed.getFailureCode());
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                workflow.getWriteCertainty()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.WAIT_FOR_AUTHORIZATION,
                workflow.getNextAction()
        );
    }

    @Test
    void interruptedTaskCannotCombineLookupAndContinuation() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        successResult(),
                        continuationSuccessResult(),
                        null
                ).withCreateReferenceStep(successCreateReferenceLookupStep());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        mapper.forceRunning(submitted.getTaskId(), LocalDateTime.now().minusHours(2));
        service.recoverStaleRunningRealRunTasks(Duration.ofMinutes(30));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context, submitted.getTaskId())
        );

        assertTrue(error.getMessage().contains("不允许执行该恢复操作"));
        assertEquals(0, adapter.callCount());
        assertEquals(0, adapter.resolveCreateReferenceCallCount());
        assertEquals(0, adapter.continueAfterCreateCallCount());
    }

}
