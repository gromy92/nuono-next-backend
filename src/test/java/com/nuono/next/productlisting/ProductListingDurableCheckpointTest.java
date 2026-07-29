package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ProductListingDurableCheckpointTest {
    @Test
    void fatalExitBeforeProviderWriteLeavesANewTaskSafelyReopenable() {
        Fixture fixture = executeUntilFatalExit(request -> {
        });

        ProductListingTaskView recovered = recover(fixture);
        ProductListingWorkflowView workflow = workflow(recovered);
        assertEquals("failed", recovered.getStatus());
        assertEquals(
                "real_run_interrupted_before_write",
                recovered.getFailureCode()
        );
        assertEquals(
                ProductListingWorkflowView.WriteCertainty.NOT_STARTED,
                workflow.getWriteCertainty()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.REVIEW_DRAFT,
                workflow.getNextAction()
        );
    }
    @Test
    void fatalExitKeepsUnknownCreateEvidenceUsableByReadOnlyRecovery() {
        Fixture fixture = executeUntilFatalExit(request ->
                request.checkpointNoonResultOrThrow(
                        unknownCreateResult(request)));

        ProductListingTaskView interrupted = fixture.service.loadTask(
                fixture.context, fixture.taskId);
        assertEquals("running", interrupted.getStatus());
        assertNotNull(interrupted.getNoonResult());
        ProductListingTaskView recovered = recover(fixture);
        ProductListingWorkflowView workflow = workflow(recovered);
        assertEquals("written_verify_failed", recovered.getStatus());
        assertEquals(
                "noon_create_outcome_unknown",
                recovered.getFailureCode()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.CHECK_CREATE_RESULT,
                workflow.getNextAction()
        );
        assertDoesNotThrow(() ->
                ProductListingCreateContinuationPolicy.requireRecoverable(
                        recovered.getNoonResult(),
                        recovered.getTaskId(),
                        recovered.getStoreCode(),
                        recovered.getPartnerSku()
                ));
    }
    @Test
    void fatalExitAfterExplicitNoWriteResultReturnsToDraftReview() {
        Fixture fixture = executeUntilFatalExit(request ->
                request.checkpointNoonResultOrThrow(
                        rejectedCreateResult()));

        ProductListingTaskView recovered = recover(fixture);
        ProductListingWorkflowView workflow = workflow(recovered);
        assertEquals("failed", recovered.getStatus());
        assertEquals(
                "noon_create_rejected",
                recovered.getFailureCode()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.REVIEW_DRAFT,
                workflow.getNextAction()
        );
    }
    @Test
    void fatalExitAfterSuccessfulWriteOnlyReplaysLocalProjection() {
        Fixture fixture = executeUntilFatalExit(request ->
                request.checkpointNoonResultOrThrow(successResult()));

        ProductListingTaskView recovered = recover(fixture);
        ProductListingWorkflowView workflow = workflow(recovered);
        assertEquals("written_verify_failed", recovered.getStatus());
        assertEquals(
                "projection_backfill_failed",
                recovered.getFailureCode()
        );
        assertEquals(
                ProductListingWorkflowView.NextAction.REPLAY_PROJECTION,
                workflow.getNextAction()
        );
        ProductListingTaskView completed =
                fixture.service.replaySuccessfulProjectionBackfill(
                        fixture.context, fixture.taskId);
        assertEquals("succeeded", completed.getStatus());
        Fixture readBackFailure = executeUntilFatalExit(request ->
                request.checkpointNoonResultOrThrow(
                        readBackFailureResult()));
        ProductListingTaskView retry = recover(readBackFailure);
        assertEquals("readback_mismatch", retry.getFailureCode());
        assertEquals(
                ProductListingWorkflowView.NextAction.VERIFY_READBACK,
                workflow(retry).getNextAction()
        );
    }
    private Fixture executeUntilFatalExit(
            Consumer<ProductListingNoonWriteRequest> beforeExit
    ) {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service =
                ProductListingTestFixtures.service(
                        mapper, true, fatalAdapter(beforeExit));
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView dryRun =
                ProductListingTestFixtures.validatedDryRun(
                        service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        assertThrows(
                SimulatedJvmTermination.class,
                () -> service.executeSubmittedRealRunTask(
                        submitted.getTaskId())
        );
        return new Fixture(
                mapper, service, context, submitted.getTaskId());
    }
    private ProductListingTaskView recover(Fixture fixture) {
        fixture.mapper.forceRunning(
                fixture.taskId,
                LocalDateTime.now().minusHours(1)
        );
        assertEquals(
                1,
                fixture.service.recoverStaleRunningRealRunTasks(
                        Duration.ofMinutes(30))
        );
        return fixture.service.loadTask(
                fixture.context, fixture.taskId);
    }
    private ProductListingWorkflowView workflow(
            ProductListingTaskView task
    ) {
        return new ProductListingWorkflowProjector().project(
                null, null, task);
    }
    private ProductListingNoonWriteAdapter fatalAdapter(
            Consumer<ProductListingNoonWriteRequest> beforeExit
    ) {
        return new ProductListingNoonWriteAdapter() {
            @Override
            public ProductListingNoonWriteResult execute(
                    ProductListingNoonWriteRequest request
            ) {
                beforeExit.accept(request);
                throw new SimulatedJvmTermination();
            }

            @Override
            public ProductListingNoonWriteResult continueAfterCreate(
                    ProductListingNoonWriteRequest request,
                    String skuParent,
                    String pskuCode
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ProductListingNoonWriteStepResult verifyReadBack(
                    ProductListingNoonWriteRequest request,
                    String skuParent,
                    String pskuCode,
                    List<String> expectedImageValues
            ) {
                throw new UnsupportedOperationException();
            }
        };
    }
    private ProductListingNoonWriteResult unknownCreateResult(
            ProductListingNoonWriteRequest request
    ) {
        ProductListingNoonWriteStepResult absence =
                new ProductListingNoonWriteStepResult();
        absence.setStepKey("pre_create_absence_verified");
        absence.setStatus("succeeded");
        absence.setWriteMayHaveOccurred(false);
        absence.setExternalReference(
                "storeCode=" + request.getStoreCode()
                        + ";partnerSku=" + request.getDraft().getPsku()
                        + ";realRunTaskId=" + request.getRealRunTaskId()
                        + ";checkedAt=" + OffsetDateTime.now()
        );
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_outcome_unknown");
        create.setFailureMessage("Create outcome requires verification.");
        create.setWriteMayHaveOccurred(true);
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "recovery",
                        "noon_create_outcome_unknown",
                        "Create outcome requires verification.",
                        List.of(absence, create)
                );
        result.setWriteMayHaveOccurred(true);
        return result;
    }
    private ProductListingNoonWriteResult rejectedCreateResult() {
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("failed");
        create.setFailureCode("noon_create_rejected");
        create.setFailureMessage("Noon rejected the create payload.");
        create.setWriteMayHaveOccurred(false);
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.failed(
                        "noon_api",
                        "noon_create_rejected",
                        create.getFailureMessage(),
                        List.of(create)
                );
        result.setWriteMayHaveOccurred(false);
        return result;
    }
    private ProductListingNoonWriteResult successResult() {
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setWriteMayHaveOccurred(true);
        create.setExternalReference(
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.succeeded(List.of(create));
        result.setWriteMayHaveOccurred(true);
        return result;
    }
    private ProductListingNoonWriteResult readBackFailureResult() {
        ProductListingNoonWriteResult success = successResult();
        ProductListingNoonWriteStepResult readBack =
                new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("failed");
        readBack.setFailureCode("readback_mismatch");
        readBack.setFailureMessage("Noon read-back does not match.");
        return ProductListingNoonWriteResult.failed(
                "noon_readback",
                readBack.getFailureCode(),
                readBack.getFailureMessage(),
                List.of(success.getSteps().get(0), readBack)
        );
    }
    private static final class Fixture {
        private final ProductListingTestFixtures.FakeProductListingMapper
                mapper;
        private final ProductListingService service;
        private final BusinessAccessContext context;
        private final Long taskId;

        private Fixture(
                ProductListingTestFixtures.FakeProductListingMapper mapper,
                ProductListingService service,
                BusinessAccessContext context,
                Long taskId
        ) {
            this.mapper = mapper;
            this.service = service;
            this.context = context;
            this.taskId = taskId;
        }
    }
    private static final class SimulatedJvmTermination extends Error {
        private static final long serialVersionUID = 1L;
    }
}
