package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingNoRealNoonWriteGuardTest {

    @Test
    void draftValidateAndDryRunPathsDoNotInvokeNoonWriteAdapter() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(ProductListingNoonWriteResult.succeeded(List.of()));
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );

        ProductListingDraftView draft = service.saveDraft(context, ProductListingTestFixtures.validCommand());
        service.validateDraft(context, draft.getDraftId());
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode("STR245027-NAE");
        service.submitDryRun(context, command);

        assertEquals(0, adapter.callCount());
    }

    @Test
    void disabledKillSwitchDoesNotInvokeNoonWriteAdapter() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(ProductListingNoonWriteResult.succeeded(List.of()));
        ProductListingService service = ProductListingTestFixtures.service(mapper, false, adapter);
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingTaskView dryRun = ProductListingTestFixtures.validatedDryRun(service, context);

        service.confirmRealRun(context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        assertEquals(0, adapter.callCount());
    }
}
