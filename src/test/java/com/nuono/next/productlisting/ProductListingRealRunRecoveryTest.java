package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingRealRunRecoveryTest extends ProductListingRealRunServiceTest {
    @Test
    void splitCreateReferencesAcrossSuccessfulStepsAreNotExposed() {
        ProductListingNoonWriteStepResult create =
                new ProductListingNoonWriteStepResult();
        create.setStepKey("create_product");
        create.setStatus("succeeded");
        create.setExternalReference("skuParent=Z-SPLIT");
        ProductListingNoonWriteStepResult resolve =
                new ProductListingNoonWriteStepResult();
        resolve.setStepKey("resolve_create_reference");
        resolve.setStatus("succeeded");
        resolve.setExternalReference("pskuCode=PSKU-SPLIT");
        ProductListingNoonWriteResult result =
                ProductListingNoonWriteResult.succeeded(
                        List.of(create, resolve)
                );
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(result);
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                adapter
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView executed =
                service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertNull(executed.getSkuParent());
        assertNull(executed.getPskuCode());
    }

    @Test
    void preCreateExistingProductReferencesAreNotExposedAsThisTasksCreatedProduct() {
        ProductListingNoonWriteStepResult preflight =
                new ProductListingNoonWriteStepResult();
        preflight.setStepKey("pre_create_absence_verified");
        preflight.setStatus("failed");
        preflight.setFailureCode("partner_sku_already_exists");
        preflight.setFailureMessage("Noon already contains this PSKU.");
        preflight.setExternalReference(
                "skuParent=Z-EXISTING-PRODUCT;pskuCode=EXISTING-NOON-PSKU"
        );
        preflight.setWriteMayHaveOccurred(false);
        ProductListingNoonWriteResult result = ProductListingNoonWriteResult.failed(
                "validation",
                "partner_sku_already_exists",
                "Noon already contains this PSKU.",
                List.of(preflight)
        );
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(result);
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                adapter
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView failed =
                service.executeSubmittedRealRunTask(submitted.getTaskId());

        assertEquals("failed", failed.getStatus());
        assertEquals("partner_sku_already_exists", failed.getFailureCode());
        assertNull(failed.getSkuParent());
        assertNull(failed.getPskuCode());
    }

    @Test
    void malformedOldestTaskIsQuarantinedWithoutBlockingLaterRunnableTask() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult());
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                adapter
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView firstDryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView firstSubmitted = service.confirmRealRun(
                context,
                firstDryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingDraftCommand secondCommand =
                ProductListingTestFixtures.validCommand();
        secondCommand.setPsku("NN-TEST-PSKU-SECOND");
        secondCommand.setBarcode("6290000000002");
        ProductListingDraftView secondDraft = service.saveDraft(
                context,
                secondCommand
        );
        ProductListingDryRunSubmitCommand secondDryRunCommand =
                new ProductListingDryRunSubmitCommand();
        secondDryRunCommand.setDraftId(secondDraft.getDraftId());
        secondDryRunCommand.setStoreCode("STR245027-NAE");
        ProductListingTaskView secondDryRun = service.submitDryRun(
                context,
                secondDryRunCommand
        );
        ProductListingTaskView secondSubmitted = service.confirmRealRun(
                context,
                secondDryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        mapper.selectTaskById(firstSubmitted.getTaskId(), 10002L)
                .setInputSnapshotJson("{not-json");

        List<ProductListingTaskView> executed =
                service.executeRunnableRealRunTasks(2);

        assertEquals(2, executed.size());
        assertEquals(firstSubmitted.getTaskId(), executed.get(0).getTaskId());
        assertEquals("failed", executed.get(0).getStatus());
        assertEquals(
                "invalid_input_snapshot",
                executed.get(0).getFailureCode()
        );
        assertEquals(secondSubmitted.getTaskId(), executed.get(1).getTaskId());
        assertEquals("succeeded", executed.get(1).getStatus());
        assertEquals(1, adapter.callCount());
    }
}
