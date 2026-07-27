package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductListingDraftAttemptBlockerTest {

    @Test
    void genericFailedAttemptBlocksNewDryRunAndPreexistingSecondConfirmation() {
        Fixture fixture = fixture(genericFailure());
        ProductListingDraftView draft =
                fixture.service.saveDraft(fixture.context, ProductListingTestFixtures.validCommand());
        ProductListingTaskView firstDryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView secondDryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView submitted = fixture.service.confirmRealRun(
                fixture.context, firstDryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        ProductListingTaskView failed =
                fixture.service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingWorkflowView workflow =
                fixture.workflowService.loadWorkflow(fixture.context, draft.getDraftId());

        assertEquals("failed", failed.getStatus());
        assertEquals(ProductListingWorkflowView.Phase.ACTION_REQUIRED, workflow.getPhase());
        assertEquals(ProductListingWorkflowView.WriteCertainty.UNKNOWN, workflow.getWriteCertainty());
        assertEquals(ProductListingWorkflowView.NextAction.NONE, workflow.getNextAction());
        assertThrows(IllegalArgumentException.class, () -> fixture.submitDryRun(draft.getDraftId()));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.confirmRealRun(
                fixture.context, secondDryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()));
    }

    @Test
    void succeededProductRebuildBlocksNewDryRunAndPreexistingSecondConfirmation() {
        Fixture fixture = fixture(ProductListingNoonWriteResult.succeeded(List.of()));
        ProductListingDraftCommand rebuild = ProductListingTestFixtures.validCommand();
        rebuild.setSourceType("PRODUCT_REBUILD");
        rebuild.setSourceRefId(31001L);
        rebuild.setRebuildSourceProductMasterId(31001L);
        ProductListingDraftView draft = fixture.service.saveDraft(fixture.context, rebuild);
        ProductListingTaskView firstDryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView secondDryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView submitted = fixture.service.confirmRealRun(
                fixture.context, firstDryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        ProductListingTaskView succeeded =
                fixture.service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingWorkflowView workflow =
                fixture.workflowService.loadWorkflow(fixture.context, draft.getDraftId());

        assertEquals("succeeded", succeeded.getStatus());
        assertEquals(ProductListingWorkflowView.Phase.PUBLISHED, workflow.getPhase());
        assertThrows(IllegalArgumentException.class, () -> fixture.submitDryRun(draft.getDraftId()));
        assertThrows(IllegalArgumentException.class, () -> fixture.service.confirmRealRun(
                fixture.context, secondDryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()));
    }

    @Test
    void explicitReopenOfClearlyNotStartedAttemptAllowsNewDryRun() {
        Fixture fixture = fixture(clearlyNotStartedFailure());
        ProductListingDraftView draft =
                fixture.service.saveDraft(fixture.context, ProductListingTestFixtures.validCommand());
        ProductListingTaskView dryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView submitted = fixture.service.confirmRealRun(
                fixture.context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView failed =
                fixture.service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("failed", failed.getStatus());
        assertThrows(IllegalArgumentException.class, () -> fixture.submitDryRun(draft.getDraftId()));

        fixture.workflowService.reopenReview(fixture.context, dryRun.getTaskId());
        ProductListingTaskView replacement = fixture.submitDryRun(draft.getDraftId());

        assertEquals("validated", replacement.getStatus());
    }

    @Test
    void attemptedValidationFailedDryRunCanBeExplicitlyReopened() {
        Fixture fixture = fixture(null);
        ProductListingDraftCommand invalid = ProductListingTestFixtures.validCommand();
        invalid.setPsku(null);
        ProductListingDraftView draft = fixture.service.saveDraft(fixture.context, invalid);
        ProductListingTaskView validationFailed = fixture.submitDryRun(draft.getDraftId());
        ProductListingDraftCommand corrected = ProductListingTestFixtures.validCommand();
        corrected.setDraftId(draft.getDraftId());

        fixture.service.saveDraft(fixture.context, corrected);
        ProductListingTaskView rejected = fixture.service.confirmRealRun(
                fixture.context,
                validationFailed.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("validation_failed", validationFailed.getStatus());
        assertEquals("rejected", rejected.getStatus());
        assertThrows(
                IllegalArgumentException.class,
                () -> fixture.service.saveDraft(fixture.context, corrected)
        );

        fixture.workflowService.reopenReview(fixture.context, validationFailed.getTaskId());
        ProductListingDraftView saved = fixture.service.saveDraft(fixture.context, corrected);

        assertEquals("ready_for_dry_run", saved.getStatus());
    }

    @Test
    void orphanTerminalAttemptRemainsBlockingWhenSourceDryRunCannotBeResolved() {
        Fixture fixture = fixture(null);
        ProductListingDraftView draft =
                fixture.service.saveDraft(fixture.context, ProductListingTestFixtures.validCommand());
        ProductListingTaskView dryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView submitted = fixture.service.confirmRealRun(
                fixture.context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskRecord orphan =
                fixture.mapper.selectTaskById(submitted.getTaskId(), 10002L);
        orphan.setStatus("failed");
        orphan.setFailureCategory("authentication");
        orphan.setFailureCode("noon_auth_failed");
        orphan.setFailureMessage("Noon cookie expired before create.");
        orphan.setSourceTaskId(999999L);
        fixture.mapper.markValidatedDryRunSuperseded(dryRun.getTaskId(), 10002L);

        assertThrows(IllegalArgumentException.class, () -> fixture.submitDryRun(draft.getDraftId()));
    }

    @Test
    void invalidHistoricalNoonEvidenceFailsClosedAfterSourceReopen() {
        Fixture fixture = fixture(null);
        ProductListingDraftView draft =
                fixture.service.saveDraft(fixture.context, ProductListingTestFixtures.validCommand());
        ProductListingTaskView dryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView submitted = fixture.service.confirmRealRun(
                fixture.context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskRecord invalidEvidence =
                fixture.mapper.selectTaskById(submitted.getTaskId(), 10002L);
        invalidEvidence.setStatus("failed");
        invalidEvidence.setFailureCategory("authentication");
        invalidEvidence.setFailureCode("noon_auth_failed");
        invalidEvidence.setFailureMessage("Noon cookie expired before create.");
        invalidEvidence.setNoonResultJson("{invalid-json");
        fixture.mapper.markValidatedDryRunSuperseded(dryRun.getTaskId(), 10002L);

        assertThrows(IllegalArgumentException.class, () -> fixture.submitDryRun(draft.getDraftId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "[]",
            "\"x\"",
            "{\"steps\":\"bad\"}",
            "{\"success\":\"false\",\"steps\":[]}"
    })
    void wrongHistoricalNoonEvidenceSchemaFailsClosed(String noonResultJson) {
        Fixture fixture = fixture(null);
        ProductListingDraftView draft =
                fixture.service.saveDraft(fixture.context, ProductListingTestFixtures.validCommand());
        ProductListingTaskView dryRun = fixture.submitDryRun(draft.getDraftId());
        ProductListingTaskView submitted = fixture.service.confirmRealRun(
                fixture.context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskRecord invalidEvidence =
                fixture.mapper.selectTaskById(submitted.getTaskId(), 10002L);
        invalidEvidence.setStatus("failed");
        invalidEvidence.setFailureCategory("authentication");
        invalidEvidence.setFailureCode("noon_auth_failed");
        invalidEvidence.setFailureMessage("Noon cookie expired before create.");
        invalidEvidence.setNoonResultJson(noonResultJson);
        fixture.mapper.markValidatedDryRunSuperseded(dryRun.getTaskId(), 10002L);

        assertThrows(IllegalArgumentException.class, () -> fixture.submitDryRun(draft.getDraftId()));
    }

    private static ProductListingNoonWriteResult genericFailure() {
        return ProductListingNoonWriteResult.failed(
                "noon_api", "noon_write_exception", "gateway failed", List.of());
    }

    private static ProductListingNoonWriteResult clearlyNotStartedFailure() {
        return ProductListingNoonWriteResult.failed(
                "authentication",
                "noon_auth_required",
                "Noon cookie expired before create.",
                List.of()
        );
    }

    private static Fixture fixture(ProductListingNoonWriteResult result) {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(result)
        );
        return new Fixture(mapper, service);
    }

    private static final class Fixture {
        private final ProductListingTestFixtures.FakeProductListingMapper mapper;
        private final ProductListingService service;
        private final ProductListingWorkflowService workflowService;
        private final BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(
                        10002L, 90001L, "STR245027-NAE");

        private Fixture(
                ProductListingTestFixtures.FakeProductListingMapper mapper,
                ProductListingService service
        ) {
            this.mapper = mapper;
            this.service = service;
            this.workflowService =
                    new ProductListingWorkflowService(mapper, service, new ObjectMapper());
        }

        private ProductListingTaskView submitDryRun(Long draftId) {
            ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
            command.setDraftId(draftId);
            command.setStoreCode("STR245027-NAE");
            return service.submitDryRun(context, command);
        }
    }
}
