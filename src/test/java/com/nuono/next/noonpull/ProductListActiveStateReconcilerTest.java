package com.nuono.next.noonpull;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.nuono.next.infrastructure.mapper.ProductManagementMapper;
import com.nuono.next.product.ProductProjectionPersistenceService.ProductMasterSeed;
import com.nuono.next.product.ProductProjectionPersistenceService.SiteSeed;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductListActiveStateReconcilerTest {

    @Test
    void completeListMarksOnlyProductsMissingFromExactStoreSiteInactive() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductListActiveStateReconciler reconciler = new ProductListActiveStateReconciler(mapper);
        NoonProductProjectionWriteCommand command = completeCommand(product("sku-a"), product("SKU-B"));

        reconciler.reconcile(command);

        verify(mapper).markProductOffersMissingFromCompleteListInactive(
                eq(307L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq(List.of("SKU-A", "SKU-B")),
                eq(ProductListActiveStateReconciler.ABSENCE_STATE_SOURCE),
                any(),
                eq(307L)
        );
    }

    @Test
    void completeListUpdatesPresentProductStateBeforeMarkingAbsentProductsInactive() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductListActiveStateReconciler reconciler = new ProductListActiveStateReconciler(mapper);
        ProductMasterSeed active = product("PAPERSAYSB261");
        active.setIsActive(true);
        active.setActiveStateSource("NOON_PRODUCT_LIST_STATUS_CODE");
        active.setStatusCode("ACTIVE");
        NoonProductProjectionWriteCommand command = completeCommand(active);

        reconciler.reconcile(command);

        verify(mapper).updateProductOfferActiveStateFromCompleteList(
                eq(307L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("PAPERSAYSB261"),
                eq(true),
                eq("NOON_PRODUCT_LIST_STATUS_CODE"),
                any(),
                eq("ACTIVE"),
                eq(null),
                eq(307L)
        );
    }

    @Test
    void incompleteListNeverMarksMissingProductsInactive() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductListActiveStateReconciler reconciler = new ProductListActiveStateReconciler(mapper);
        NoonProductProjectionWriteCommand command = completeCommand(product("SKU-A"));
        command.setCompleteProductScope(false);

        reconciler.reconcile(command);

        verify(mapper, never()).markProductOffersMissingFromCompleteListInactive(
                any(), any(), any(), any(), any(), any(), any()
        );
        verify(mapper, never()).updateProductOfferActiveStateFromCompleteList(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void missingPartnerSkuFailsClosedEvenIfCallerClaimsListIsComplete() {
        ProductManagementMapper mapper = mock(ProductManagementMapper.class);
        ProductListActiveStateReconciler reconciler = new ProductListActiveStateReconciler(mapper);
        NoonProductProjectionWriteCommand command = completeCommand(product(null));

        reconciler.reconcile(command);

        verify(mapper, never()).markProductOffersMissingFromCompleteListInactive(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    private NoonProductProjectionWriteCommand completeCommand(ProductMasterSeed... products) {
        NoonProductProjectionWriteCommand command = new NoonProductProjectionWriteCommand();
        command.setOwnerUserId(307L);
        command.setReferenceStoreCode("STR108065-NSA");
        command.setSiteSeeds(List.of(new SiteSeed("STR108065-NSA", "SA", "LOCAL_READY", true)));
        command.setProductSeeds(List.of(products));
        command.setCompleteProductScope(true);
        return command;
    }

    private ProductMasterSeed product(String partnerSku) {
        ProductMasterSeed seed = new ProductMasterSeed();
        seed.setPartnerSku(partnerSku);
        return seed;
    }
}
