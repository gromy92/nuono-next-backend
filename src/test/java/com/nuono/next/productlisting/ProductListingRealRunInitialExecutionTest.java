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


class ProductListingRealRunInitialExecutionTest extends ProductListingRealRunServiceTest {
    @Test
    void rebuildSubmissionEntryKeepsIdentityReservationInsideTransaction() throws Exception {
        assertNotNull(ProductListingService.class.getMethod(
                "submitConfirmedRealRunFromDraft",
                BusinessAccessContext.class,
                ProductListingDraftCommand.class,
                String.class
        ).getAnnotation(Transactional.class));
    }

    @Test
    void confirmedRealRunEnqueuesDurableTaskWithoutCallingNoonAdapterInRequest() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        ProductListingTaskView realRun = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals(0, adapter.callCount());
        assertEquals("REAL_RUN", realRun.getMode());
        assertEquals("submitted", realRun.getStatus());
        assertEquals(dryRun.getTaskId(), realRun.getSourceTaskId());
        assertEquals("NN-TEST-PSKU", realRun.getPartnerSku());
        assertNull(realRun.getStartedAt());
        assertNull(realRun.getCompletedAt());
        assertNull(realRun.getNoonResult());
        assertTrue(mapper.insertedTask().getConfirmationJson().contains("confirmRealNoonWrite"));

        ProductListingTaskView loaded = service.loadTask(context, realRun.getTaskId());
        assertEquals("submitted", loaded.getStatus());
        assertNull(loaded.getNoonResult());
    }

    @Test
    void confirmationUsesTheLockedDryRunStatusInsteadOfTheEarlierSnapshot() {
        ProductListingTaskRecord[] lockedTask = new ProductListingTaskRecord[1];
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public ProductListingTaskRecord selectTaskByIdForUpdate(
                            Long taskId,
                            Long ownerUserId
                    ) {
                        return lockedTask[0] == null
                                ? super.selectTaskByIdForUpdate(taskId, ownerUserId)
                                : lockedTask[0];
                    }
                };
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskRecord stored =
                mapper.selectTaskById(dryRun.getTaskId(), 10002L);
        ProductListingTaskRecord superseded = new ProductListingTaskRecord();
        superseded.setId(stored.getId());
        superseded.setDraftId(stored.getDraftId());
        superseded.setOwnerUserId(stored.getOwnerUserId());
        superseded.setStoreCode(stored.getStoreCode());
        superseded.setTaskNo(stored.getTaskNo());
        superseded.setMode(stored.getMode());
        superseded.setStatus("superseded");
        superseded.setInputSnapshotJson(stored.getInputSnapshotJson());
        superseded.setValidationJson(stored.getValidationJson());
        lockedTask[0] = superseded;

        ProductListingTaskView result = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("rejected", result.getStatus());
        assertEquals("dry_run_not_validated", result.getFailureCode());
        assertEquals(0, adapter.callCount());
    }

    @Test
    void workerExecutesSubmittedRealRunAndPersistsReadableResult() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
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

        ProductListingTaskView executed = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals(1, adapter.callCount());
        assertEquals("REAL_RUN", executed.getMode());
        assertEquals("succeeded", executed.getStatus());
        assertEquals(dryRun.getTaskId(), executed.getSourceTaskId());
        assertEquals("NN-TEST-PSKU", executed.getPartnerSku());
        assertNotNull(executed.getStartedAt());
        assertNotNull(executed.getCompletedAt());
        assertNotNull(executed.getNoonResult());
        assertEquals("create_product", executed.getNoonResult().getSteps().get(0).getStepKey());
        assertEquals("skuParent=ZPARENT;pskuCode=PSKU_CODE_1", executed.getNoonResult().getSteps().get(0).getExternalReference());
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("verify_noon_readback"));
    }

    @Test
    void workerBackfillsProductProjectionAfterSuccessfulRealRun() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        TrackingProjectionBackfill projectionBackfill = new TrackingProjectionBackfill();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        properties.setEnabled(true);
        ProductListingService service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties,
                adapter,
                null,
                objectProvider(projectionBackfill)
        );
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

        ProductListingTaskView executed = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("succeeded", executed.getStatus());
        assertEquals(1, projectionBackfill.callCount);
        assertEquals(executed.getTaskId(), projectionBackfill.task.getId());
        assertEquals("NN-TEST-PSKU", projectionBackfill.draft.getPsku());
        assertEquals("skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                projectionBackfill.result.getSteps().get(0).getExternalReference());
    }

    @Test
    void saveDraftBackfillsDraftProductProjectionWhenPskuIsPresent() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        TrackingProjectionBackfill projectionBackfill = new TrackingProjectionBackfill();
        ProductListingRealWriteProperties properties = new ProductListingRealWriteProperties();
        ProductListingService service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                properties,
                adapter,
                null,
                objectProvider(projectionBackfill)
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );

        ProductListingDraftView draftView = service.saveDraft(context, ProductListingTestFixtures.validCommand());

        assertEquals(1, projectionBackfill.draftBackfillCallCount);
        assertEquals(draftView.getDraftId(), projectionBackfill.draftRecord.getId());
        assertEquals("NN-TEST-PSKU", projectionBackfill.draftProjection.getPsku());
    }

    @Test
    void submitConfirmedRealRunFromDraftUsesSharedDraftDryRunAndRealRunPipeline() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );

        ProductListingRealRunSubmission submission = service.submitConfirmedRealRunFromDraft(
                context,
                ProductListingTestFixtures.validCommand(),
                "confirmed by product rebuild after delete task 77001"
        );

        assertEquals(10001L, submission.getDraft().getDraftId());
        assertEquals(20001L, submission.getDryRun().getTaskId());
        assertEquals("validated", submission.getDryRun().getStatus());
        assertEquals(20002L, submission.getRealRun().getTaskId());
        assertEquals("submitted", submission.getRealRun().getStatus());
        assertEquals(20001L, submission.getRealRun().getSourceTaskId());
        assertEquals(0, adapter.callCount());
    }

    @Test
    void killSwitchRejectsConfirmedRealRunWithoutCallingAdapter() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, false, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        ProductListingTaskView realRun = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals(0, adapter.callCount());
        assertEquals("REAL_RUN", realRun.getMode());
        assertEquals("rejected", realRun.getStatus());
        assertEquals("real_write_disabled", realRun.getFailureCode());
    }

}
