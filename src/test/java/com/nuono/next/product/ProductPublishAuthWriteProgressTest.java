package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nuono.next.infrastructure.mapper.ProductGroupMapper;
import com.nuono.next.product.noon.NoonProductGateway;
import com.nuono.next.product.noon.ProductNoonAdapter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ProductPublishAuthWriteProgressTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void groupPublishingCannotFallBackToAnUnguardedRawSession() {
        assertThrows(
                NullPointerException.class,
                () -> new ProductGroupPublishService(mock(ProductGroupMapper.class), objectMapper, null)
        );
    }

    @Test
    void priceSuccessThenWarrantyAuthShouldRecordPriorWrite() {
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        ProductPublishOfferWriter writer = new ProductPublishOfferWriter(objectMapper, adapter);
        Map<String, Object> baseline = offer("37.00", 0);
        Map<String, Object> draft = offer("38.00", 3);
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.OFFER_MGMT_PRICE_UPSERT_URL),
                any(), eq(false), any()
        )).thenReturn(objectMapper.createObjectNode());
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.OFFER_MGMT_ID_WARRANTY_UPSERT_URL),
                any(), eq(false), any()
        )).thenThrow(authFailure());

        ProductWriteAuthRequiredException failure = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> writer.publishOffer(null, "PSKU-1", draft, baseline, new ArrayList<>())
        );

        assertTrue(failure.isWriteMayHaveOccurred());
    }

    @Test
    void englishSuccessThenArabicAuthShouldRecordPriorWrite() {
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        ProductPublishSharedZskuWriter writer = new ProductPublishSharedZskuWriter(
                objectMapper,
                adapter,
                new ProductPublishChangedDomainComparator(objectMapper)
        );
        ProductMasterSnapshotView baseline = snapshot("Old English", "قديم");
        ProductMasterSnapshotView draft = snapshot("New English", "جديد");
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.ZSKU_UPSERT_URL), any(), eq(true)
        )).thenReturn(objectMapper.createObjectNode()).thenThrow(authFailure());

        ProductWriteAuthRequiredException failure = assertThrows(
                ProductWriteAuthRequiredException.class,
                () -> writer.publishSharedAttributes(
                        null,
                        draft,
                        baseline,
                        baseline,
                        new ProductPublishUnsupportedChanges(),
                        new ArrayList<>()
                )
        );

        assertTrue(failure.isWriteMayHaveOccurred());
    }

    @Test
    void groupUpsertSuccessThenCacheAuthShouldRemainDetectableAsPartialWrite() {
        ProductGroupMapper mapper = mock(ProductGroupMapper.class);
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        ProductGroupPublishService service = new ProductGroupPublishService(mapper, objectMapper, adapter);
        ProductGroupMemberGuardRecord guard = new ProductGroupMemberGuardRecord();
        guard.setSkuParent("Z2");
        when(mapper.selectGroupMemberGuardBySkuParent(307L, "STORE", "Z2")).thenReturn(guard);
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.GROUP_UPSERT_URL), any(), eq(true)
        )).thenReturn(objectMapper.createObjectNode());
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.CATPLAT_SKU_CACHE_URL), any(), eq(true)
        )).thenThrow(authFailure());

        ProductGroupPartialPublishException failure = assertThrows(
                ProductGroupPartialPublishException.class,
                () -> service.publishGroupChanges(
                        null,
                        groupedSnapshot(List.of("Z1", "Z2")),
                        groupedSnapshot(List.of("Z1")),
                        307L,
                        "STORE"
                )
        );

        assertNotNull(ProductWriteAuthRequiredException.find(failure));
    }

    @Test
    void firstGroupWriteHttp503ShouldBeOutcomeUnknownInsteadOfRetryableWholeTask() {
        ProductGroupMapper mapper = mock(ProductGroupMapper.class);
        ProductNoonAdapter adapter = mock(ProductNoonAdapter.class);
        ProductGroupPublishService service = new ProductGroupPublishService(mapper, objectMapper, adapter);
        ProductGroupMemberGuardRecord guard = new ProductGroupMemberGuardRecord();
        guard.setSkuParent("Z2");
        when(mapper.selectGroupMemberGuardBySkuParent(307L, "STORE", "Z2")).thenReturn(guard);
        when(adapter.postWriteJson(
                any(), eq(NoonProductGateway.GROUP_UPSERT_URL), any(), eq(true)
        )).thenThrow(new IllegalStateException("HTTP 503"));

        assertThrows(
                ProductGroupPartialPublishException.class,
                () -> service.publishGroupChanges(
                        null,
                        groupedSnapshot(List.of("Z1", "Z2")),
                        groupedSnapshot(List.of("Z1")),
                        307L,
                        "STORE"
                )
        );
    }

    private Map<String, Object> offer(String price, int warranty) {
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", "STR1-NSA");
        offer.put("site", "SA");
        offer.put("partnerSku", "P1");
        offer.put("price", price);
        offer.put("idWarranty", warranty);
        return offer;
    }

    private ProductMasterSnapshotView snapshot(String titleEn, String titleAr) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "Z1");
        snapshot.getIdentity().put("brand", "Brand");
        snapshot.getTaxonomy().put("productFulltype", "family-type-subtype");
        snapshot.getContent().put("titleEn", titleEn);
        snapshot.getContent().put("titleAr", titleAr);
        snapshot.getContent().put("descriptionEn", "English description");
        snapshot.getContent().put("descriptionAr", "Arabic description");
        snapshot.getContent().put("highlightsEn", List.of("English bullet"));
        snapshot.getContent().put("highlightsAr", List.of("Arabic bullet"));
        snapshot.getContent().put("images", List.of("https://image.example/1.jpg"));
        return snapshot;
    }

    private ProductMasterSnapshotView groupedSnapshot(List<String> skuParents) {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getIdentity().put("skuParent", "Z1");
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("skuGroup", "G1");
        group.put("groupRef", "R1");
        group.put("members", skuParents.stream()
                .map(code -> Map.<String, Object>of("skuParent", code))
                .collect(Collectors.toList()));
        snapshot.setGroup(group);
        return snapshot;
    }

    private ProductWriteAuthRequiredException authFailure() {
        return new ProductWriteAuthRequiredException(
                991L,
                false,
                "Noon Project 授权恢复中。",
                new IllegalStateException("auth_required")
        );
    }
}
