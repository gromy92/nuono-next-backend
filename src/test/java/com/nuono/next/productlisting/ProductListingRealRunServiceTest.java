package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProductListingRealRunServiceTest {

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

    @Test
    void secondRealRunAttemptForSameDryRunIsRejectedWithoutCallingAdapterAgain() {
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

        ProductListingTaskView first = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView second = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("submitted", first.getStatus());
        assertEquals("rejected", second.getStatus());
        assertEquals("real_run_already_active", second.getFailureCode());
        assertEquals(0, adapter.callCount());
    }

    @Test
    void readBackFailureAfterRemoteWriteKeepsWrittenVerificationStateAndRejectsRecreate() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(readBackFailureAfterRemoteWriteResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        ProductListingTaskView submitted = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView first = service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingTaskView second = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("written_verify_failed", first.getStatus());
        assertEquals("noon_readback", first.getFailureCategory());
        assertEquals("readback_mismatch", first.getFailureCode());
        assertEquals("rejected", second.getStatus());
        assertEquals("real_run_already_attempted", second.getFailureCode());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void imageUploadFailureAfterRemoteCreateKeepsWrittenVerificationState() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(imageUploadFailureAfterRemoteCreateResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        ProductListingTaskView submitted = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView executed = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("written_verify_failed", executed.getStatus());
        assertEquals("noon_api", executed.getFailureCategory());
        assertEquals("noon_write_failed", executed.getFailureCode());
        assertTrue(executed.getNoonResult().getSteps().get(0).getExternalReference().contains("skuParent=ZPARENT"));
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("upload_images"));
        assertEquals(1, adapter.callCount());
    }

    @Test
    void readBackOnlyRecoveryPromotesWrittenVerifyFailedTaskWithoutRecreatingNoonProduct() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        readBackFailureAfterRemoteWriteResult(),
                        successReadBackStep()
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

        ProductListingTaskView recovered = service.verifyRealRunReadBack(context, writtenVerifyFailed.getTaskId());

        assertEquals("succeeded", recovered.getStatus());
        assertEquals(1, adapter.callCount());
        assertEquals(1, adapter.verifyReadBackCallCount());
        assertEquals("ZPARENT", adapter.lastReadBackSkuParent());
        assertEquals("PSKU_CODE_1", adapter.lastReadBackPskuCode());
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("verify_noon_readback"));
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("readBackAttempts=1"));
    }

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
    void workerDoesNotExecuteAlreadyCompletedRealRunTwice() {
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

        ProductListingTaskView first = service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingTaskView second = service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("succeeded", first.getStatus());
        assertEquals("succeeded", second.getStatus());
        assertEquals(1, adapter.callCount());
    }

    private ProductListingNoonWriteResult successResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return ProductListingNoonWriteResult.succeeded(List.of(create, readBack));
    }

    private ProductListingNoonWriteResult readBackFailureAfterRemoteWriteResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("failed");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=13");
        readBack.setFailureCode("readback_mismatch");
        readBack.setFailureMessage("Noon product was created but readback fields differ.");
        return ProductListingNoonWriteResult.failed(
                "noon_readback",
                "readback_mismatch",
                "Noon product was created but readback fields differ.",
                List.of(create, readBack)
        );
    }

    private ProductListingNoonWriteResult imageUploadFailureAfterRemoteCreateResult() {
        ProductListingNoonWriteStepResult create = new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult uploadImages = new ProductListingNoonWriteStepResult();
        uploadImages.setStepKey("upload_images");
        uploadImages.setStatus("failed");
        uploadImages.setExternalReference("uploadedImages=0");
        uploadImages.setFailureCode("noon_image_upload_failed");
        uploadImages.setFailureMessage("HTTP 400 Filetype <None> not supported.");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_write_failed",
                "HTTP 400 Filetype <None> not supported.",
                List.of(create, uploadImages)
        );
    }

    private ProductListingNoonWriteResult continuationSuccessResult() {
        ProductListingNoonWriteStepResult uploadImages = new ProductListingNoonWriteStepResult();
        uploadImages.setStepKey("upload_images");
        uploadImages.setStatus("succeeded");
        uploadImages.setExternalReference("uploadedImages=1;uploadedImagePaths=noon-uploaded/sku-main.jpg");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return ProductListingNoonWriteResult.succeeded(List.of(uploadImages, readBack));
    }

    private ProductListingNoonWriteStepResult successReadBackStep() {
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return readBack;
    }

    private static ObjectProvider<ProductListingProjectionBackfill> objectProvider(
            ProductListingProjectionBackfill backfill
    ) {
        return new ObjectProvider<>() {
            @Override
            public ProductListingProjectionBackfill getObject(Object... args) {
                return backfill;
            }

            @Override
            public ProductListingProjectionBackfill getIfAvailable() {
                return backfill;
            }

            @Override
            public ProductListingProjectionBackfill getIfUnique() {
                return backfill;
            }

            @Override
            public ProductListingProjectionBackfill getObject() {
                return backfill;
            }
        };
    }

    private static class TrackingProjectionBackfill implements ProductListingProjectionBackfill {
        private int callCount;
        private int draftBackfillCallCount;
        private ProductListingTaskRecord task;
        private ProductListingDraftRecord draftRecord;
        private ProductListingDraftCommand draft;
        private ProductListingDraftCommand draftProjection;
        private ProductListingNoonWriteResult result;

        @Override
        public void backfillDraftListing(
                ProductListingDraftRecord record,
                ProductListingDraftCommand draft
        ) {
            this.draftBackfillCallCount += 1;
            this.draftRecord = record;
            this.draftProjection = draft;
        }

        @Override
        public void backfillSuccessfulListing(
                ProductListingTaskRecord task,
                ProductListingDraftCommand draft,
                ProductListingNoonWriteResult result
        ) {
            this.callCount += 1;
            this.task = task;
            this.draft = draft;
            this.result = result;
        }
    }
}
