package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductProjectionPersistenceService.ProductMasterSeed;
import com.nuono.next.product.ProductProjectionPersistenceService.SiteSeed;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Dp04ExistingScopeProjectionAdapterTest {
    private ProductManagementMapper productManagementMapper;
    private Dp04ProjectionSchemaPreflight schemaPreflight;
    private ProductProjectionPersistenceService persistenceService;
    private Dp04ExistingScopeProjectionAdapter adapter;

    @BeforeEach
    void setUp() {
        productManagementMapper = mock(ProductManagementMapper.class);
        schemaPreflight = mock(Dp04ProjectionSchemaPreflight.class);
        persistenceService = mock(ProductProjectionPersistenceService.class);
        adapter = new Dp04ExistingScopeProjectionAdapter(
                productManagementMapper,
                schemaPreflight,
                persistenceService
        );
    }

    @Test
    void emptySnapshotStillValidatesBoundScopeWithoutMutatingStoreConfiguration() {
        when(productManagementMapper.selectActiveBoundLogicalStoreSiteId(
                307L,
                50005L,
                "PRJ108065",
                "STR108065-NSA",
                "SA"
        )).thenReturn(51005L);

        adapter.persist(
                307L,
                50005L,
                "PRJ108065",
                "STR108065-NSA",
                List.of(siteSeed()),
                List.of(),
                new ArrayList<>(),
                true
        );

        verify(productManagementMapper).selectActiveBoundLogicalStoreSiteId(
                307L,
                50005L,
                "PRJ108065",
                "STR108065-NSA",
                "SA"
        );
        verify(persistenceService, never()).persistProductSeeds(
                eq(307L),
                eq(50005L),
                eq("PRJ108065"),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(),
                eq(true),
                eq(false)
        );
    }

    @Test
    void changedBoundStoreSiteFailsBeforeProductPersistence() {
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> adapter.persist(
                        307L,
                        50005L,
                        "PRJ108065",
                        "STR108065-NSA",
                        List.of(siteSeed()),
                        List.of(),
                        new ArrayList<>(),
                        true
                )
        );

        assertTrue(failure.getMessage().contains("scope changed"));
        verify(persistenceService, never()).persistProductSeeds(
                eq(307L),
                eq(50005L),
                eq("PRJ108065"),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList(),
                eq(true),
                eq(false)
        );
    }

    @Test
    void validScopeForwardsFactsWithTheExactResolvedSiteIndex() {
        when(productManagementMapper.selectActiveBoundLogicalStoreSiteId(
                307L,
                50005L,
                "PRJ108065",
                "STR108065-NSA",
                "SA"
        )).thenReturn(51005L);
        List<ProductMasterSeed> productSeeds = List.of(new ProductMasterSeed());
        List<String> warnings = new ArrayList<>();

        adapter.persist(
                307L,
                50005L,
                "PRJ108065",
                "STR108065-NSA",
                List.of(siteSeed()),
                productSeeds,
                warnings,
                false
        );

        verify(persistenceService).persistProductSeeds(
                eq(307L),
                eq(50005L),
                eq("PRJ108065"),
                eq(Map.of("STR108065-NSA", 51005L)),
                eq(Map.of("STR108065-NSA", "SA")),
                same(productSeeds),
                same(warnings),
                eq(false),
                eq(false)
        );
    }

    @Test
    void missingProjectionColumnFailsBeforeScopeLookup() {
        doThrow(new IllegalStateException(
                "DP-04 product projection schema is missing: product_site_offer.listing_started_at"
        )).when(schemaPreflight).requireReady();

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> adapter.persist(
                        307L,
                        50005L,
                        "PRJ108065",
                        "STR108065-NSA",
                        List.of(siteSeed()),
                        List.of(),
                        new ArrayList<>(),
                        true
                )
        );

        assertTrue(failure.getMessage().contains("schema is missing"));
        verify(productManagementMapper, never()).selectActiveBoundLogicalStoreSiteId(
                307L,
                50005L,
                "PRJ108065",
                "STR108065-NSA",
                "SA"
        );
    }

    private SiteSeed siteSeed() {
        return new SiteSeed("STR108065-NSA", "SA", "LOCAL_READY", true);
    }
}
