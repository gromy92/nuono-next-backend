package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.CoreTableStatusMapper;
import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductProjectionPersistenceService.ProductMasterSeed;
import com.nuono.next.product.ProductProjectionPersistenceService.SiteOfferSeed;
import com.nuono.next.system.BootstrapProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

class Dp04ListingTimeDerivationBoundaryTest {

    @Test
    void dp04KeepsExplicitListingTimeButNeverDerivesItFromSalesFacts() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        CoreTableStatusMapper tableStatus = mock(CoreTableStatusMapper.class);
        when(tableStatus.findExistingTableNames(eq("nuono_new_dev"), anyList()))
                .thenReturn(List.of());
        ProductProjectionPersistenceService service = new ProductProjectionPersistenceService(
                mapper,
                tableStatus,
                new BootstrapProperties(),
                new ObjectMapper(),
                new ProductKeyContentHistoryAssembler(),
                null
        );
        ProductMasterSeed seed = new ProductMasterSeed();
        seed.setSkuParent("Z-GOOD");
        seed.setPartnerSku("GOOD");
        seed.setReferenceStoreCode("STR108065-NSA");
        SiteOfferSeed offer = SiteOfferSeed.fromRepresentative(seed);
        offer.setListingStartedAt("2026-07-01 00:00:00");
        offer.setListingStartedSource("NOON_EXPLICIT");
        seed.addSiteOffer(offer);
        when(mapper.selectProductMasterIdByStorePartnerSku(8001L, "GOOD"))
                .thenReturn(null, 52001L);
        when(mapper.nextProductMasterId()).thenReturn(52001L);
        when(mapper.selectProductVariantIdByStorePartnerSku(8001L, "GOOD"))
                .thenReturn(null, 53001L);
        when(mapper.nextProductVariantId()).thenReturn(53001L);
        when(mapper.selectProductSiteOfferIdByStorePartnerSkuSite(8001L, "GOOD", "SA"))
                .thenReturn(null);
        when(mapper.nextProductSiteOfferId()).thenReturn(54001L);

        service.persistProductSeeds(
                307L,
                8001L,
                "PRJ108065",
                Map.of("STR108065-NSA", 8101L),
                Map.of("STR108065-NSA", "SA"),
                List.of(seed),
                new ArrayList<>(),
                true,
                false
        );

        Invocation upsert = mockingDetails(mapper).getInvocations().stream()
                .filter(invocation -> "upsertProductSiteOffer".equals(
                        invocation.getMethod().getName()
                ))
                .findFirst()
                .orElseThrow();
        assertThat(upsert.getArguments()[36])
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(upsert.getArguments()[37]).isEqualTo("NOON_EXPLICIT");
        verify(mapper, never()).backfillProductSiteOfferListingStartedAtById(
                eq(54001L),
                org.mockito.ArgumentMatchers.any(),
                eq(307L)
        );
    }
}
