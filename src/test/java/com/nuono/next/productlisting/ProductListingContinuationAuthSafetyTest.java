package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingContinuationAuthSafetyTest {

    @Test
    void authFailureAfterKnownCreateMustKeepWriteRiskAndBlockAnotherCreate() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        failedAfterCreate(),
                        continuationAuthFailure(),
                        null
                );
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView partial = service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingTaskView authPending = service.continueRealRunAfterCreate(
                context, partial.getTaskId()
        );
        ProductListingTaskView duplicate = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("written_verify_failed", authPending.getStatus());
        assertEquals(ProductListingWriteAuthRecovery.FAILURE_CODE, authPending.getFailureCode());
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("\"writeMayHaveOccurred\":true"));
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("\"recoveryId\":991"));
        assertEquals("rejected", duplicate.getStatus());
        assertEquals("real_run_already_attempted", duplicate.getFailureCode());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void failedReferenceLookupMustLeaveClaimedTaskInSafeTerminalState() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingNoonWriteStepResult lookupFailure = step(
                "resolve_create_reference",
                "failed",
                "noon_create_reference_not_found",
                "Noon create reference was not found."
        );
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        unknownCreateResult(), null, null
                ).withCreateReferenceStep(lookupFailure);
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView uncertain = service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingTaskView checked = service.continueRealRunAfterCreate(
                context, uncertain.getTaskId()
        );

        assertEquals("written_verify_failed", checked.getStatus());
        assertEquals("noon_create_outcome_unknown", checked.getFailureCode());
        assertEquals(1, adapter.resolveCreateReferenceCallCount());
        assertEquals(0, adapter.continueAfterCreateCallCount());
    }

    @Test
    void finalReadBackUsesLatestResultForEachContinuedWriteStep() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        failedAfterCreate(), continuationReadBackFailure(), successfulReadBack()
                );
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView partial = service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingTaskView continued = service.continueRealRunAfterCreate(context, partial.getTaskId());

        ProductListingTaskView verified = service.verifyRealRunReadBack(context, continued.getTaskId());

        assertEquals("written_verify_failed", continued.getStatus());
        assertEquals("succeeded", verified.getStatus());
        assertEquals(1, adapter.continueAfterCreateCallCount());
        assertEquals(1, adapter.verifyReadBackCallCount());
    }

    @Test
    void recoveredCreateReferenceMustBeCheckpointedBeforeContinuationWrites() {
        boolean[] checkpointed = {false};
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public int checkpointRunningTaskNoonResult(
                            Long taskId,
                            Long ownerUserId,
                            String noonResultJson,
                            java.time.LocalDateTime startedAt
                    ) {
                        checkpointed[0] = noonResultJson.contains("resolve_create_reference");
                        return super.checkpointRunningTaskNoonResult(
                                taskId, ownerUserId, noonResultJson, startedAt
                        );
                    }
                };
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        unknownCreateResult(), continuationSuccess(), null
                ) {
                    @Override
                    public ProductListingNoonWriteResult continueAfterCreate(
                            ProductListingNoonWriteRequest request, String skuParent, String pskuCode
                    ) {
                        assertTrue(checkpointed[0]);
                        return super.continueAfterCreate(request, skuParent, pskuCode);
                    }
                };
        adapter.withCreateReferenceStep(successfulCreateReference());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView uncertain = service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingTaskView referenceRecovered =
                service.continueRealRunAfterCreate(context, uncertain.getTaskId());
        ProductListingTaskView recovered =
                service.continueRealRunAfterCreate(context, uncertain.getTaskId());

        assertEquals("written_verify_failed", referenceRecovered.getStatus());
        assertEquals("succeeded", recovered.getStatus());
        assertTrue(checkpointed[0]);
        assertEquals(1, adapter.continueAfterCreateCallCount());
    }

    private ProductListingNoonWriteResult failedAfterCreate() {
        ProductListingNoonWriteStepResult create = step(
                "create_product", "succeeded", null, null
        );
        create.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult laterFailure = step(
                "upload_images", "failed", "noon_image_upload_failed", "upload rejected"
        );
        return ProductListingNoonWriteResult.failed(
                "noon_api", "noon_image_upload_failed", "upload rejected",
                List.of(create, laterFailure)
        );
    }

    private ProductListingNoonWriteResult continuationAuthFailure() {
        ProductListingNoonWriteStepResult auth = step(
                "upsert_zsku_base",
                "failed",
                ProductListingWriteAuthRecovery.FAILURE_CODE,
                "Noon Project 授权恢复中"
        );
        auth.setRecoveryId(991L);
        auth.setWriteMayHaveOccurred(false);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "authorization",
                ProductListingWriteAuthRecovery.FAILURE_CODE,
                "Noon Project 授权恢复中",
                List.of(auth)
        );
        result.setRecoveryId(991L);
        result.setWriteMayHaveOccurred(false);
        return result;
    }

    private ProductListingNoonWriteResult continuationReadBackFailure() {
        ProductListingNoonWriteStepResult auth = step(
                "authorization_recovery",
                "failed",
                ProductListingWriteAuthRecovery.FAILURE_CODE,
                "earlier authorization recovery"
        );
        ProductListingNoonWriteStepResult upload = step("upload_images", "succeeded", null, null);
        upload.setExternalReference("uploadedImages=1;uploadedImagePaths=noon-uploaded/main.jpg");
        ProductListingNoonWriteStepResult readBack = step(
                "verify_noon_readback", "failed", "readback_mismatch", "readback pending"
        );
        return ProductListingNoonWriteResult.failed(
                "noon_readback", "readback_mismatch", "readback pending", List.of(auth, upload, readBack)
        );
    }

    private ProductListingNoonWriteResult continuationSuccess() {
        ProductListingNoonWriteStepResult upload = step("upload_images", "succeeded", null, null);
        ProductListingNoonWriteStepResult readBack = successfulReadBack();
        return ProductListingNoonWriteResult.succeeded(List.of(upload, readBack));
    }

    private ProductListingNoonWriteStepResult successfulReadBack() {
        ProductListingNoonWriteStepResult readBack =
                step("verify_noon_readback", "succeeded", null, null);
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=1");
        return readBack;
    }

    private ProductListingNoonWriteStepResult successfulCreateReference() {
        ProductListingNoonWriteStepResult lookup =
                step("resolve_create_reference", "succeeded", null, null);
        lookup.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        return lookup;
    }

    private ProductListingNoonWriteResult unknownCreateResult() {
        ProductListingNoonWriteStepResult create = step(
                "create_product",
                "failed",
                "noon_create_outcome_unknown",
                "Noon create request outcome is unknown."
        );
        create.setWriteMayHaveOccurred(true);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "noon_uncertain_write",
                "noon_create_outcome_unknown",
                "Noon create request outcome is unknown.",
                List.of(preCreateAbsence(), create)
        );
        result.setWriteMayHaveOccurred(true);
        return result;
    }

    private ProductListingNoonWriteStepResult preCreateAbsence() {
        ProductListingNoonWriteStepResult absence =
                step("pre_create_absence_verified", "succeeded", null, null);
        absence.setExternalReference(
                "storeCode=STR245027-NAE;partnerSku=NN-TEST-PSKU;realRunTaskId=20002"
                        + ";checkedAt=2026-07-27T10:15:30+08:00");
        absence.setWriteMayHaveOccurred(false);
        return absence;
    }

    private ProductListingNoonWriteStepResult step(
            String key,
            String status,
            String failureCode,
            String failureMessage
    ) {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey(key);
        step.setStatus(status);
        step.setFailureCode(failureCode);
        step.setFailureMessage(failureMessage);
        return step;
    }
}
