package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.permission.access.BusinessAccessContext;
import org.junit.jupiter.api.Test;

class ProductListingRebuildIdentitySafetyTest {

    @Test
    void currentRealRunCannotBeBypassedAsHistoricalByANewerRebuildDryRun() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null);
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context =
                ProductListingTestFixtures.businessContext(10002L, 90001L, "STR245027-NAE");
        ProductListingTaskView originalDryRun =
                ProductListingTestFixtures.validatedDryRun(service, context);
        ProductListingTaskView currentRealRun = service.confirmRealRun(
                context,
                originalDryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingDraftCommand rebuild = ProductListingTestFixtures.validCommand();
        rebuild.setSourceType("PRODUCT_REBUILD");
        rebuild.setSourceRefId(64001L);
        rebuild.setRebuildSourceProductMasterId(64001L);
        rebuild.setInheritedListingStartedAt("2026-03-12 00:00:00");

        ProductListingRealRunSubmission submission = service.submitConfirmedRealRunFromDraft(
                context,
                rebuild,
                "confirmed by product rebuild after delete task 77001"
        );

        assertEquals("submitted", currentRealRun.getStatus());
        assertEquals("rejected", submission.getRealRun().getStatus());
        assertEquals("partner_sku_already_exists", submission.getRealRun().getFailureCode());
        assertEquals(0, adapter.callCount());
    }
}
