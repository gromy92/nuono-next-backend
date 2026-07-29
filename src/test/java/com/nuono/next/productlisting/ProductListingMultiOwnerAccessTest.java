package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductKeywordMapper;
import com.nuono.next.infrastructure.mapper.ProductListingKeywordSuggestionMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.productkeyword.ProductKeywordNormalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProductListingMultiOwnerAccessTest extends ProductListingRealRunServiceTest {

    private static final String STORE_A = "STR245027-NAE";
    private static final String STORE_B = "STR108065-NSA";
    private static final Long OWNER_A = 10002L;
    private static final Long OWNER_B = 10003L;

    @Test
    void idOnlyDraftOperationsResolveTheOwnerMappedToTheDraftStore() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult())
        );
        BusinessAccessContext context = multiOwnerContext();
        ProductListingDraftCommand command = commandForStoreB();

        ProductListingDraftView saved = service.saveDraft(context, command);
        ProductListingDraftView loaded = service.loadDraft(context, saved.getDraftId());
        ProductListingDraftView validated =
                service.validateDraft(context, saved.getDraftId());

        assertEquals(OWNER_B, saved.getOwnerUserId());
        assertEquals(OWNER_B, loaded.getOwnerUserId());
        assertEquals(OWNER_B, validated.getOwnerUserId());
        assertEquals("ready_for_dry_run", validated.getStatus());
    }

    @Test
    void idOnlyTaskOperationsResolveTheOwnerMappedToTheTaskStore() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult())
        );
        BusinessAccessContext context = multiOwnerContext();
        ProductListingTaskView dryRun = dryRunForStoreB(service, context);

        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView loaded =
                service.loadTask(context, submitted.getTaskId());

        assertEquals(OWNER_B, submitted.getOwnerUserId());
        assertEquals(OWNER_B, loaded.getOwnerUserId());
        assertEquals("submitted", loaded.getStatus());
    }

    @Test
    void recoveryTaskLookupResolvesTheOwnerMappedToTheTaskStore() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingTestFixtures.TrackingNoonWriteAdapter adapter =
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(
                        readBackFailureAfterRemoteWriteResult(),
                        successReadBackStep()
                );
        ProductListingService service =
                ProductListingTestFixtures.service(mapper, true, adapter);
        BusinessAccessContext context = multiOwnerContext();
        ProductListingTaskView dryRun = dryRunForStoreB(service, context);
        ProductListingTaskView submitted = service.confirmRealRun(
                context,
                dryRun.getTaskId(),
                ProductListingTestFixtures.confirmedCommand()
        );
        ProductListingTaskView failed =
                service.executeSubmittedRealRunTask(submitted.getTaskId());

        ProductListingTaskView recovered =
                service.verifyRealRunReadBack(context, failed.getTaskId());

        assertEquals(OWNER_B, recovered.getOwnerUserId());
        assertEquals("succeeded", recovered.getStatus());
        assertEquals(1, adapter.verifyReadBackCallCount());
    }

    @Test
    void idOnlyLookupDoesNotCrossAStoreOwnerMappingThatExcludesTheRecordOwner() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService service = ProductListingTestFixtures.service(
                mapper,
                true,
                new ProductListingTestFixtures.TrackingNoonWriteAdapter(successResult())
        );
        ProductListingDraftView saved =
                service.saveDraft(multiOwnerContext(), commandForStoreB());
        BusinessAccessContext wrongOwnerForStore = context(
                OWNER_A,
                Map.of(STORE_A, OWNER_B, STORE_B, OWNER_A)
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.loadDraft(wrongOwnerForStore, saved.getDraftId())
        );

        assertEquals("Product listing draft not found.", error.getMessage());
    }

    @Test
    void keywordSuggestionsUseTheOwnerMappedToTheDraftStore() {
        ProductListingTestFixtures.FakeProductListingMapper mapper =
                new ProductListingTestFixtures.FakeProductListingMapper();
        ProductListingService listingService =
                ProductListingTestFixtures.service(
                        mapper,
                        true,
                        new ProductListingTestFixtures
                                .TrackingNoonWriteAdapter(successResult())
                );
        BusinessAccessContext context = multiOwnerContext();
        ProductListingDraftView draft = listingService.saveDraft(
                context, commandForStoreB());
        ProductKeywordMapper keywordMapper =
                mock(ProductKeywordMapper.class);
        ProductListingKeywordSuggestionMapper suggestionMapper =
                mock(ProductListingKeywordSuggestionMapper.class);
        ProductListingKeywordSuggestionService keywordService =
                new ProductListingKeywordSuggestionService(
                        keywordMapper,
                        suggestionMapper,
                        new ProductKeywordNormalizer()
                );
        ProductListingDraftRecord productScopeDraft =
                mapper.selectDraftById(draft.getDraftId(), OWNER_B);
        when(suggestionMapper.listDraftSuggestionEvents(
                draft.getDraftId())).thenReturn(List.of());
        when(suggestionMapper.selectLatestDraftByProductScope(
                OWNER_B, STORE_B, "NN-TEST-PSKU"
        )).thenReturn(productScopeDraft);

        ProductListingKeywordSuggestionView forDraft =
                keywordService.listForDraft(context, draft);
        ProductListingKeywordSuggestionView forProduct =
                keywordService.latestForProductScope(
                        context, STORE_B, "NN-TEST-PSKU");

        assertEquals(draft.getDraftId(), forDraft.getDraftId());
        assertEquals(draft.getDraftId(), forProduct.getDraftId());
        verify(suggestionMapper).selectLatestDraftByProductScope(
                OWNER_B, STORE_B, "NN-TEST-PSKU");
    }

    private ProductListingTaskView dryRunForStoreB(
            ProductListingService service,
            BusinessAccessContext context
    ) {
        ProductListingDraftView draft =
                service.saveDraft(context, commandForStoreB());
        ProductListingDryRunSubmitCommand command =
                new ProductListingDryRunSubmitCommand();
        command.setDraftId(draft.getDraftId());
        command.setStoreCode(STORE_B);
        return service.submitDryRun(context, command);
    }

    private ProductListingDraftCommand commandForStoreB() {
        ProductListingDraftCommand command =
                ProductListingTestFixtures.validCommand();
        command.setStoreCode(STORE_B);
        return command;
    }

    private BusinessAccessContext multiOwnerContext() {
        return context(
                OWNER_A,
                Map.of(STORE_A, OWNER_A, STORE_B, OWNER_B)
        );
    }

    private BusinessAccessContext context(
            Long defaultOwnerUserId,
            Map<String, Long> storeOwnerUserIds
    ) {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(defaultOwnerUserId)
                .accountType(BusinessAccountType.OPERATOR)
                .roleId(3L)
                .roleLevel(2)
                .roleName("purchase")
                .storeCodes(Set.of(STORE_A, STORE_B))
                .storeOwnerUserIds(storeOwnerUserIds)
                .menuPaths(Set.of("/purchase/listing", "/api/product-listing"))
                .build();
    }
}
