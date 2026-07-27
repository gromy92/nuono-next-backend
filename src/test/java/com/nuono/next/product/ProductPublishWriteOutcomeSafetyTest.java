package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.StoreSyncMapper;
import com.nuono.next.noon.NoonHttpException;
import com.nuono.next.noon.NoonSessionGateway.NoonSession;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import com.nuono.next.store.StoreSyncOwnerContext;
import com.nuono.next.store.StoreSyncStoreRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductPublishWriteOutcomeSafetyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void firstOfferWriteTransportFailureShouldBeOutcomeUnknown() {
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.OFFER_MGMT_PRICE_UPSERT_URL),
                any(), eq(false), any()
        )).thenThrow(new IllegalStateException("HTTP 503"));

        ProductPublishWriteOutcomeUnknownException failure = assertThrows(
                ProductPublishWriteOutcomeUnknownException.class,
                () -> new ProductPublishOfferWriter(objectMapper, adapter).publishOffer(
                        null, "PSKU-1", offer("38.00", "AE"), offer("37.00", "AE"), new ArrayList<>()
                )
        );

        assertTrue(failure.isWriteMayHaveOccurred());
        assertFalse(failure.isPriorWriteCompleted());
    }

    @Test
    void deterministicFirstWriteRejectionShouldRemainOrdinaryFailure() {
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        NoonHttpException rejection = new NoonHttpException(400, "invalid payload", "/offer/upsert");
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.OFFER_MGMT_PRICE_UPSERT_URL),
                any(), eq(false), any()
        )).thenThrow(rejection);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ProductPublishOfferWriter(objectMapper, adapter).publishOffer(
                        null, "PSKU-1", offer("38.00", "AE"), offer("37.00", "AE"), new ArrayList<>()
                )
        );

        assertTrue(failure == rejection);
        assertFalse(failure instanceof ProductPublishWriteOutcomeUnknownException);
    }

    @Test
    void firstSharedWriteTransportFailureShouldBeOutcomeUnknown() {
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.ZSKU_UPSERT_URL), any(), eq(true)
        )).thenThrow(new IllegalStateException("unexpected EOF"));

        ProductPublishWriteOutcomeUnknownException failure = assertThrows(
                ProductPublishWriteOutcomeUnknownException.class,
                () -> new ProductPublishSharedZskuWriter(
                        objectMapper, adapter, new ProductPublishChangedDomainComparator(objectMapper)
                ).publishSharedAttributes(
                        null,
                        snapshot("New title"),
                        snapshot("Old title"),
                        snapshot("Old title"),
                        new ProductPublishUnsupportedChanges(),
                        new ArrayList<>()
                )
        );

        assertTrue(failure.isWriteMayHaveOccurred());
        assertFalse(failure.isPriorWriteCompleted());
    }

    @Test
    void secondSiteFailureShouldPreserveFirstSiteWriteAndLoginFailureShouldStayPreWrite() {
        StoreSyncMapper storeSyncMapper = mock(StoreSyncMapper.class);
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        ProductPublishWriteService.WriteOperations operations =
                mock(ProductPublishWriteService.WriteOperations.class);
        ProductPublishWriteService service = new ProductPublishWriteService(
                storeSyncMapper, adapter, mock(ProductGroupPublishService.class), operations
        );
        StoreSyncOwnerContext owner = owner();
        StoreSyncStoreRecord store = store();
        ProductMasterSnapshotView draft = new ProductMasterSnapshotView();
        draft.setSiteOffers(List.of(offer("38.00", "AE"), offer("42.00", "SA")));
        ProductMasterSnapshotView baseline = new ProductMasterSnapshotView();
        baseline.setSiteOffers(List.of(offer("37.00", "AE"), offer("41.00", "SA")));
        Map<String, Map<String, Object>> baselineOffers = new LinkedHashMap<>();
        baselineOffers.put("AE", baseline.getSiteOffers().get(0));
        baselineOffers.put("SA", baseline.getSiteOffers().get(1));
        when(storeSyncMapper.selectOwnerContext(307L)).thenReturn(owner);
        when(operations.resolveProjectCode(any(), anyString(), eq(store), any())).thenReturn("PRJ-1");
        when(operations.withProjectAndStore(any(), anyString(), anyString())).thenReturn(null);
        when(operations.withStore(any(), anyString())).thenReturn(null);
        when(operations.sharedZskuChanged(draft, baseline)).thenReturn(false);
        when(operations.groupChanged(draft, baseline)).thenReturn(false);
        when(operations.targetOffers(draft, "AE")).thenReturn(draft.getSiteOffers());
        when(operations.baselineOffers(baseline)).thenReturn(baselineOffers);
        when(operations.siteOfferChanged(any(), any())).thenReturn(true);
        doNothing().doThrow(new IllegalStateException("deterministic second-site failure"))
                .when(operations).publishOffer(any(), anyString(), any(), any(), any());

        ProductPublishWriteOutcomeUnknownException failure = assertThrows(
                ProductPublishWriteOutcomeUnknownException.class,
                () -> service.publishSupportedChanges(
                        command(), store, draft, baseline, baseline, "AE",
                        new ProductPublishUnsupportedChanges(), new ArrayList<>()
                )
        );
        assertTrue(failure.isPriorWriteCompleted());

        ProductNoonAdapter loginFailureAdapter = mock(ProductNoonAdapter.class);
        when(loginFailureAdapter.loginWithPersistedCookie(
                any(), anyString(), anyString(), anyString(), anyString()
        )).thenThrow(new IllegalStateException("connect timeout"));
        ProductPublishWriteService preWriteService = new ProductPublishWriteService(
                storeSyncMapper, loginFailureAdapter, mock(ProductGroupPublishService.class), operations
        );
        IllegalStateException preWriteFailure = assertThrows(
                IllegalStateException.class,
                () -> preWriteService.publishSupportedChanges(
                        command(), store, draft, baseline, baseline, "AE",
                        new ProductPublishUnsupportedChanges(), new ArrayList<>()
                )
        );
        assertFalse(preWriteFailure instanceof ProductPublishWriteOutcomeUnknownException);
    }

    private ProductMasterActionCommand command() {
        ProductMasterActionCommand command = new ProductMasterActionCommand();
        command.setOwnerUserId(307L);
        command.setStoreCode("STORE");
        return command;
    }

    private StoreSyncOwnerContext owner() {
        StoreSyncOwnerContext owner = new StoreSyncOwnerContext();
        owner.setId(307L);
        owner.setNoonPartnerProjectUser("user");
        return owner;
    }

    private StoreSyncStoreRecord store() {
        StoreSyncStoreRecord store = new StoreSyncStoreRecord();
        store.setStoreCode("STORE");
        store.setProjectCode("PRJ-1");
        store.setNoonPartnerProjectUser("user");
        store.setNoonPartnerCookie("cookie");
        return store;
    }

    private Map<String, Object> offer(String price, String site) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", site);
        offer.put("site", site);
        offer.put("partnerSku", "P-" + site);
        offer.put("pskuCode", "PSKU-" + site);
        offer.put("price", price);
        return offer;
    }

    private ProductMasterSnapshotView snapshot(String title) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "Z1");
        snapshot.getContent().put("titleEn", title);
        snapshot.getContent().put("titleAr", "عنوان");
        snapshot.getContent().put("descriptionEn", "English description");
        snapshot.getContent().put("descriptionAr", "وصف");
        snapshot.getContent().put("highlightsEn", List.of("English bullet"));
        snapshot.getContent().put("highlightsAr", List.of("نقطة"));
        snapshot.getContent().put("images", List.of("https://image.example/1.jpg"));
        return snapshot;
    }
}
