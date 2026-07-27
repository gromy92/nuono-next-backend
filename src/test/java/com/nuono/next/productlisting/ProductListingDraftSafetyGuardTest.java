package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProductListingDraftSafetyGuardTest {

    @Test
    void productRebuildCannotCreateANewDryRunWhileDraftHasUnresolvedRealRun() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null);
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = context();
        ProductListingDraftView draft = service.saveDraft(context, rebuildCommand());
        ProductListingTaskView dryRun = submitDryRun(service, context, draft.getDraftId());
        service.confirmRealRun(
                context, dryRun.getTaskId(), ProductListingTestFixtures.confirmedCommand());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> submitDryRun(service, context, draft.getDraftId())
        );

        assertTrue(error.getMessage().contains("未决真实上架任务"));
        assertEquals(0, adapter.callCount());
    }

    @Test
    void productRebuildCannotConfirmDifferentDryRunWhileDraftHasUnresolvedRealRun() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null);
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = context();
        ProductListingDraftView draft = service.saveDraft(context, rebuildCommand());
        ProductListingTaskView firstDryRun =
                submitDryRun(service, context, draft.getDraftId());
        ProductListingTaskView secondDryRun =
                submitDryRun(service, context, draft.getDraftId());
        ProductListingTaskView firstRealRun = service.confirmRealRun(
                context,
                firstDryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.confirmRealRun(
                        context,
                        secondDryRun.getTaskId(),
                        ProductListingTestFixtures.confirmedCommand()
                )
        );

        assertEquals("submitted", firstRealRun.getStatus());
        assertTrue(error.getMessage().contains("另一个未决真实上架任务"));
        assertEquals(0, adapter.callCount());
    }

    @Test
    void savingExistingDraftUsesOwnerScopedDraftLock() {
        AtomicInteger lockCount = new AtomicInteger();
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public ProductListingDraftRecord selectDraftByIdForUpdate(
                            Long draftId,
                            Long ownerUserId
                    ) {
                        lockCount.incrementAndGet();
                        return super.selectDraftByIdForUpdate(draftId, ownerUserId);
                    }
                };
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null)
        );
        BusinessAccessContext context = context();
        ProductListingDraftView draft =
                service.saveDraft(context, ProductListingTestFixtures.validCommand());
        ProductListingDraftCommand changed = ProductListingTestFixtures.validCommand();
        changed.setDraftId(draft.getDraftId());
        changed.setProductTitleEn("Changed title");

        service.saveDraft(context, changed);

        assertEquals(1, lockCount.get());
    }

    @Test
    void validatedDryRunRequiresExplicitReopenBeforeDraftCanBeSaved() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null)
        );
        BusinessAccessContext context = context();
        ProductListingDraftView draft =
                service.saveDraft(context, ProductListingTestFixtures.validCommand());
        submitDryRun(service, context, draft.getDraftId());
        ProductListingDraftCommand changed = ProductListingTestFixtures.validCommand();
        changed.setDraftId(draft.getDraftId());
        changed.setProductTitleEn("Changed without reopening review");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(context, changed)
        );

        assertTrue(error.getMessage().contains("返回修改"));
    }

    @Test
    void confirmationUsesDraftSnapshotReadUnderTheDraftLock() {
        ProductListingDraftRecord[] lockedDraft = new ProductListingDraftRecord[1];
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public ProductListingDraftRecord selectDraftByIdForUpdate(
                            Long draftId,
                            Long ownerUserId
                    ) {
                        return lockedDraft[0] == null
                                ? super.selectDraftByIdForUpdate(draftId, ownerUserId)
                                : lockedDraft[0];
                    }
                };
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(null);
        ProductListingService service = ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = context();
        ProductListingDraftView draft =
                service.saveDraft(context, ProductListingTestFixtures.validCommand());
        ProductListingTaskView dryRun = submitDryRun(service, context, draft.getDraftId());
        ProductListingDraftRecord stored =
                mapper.selectDraftById(draft.getDraftId(), 10002L);
        lockedDraft[0] = changedDraft(stored);

        ProductListingTaskView result = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );

        assertEquals("rejected", result.getStatus());
        assertEquals("dry_run_stale", result.getFailureCode());
        assertEquals(0, adapter.callCount());
    }

    private ProductListingTaskView submitDryRun(
            ProductListingService service,
            BusinessAccessContext context,
            Long draftId
    ) {
        ProductListingDryRunSubmitCommand command = new ProductListingDryRunSubmitCommand();
        command.setDraftId(draftId);
        command.setStoreCode("STR245027-NAE");
        return service.submitDryRun(context, command);
    }

    private ProductListingDraftCommand rebuildCommand() {
        ProductListingDraftCommand command = ProductListingTestFixtures.validCommand();
        command.setSourceType("PRODUCT_REBUILD");
        command.setSourceRefId(31001L);
        command.setRebuildSourceProductMasterId(31001L);
        return command;
    }

    private ProductListingDraftRecord changedDraft(ProductListingDraftRecord stored) {
        ProductListingDraftRecord changed = new ProductListingDraftRecord();
        changed.setId(stored.getId());
        changed.setOwnerUserId(stored.getOwnerUserId());
        changed.setStoreCode(stored.getStoreCode());
        changed.setStatus(stored.getStatus());
        changed.setDraftJson("{\"psku\":\"NN-CHANGED-PSKU\"}");
        return changed;
    }

    private BusinessAccessContext context() {
        return ProductListingTestFixtures.businessContext(
                10002L, 90001L, "STR245027-NAE");
    }
}
