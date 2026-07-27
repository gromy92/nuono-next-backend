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


class ProductListingRealRunOutcomeGuardTest extends ProductListingRealRunServiceTest {
    @Test
    void validatedDryRunMustBeReopenedBeforeDraftChangeAndCannotThenBePublished() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingDraftCommand changed = ProductListingTestFixtures.validCommand();
        changed.setDraftId(dryRun.getDraftId());
        changed.setProductTitleEn("Changed product title after validation");
        IllegalArgumentException saveError = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(context, changed)
        );
        ProductListingWorkflowService workflowService =
                new ProductListingWorkflowService(mapper, service, new ObjectMapper());
        workflowService.reopenReview(context, dryRun.getTaskId());
        service.saveDraft(context, changed);

        ProductListingTaskView result = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView repeated = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("rejected", result.getStatus());
        assertEquals("dry_run_not_validated", result.getFailureCode());
        assertEquals(result.getTaskId(), repeated.getTaskId());
        assertTrue(saveError.getMessage().contains("返回修改"));
        assertEquals(0, adapter.callCount());
    }

    @Test
    void submittedRealRunBlocksDraftMutationFromAnotherTabOrDirectApi() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingDraftCommand changed = ProductListingTestFixtures.validCommand();
        changed.setDraftId(dryRun.getDraftId());
        changed.setProductTitleEn("Changed while real-run is pending");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(context, changed)
        );

        assertTrue(error.getMessage().contains("真实上架任务"));
        assertEquals(0, adapter.callCount());
    }

    @Test
    void reopenedReviewCannotAccumulateRejectedAuditsThroughRepeatedDirectConfirmation() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        ProductListingWorkflowService workflowService = new ProductListingWorkflowService(
                mapper,
                service,
                new ProductListingWorkflowProjector(),
                new ProductListingDryRunFreshness(new ObjectMapper())
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);
        workflowService.reopenReview(context, dryRun.getTaskId());

        ProductListingTaskView first = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView repeated = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("rejected", first.getStatus());
        assertEquals("dry_run_not_validated", first.getFailureCode());
        assertEquals(first.getTaskId(), repeated.getTaskId());
        assertEquals(0, adapter.callCount());
    }

    @Test
    void secondRealRunAttemptReturnsExistingAttemptWithoutCreatingAnotherAuditTask() {
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
        assertEquals(first.getTaskId(), second.getTaskId());
        assertEquals("submitted", second.getStatus());
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
        assertEquals(first.getTaskId(), second.getTaskId());
        assertEquals("written_verify_failed", second.getStatus());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void failedRealRunLocksSameDryRunEvenWhenFailureWasBeforeRemoteWrite() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(noonFailureBeforeRemoteWriteResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        ProductListingTaskView submitted = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView failed = service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingTaskView retry = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("failed", failed.getStatus());
        assertEquals(failed.getTaskId(), retry.getTaskId());
        assertEquals("failed", retry.getStatus());
        assertEquals(1, adapter.callCount());
    }

    @Test
    void partnerSkuAlreadyExistsFailureLocksSameDryRunAndShowsChineseMessage() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(partnerSkuAlreadyExistsResult());
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        ProductListingTaskView submitted = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());
        ProductListingTaskView failed = service.executeSubmittedRealRunTask(submitted.getTaskId());
        ProductListingTaskView retry = service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals("failed", failed.getStatus());
        assertEquals("partner_sku_already_exists", failed.getFailureCode());
        assertTrue(failed.getFailureMessage().contains("PSKU 已存在"));
        assertTrue(failed.getFailureMessage().contains("NN-TEST-PSKU"));
        assertEquals(failed.getTaskId(), retry.getTaskId());
        assertEquals("failed", retry.getStatus());
        assertEquals("partner_sku_already_exists", retry.getFailureCode());
        assertTrue(retry.getFailureMessage().contains("PSKU 已存在"));
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

}
