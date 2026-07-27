package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ProductListingContinuationEligibilityTest {
    @Test
    void interruptedTaskWithoutDurableCreateEvidenceCannotLookupOrWriteByPsku() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null);
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

        assertThrows(
                IllegalArgumentException.class,
                () -> service.continueRealRunAfterCreate(context, submitted.getTaskId())
        );
        ProductListingTaskView duplicate = service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("rejected", duplicate.getStatus());
        assertEquals("real_run_already_attempted", duplicate.getFailureCode());
        assertEquals(0, adapter.resolveCreateReferenceCallCount());
        assertEquals(0, adapter.continueAfterCreateCallCount());
    }
}
