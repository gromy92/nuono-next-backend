package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListingRealRunDraftIdentityTest extends ProductListingRealRunServiceTest {
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
    void blockingDuplicatePskuDraftIsSavedWithoutBackfillingProductProjection() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public Long selectLocalProductIdByPartnerSku(
                            Long ownerUserId,
                            String storeCode,
                            String partnerSku,
                            Long excludeListingDraftId
                    ) {
                        return 51009L;
                    }
                };
        TrackingProjectionBackfill projectionBackfill = new TrackingProjectionBackfill();
        ProductListingService service = new ProductListingService(
                mapper,
                new ObjectMapper(),
                new ProductListingValidator(),
                new ProductListingRealWriteProperties(),
                new UnavailableProductListingNoonWriteAdapter(),
                null,
                objectProvider(projectionBackfill)
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );

        ProductListingDraftView saved = service.saveDraft(
                context,
                ProductListingTestFixtures.validCommand()
        );

        assertEquals("draft", saved.getStatus());
        assertTrue(saved.getValidationIssues().stream().anyMatch(issue ->
                "partner_sku_already_exists".equals(issue.getCode())));
        assertNotNull(mapper.selectDraftById(saved.getDraftId(), 10002L));
        assertEquals(0, projectionBackfill.draftBackfillCallCount);
    }

    @Test
    void firstSaveForOneSourceAcquiresDatabaseLockBeforeFindingActiveDraft() {
        List<String> calls = new ArrayList<>();
        Long[] activeDraftId = new Long[1];
        int[] inserts = new int[1];
        int[] updates = new int[1];
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper() {
                    @Override
                    public Integer acquireIdentityLock(String lockKey, int timeoutSeconds) {
                        calls.add("lock:" + lockKey);
                        return 1;
                    }

                    @Override
                    public Long findActiveDraftId(
                            Long ownerUserId,
                            String storeCode,
                            String sourceType,
                            Long sourceRefId
                    ) {
                        calls.add("find");
                        return activeDraftId[0];
                    }

                    @Override
                    public int insertDraft(ProductListingDraftRecord draft) {
                        inserts[0]++;
                        activeDraftId[0] = draft.getId();
                        return super.insertDraft(draft);
                    }

                    @Override
                    public int updateDraft(ProductListingDraftRecord draft) {
                        updates[0]++;
                        return super.updateDraft(draft);
                    }
                };
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                false,
                new UnavailableProductListingNoonWriteAdapter()
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingDraftCommand first = ProductListingTestFixtures.validCommand();
        first.setSourceType("manual_selection_group");
        first.setSourceRefId(91015L);
        ProductListingDraftCommand second = ProductListingTestFixtures.validCommand();
        second.setSourceType("manual_selection_group");
        second.setSourceRefId(91015L);

        ProductListingDraftView firstSaved = service.saveDraft(context, first);
        ProductListingDraftView secondSaved = service.saveDraft(context, second);

        assertEquals(firstSaved.getDraftId(), secondSaved.getDraftId());
        assertEquals(1, inserts[0]);
        assertEquals(1, updates[0]);
        assertEquals("lock:source:10002:STR245027-NAE:MANUAL_SELECTION_GROUP:91015", calls.get(0));
        assertEquals("find", calls.get(1));
        assertEquals("lock:source:10002:STR245027-NAE:MANUAL_SELECTION_GROUP:91015", calls.get(2));
        assertEquals("find", calls.get(3));
    }

    @Test
    void existingDraftCannotBeReboundToAnotherDraftsSource() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                false,
                new UnavailableProductListingNoonWriteAdapter()
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingDraftCommand sourceA =
                ProductListingTestFixtures.validCommand();
        sourceA.setSourceType("manual_selection_group");
        sourceA.setSourceRefId(91015L);
        ProductListingDraftView savedA = service.saveDraft(context, sourceA);
        ProductListingDraftCommand sourceB =
                ProductListingTestFixtures.validCommand();
        sourceB.setPsku("NN-TEST-PSKU-B");
        sourceB.setBarcode("6290000000002");
        sourceB.setSourceType("manual_selection_group");
        sourceB.setSourceRefId(91016L);
        ProductListingDraftView savedB = service.saveDraft(context, sourceB);
        ProductListingDraftCommand omitted =
                ProductListingTestFixtures.validCommand();
        omitted.setDraftId(savedB.getDraftId());
        omitted.setPsku("NN-TEST-PSKU-B");
        omitted.setBarcode("6290000000002");
        service.saveDraft(context, omitted);
        ProductListingDraftCommand rebound =
                ProductListingTestFixtures.validCommand();
        rebound.setDraftId(savedB.getDraftId());
        rebound.setPsku("NN-TEST-PSKU-B");
        rebound.setBarcode("6290000000002");
        rebound.setSourceType("manual_selection_group");
        rebound.setSourceRefId(91015L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(context, rebound)
        );
        ProductListingDraftRecord persistedA =
                mapper.selectDraftById(savedA.getDraftId(), 10002L);
        ProductListingDraftRecord persistedB =
                mapper.selectDraftById(savedB.getDraftId(), 10002L);
        assertEquals(91015L, persistedA.getSourceRefId());
        assertEquals(91016L, persistedB.getSourceRefId());
    }

    @Test
    void existingDraftWithoutSourceCannotBeAssignedOneDuringUpdate() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                false,
                new UnavailableProductListingNoonWriteAdapter()
        );
        BusinessAccessContext context = ProductListingTestFixtures.businessContext(
                10002L,
                90001L,
                "STR245027-NAE"
        );
        ProductListingDraftView saved = service.saveDraft(
                context,
                ProductListingTestFixtures.validCommand()
        );
        ProductListingDraftCommand rebound =
                ProductListingTestFixtures.validCommand();
        rebound.setDraftId(saved.getDraftId());
        rebound.setSourceType("manual_selection_group");
        rebound.setSourceRefId(91015L);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.saveDraft(context, rebound)
        );
        ProductListingDraftRecord persisted =
                mapper.selectDraftById(saved.getDraftId(), 10002L);
        assertNull(persisted.getSourceType());
        assertNull(persisted.getSourceRefId());
    }
}
