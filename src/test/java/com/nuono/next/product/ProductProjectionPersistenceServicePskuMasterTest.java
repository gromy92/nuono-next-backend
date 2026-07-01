package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.system.BootstrapProperties;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class ProductProjectionPersistenceServicePskuMasterTest {

    @Mock
    private ProductManagementMapper productManagementMapper;

    @Mock
    private CoreTableStatusMapper coreTableStatusMapper;

    private ProductProjectionPersistenceService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ProductProjectionPersistenceService(
                productManagementMapper,
                coreTableStatusMapper,
                new BootstrapProperties(),
                new ObjectMapper(),
                new ProductKeyContentHistoryAssembler(),
                null
        );
        when(coreTableStatusMapper.findExistingTableNames(eq("nuono_new_dev"), anyList()))
                .thenAnswer(invocation -> new ArrayList<>((List<String>) invocation.getArgument(1)));
    }

    @Test
    void initializationProjectionPersistsMasterAndSiteOfferByPskuBusinessKeys() {
        Long logicalStoreId = 50003L;
        ProductProjectionPersistenceService.ProductMasterSeed seed =
                new ProductProjectionPersistenceService.ProductMasterSeed();
        seed.setSkuParent("Z20152FFCAE5DA47AC88EZ");
        seed.setPartnerSku("SGGRB113");
        seed.setChildSku("SGGRB113-CHILD");
        seed.setPskuCode("external-noon-psku-code");
        seed.setOfferCode("offer-SA");
        seed.setReferenceStoreCode("STR69486-NSA");

        when(productManagementMapper.selectLogicalStoreId(307L, "PRJ69486")).thenReturn(logicalStoreId);
        when(productManagementMapper.selectLogicalStoreIdBySiteStoreCode("STR69486-NSA")).thenReturn(logicalStoreId);
        when(productManagementMapper.selectLogicalStoreSiteIdInLogicalStore(logicalStoreId, "STR69486-NSA"))
                .thenReturn(51003L);
        when(productManagementMapper.selectProductMasterIdByStorePartnerSku(logicalStoreId, "SGGRB113"))
                .thenReturn(null, 52001L);
        when(productManagementMapper.nextProductMasterId()).thenReturn(52001L);
        when(productManagementMapper.selectProductVariantIdByStorePartnerSku(logicalStoreId, "SGGRB113"))
                .thenReturn(null, 53001L);
        when(productManagementMapper.nextProductVariantId()).thenReturn(53001L);
        when(productManagementMapper.selectProductSiteOfferIdByStorePartnerSkuSite(logicalStoreId, "SGGRB113", "SA"))
                .thenReturn(null);
        when(productManagementMapper.nextProductSiteOfferId()).thenReturn(54001L);

        service.persistInitializationProjection(
                307L,
                "PRJ69486",
                "Songguoguo",
                "STR69486-NSA",
                List.of(new ProductProjectionPersistenceService.SiteSeed("STR69486-NSA", "SA", "ACTIVE", true)),
                List.of(seed),
                new ArrayList<>()
        );

        verify(productManagementMapper, times(2)).selectProductMasterIdByStorePartnerSku(logicalStoreId, "SGGRB113");
        verify(productManagementMapper).upsertProductMaster(
                eq(52001L),
                eq(logicalStoreId),
                eq("SGGRB113"),
                eq("Z20152FFCAE5DA47AC88EZ"),
                eq("Z20152FFCAE5DA47AC88EZ"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(307L)
        );
        verify(productManagementMapper).selectProductSiteOfferIdByStorePartnerSkuSite(
                logicalStoreId,
                "SGGRB113",
                "SA"
        );
        verify(productManagementMapper).upsertProductSiteOffer(
                eq(54001L),
                eq(52001L),
                eq(logicalStoreId),
                eq("SGGRB113"),
                eq(53001L),
                eq(51003L),
                eq("SA"),
                eq("external-noon-psku-code"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(307L)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotSiteCodeFallbackFeedsTheSameSiteSeedMapUsedForOfferPersistence() throws Exception {
        ProductMasterSnapshotView snapshot = new ProductMasterSnapshotView();
        snapshot.getStoreContext().put("storeCode", "STR69486-NSA");
        Map<String, Object> offer = new LinkedHashMap<>();
        offer.put("storeCode", "STR69486-NSA");
        offer.put("siteCode", "SA");
        offer.put("statusCode", "ACTIVE");
        snapshot.setSiteOffers(List.of(offer));

        Method buildSiteSeeds = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "buildSiteSeeds",
                ProductMasterSnapshotView.class
        );
        buildSiteSeeds.setAccessible(true);
        List<ProductProjectionPersistenceService.SiteSeed> seeds =
                (List<ProductProjectionPersistenceService.SiteSeed>) buildSiteSeeds.invoke(service, snapshot);

        Method siteCodeByStoreCode = ProductProjectionPersistenceService.class.getDeclaredMethod(
                "siteCodeByStoreCode",
                List.class
        );
        siteCodeByStoreCode.setAccessible(true);
        Map<String, String> siteCodes =
                (Map<String, String>) siteCodeByStoreCode.invoke(service, seeds);

        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0).getStoreCode()).isEqualTo("STR69486-NSA");
        assertThat(seeds.get(0).getSite()).isEqualTo("SA");
        assertThat(siteCodes).containsEntry("STR69486-NSA", "SA");
    }
}
