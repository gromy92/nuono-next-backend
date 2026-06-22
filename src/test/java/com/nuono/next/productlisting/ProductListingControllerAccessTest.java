package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ProductListingControllerAccessTest {

    @Mock
    private ProductListingService service;

    @Mock
    private ProductListingWarehouseProvider warehouseProvider;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductListingController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductListingController(service, warehouseProvider, businessAccessResolver);
    }

    @Test
    void saveDraftRejectsStoreOutsideSessionScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProductListingDraftCommand command = new ProductListingDraftCommand();
        command.setStoreCode("STR245027-NAE");
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_LISTING,
                "STR245027-NAE"
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "store forbidden"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.saveDraft(command, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(service, never()).saveDraft(any(), any());
    }

    @Test
    void validateDraftMapsServiceStoreScopeRejectionToForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(90002L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR245027-NSA"))
                .storeOwnerUserIds(Map.of("STR245027-NSA", 10002L))
                .menuPaths(Set.of("/purchase/listing"))
                .build();
        when(businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.PRODUCT_LISTING
        )).thenReturn(context);
        when(service.validateDraft(context, 10001L))
                .thenThrow(new BusinessAccessDeniedException("当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.validateDraft(10001L, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void confirmRealRunUsesBusinessContextAndMapsStoreScopeRejectionToForbidden() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProductListingRealRunCommand command = new ProductListingRealRunCommand();
        command.setConfirmRealNoonWrite(true);
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(90002L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR245027-NSA"))
                .storeOwnerUserIds(Map.of("STR245027-NSA", 10002L))
                .menuPaths(Set.of("/purchase/listing"))
                .build();
        when(businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.PRODUCT_LISTING
        )).thenReturn(context);
        when(service.confirmRealRun(context, 20001L, command))
                .thenThrow(new BusinessAccessDeniedException("当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.confirmRealRun(20001L, command, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void warehousesRequiresStoreAccessAndUsesStoreOwnerFromSessionScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(90002L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/purchase/listing"))
                .build();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.PRODUCT_LISTING,
                "STR108065-NSA"
        )).thenReturn(context);
        when(warehouseProvider.listWarehouses(307L, "STR108065-NSA"))
                .thenReturn(List.of(new ProductListingWarehouseView()));

        assertEquals(1, controller.warehouses("STR108065-NSA", request).size());
        verify(warehouseProvider).listWarehouses(307L, "STR108065-NSA");
    }
}
