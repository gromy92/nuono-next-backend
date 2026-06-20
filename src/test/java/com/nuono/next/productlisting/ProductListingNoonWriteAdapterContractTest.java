package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingNoonWriteAdapterContractTest {

    @Test
    void confirmedRealRunCallsAdapterAndPersistsSuccessResult() {
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

        assertEquals(1, adapter.callCount());
        assertEquals("REAL_RUN", realRun.getMode());
        assertEquals("succeeded", realRun.getStatus());
        assertEquals(dryRun.getTaskId(), adapter.lastRequest().getDryRunTaskId());
        assertEquals(realRun.getTaskId(), adapter.lastRequest().getRealRunTaskId());
        assertEquals("STR245027-NAE", adapter.lastRequest().getStoreCode());
        assertEquals("NN-TEST-PSKU", adapter.lastRequest().getDraft().getPsku());
        assertEquals("succeeded", mapper.updatedTask().getStatus());
        assertNotNull(mapper.insertedTask().getStartedAt());
        assertNotNull(mapper.updatedTask().getCompletedAt());
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("create_product"));
        assertNotNull(realRun.getNoonResult());
        assertEquals(2, realRun.getNoonResult().getSteps().size());
        assertEquals("create_product", realRun.getNoonResult().getSteps().get(0).getStepKey());
        assertEquals(
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1",
                realRun.getNoonResult().getSteps().get(0).getExternalReference()
        );

        ProductListingTaskView loaded = service.loadTask(context, realRun.getTaskId());
        assertNotNull(loaded.getNoonResult());
        assertEquals(
                "skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=2",
                loaded.getNoonResult().getSteps().get(1).getExternalReference()
        );
    }

    @Test
    void confirmedRealRunPersistsAdapterFailureCategoryAndMessage() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(failureResult());
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

        assertEquals(1, adapter.callCount());
        assertEquals("failed", realRun.getStatus());
        assertEquals("noon_api", realRun.getFailureCategory());
        assertEquals("noon_validation_failed", realRun.getFailureCode());
        assertEquals("Noon rejected the product payload.", realRun.getFailureMessage());
        assertTrue(mapper.updatedTask().getNoonResultJson().contains("noon_validation_failed"));
    }

    @Test
    void confirmedRealRunRejectsSecondWriteAttemptForSameDryRun() {
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
        ProductListingTaskView firstRealRun = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        ProductListingTaskView secondRealRun = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("succeeded", firstRealRun.getStatus());
        assertEquals("rejected", secondRealRun.getStatus());
        assertEquals("real_run_already_attempted", secondRealRun.getFailureCode());
        assertEquals(1, adapter.callCount());
    }

    private ProductListingNoonWriteResult successResult() {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("create_product");
        step.setStatus("succeeded");
        step.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1");
        ProductListingNoonWriteStepResult readBack = new ProductListingNoonWriteStepResult();
        readBack.setStepKey("verify_noon_readback");
        readBack.setStatus("succeeded");
        readBack.setExternalReference("skuParent=ZPARENT;pskuCode=PSKU_CODE_1;readBackAttempts=2");
        return ProductListingNoonWriteResult.succeeded(List.of(step, readBack));
    }

    private ProductListingNoonWriteResult failureResult() {
        ProductListingNoonWriteStepResult step = new ProductListingNoonWriteStepResult();
        step.setStepKey("create_product");
        step.setStatus("failed");
        step.setFailureCode("noon_validation_failed");
        step.setFailureMessage("Noon rejected the product payload.");
        return ProductListingNoonWriteResult.failed(
                "noon_api",
                "noon_validation_failed",
                "Noon rejected the product payload.",
                List.of(step)
        );
    }
}
