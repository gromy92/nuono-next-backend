package com.nuono.next.productlisting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
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
    private BusinessAccessResolver businessAccessResolver;

    private ProductListingController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductListingController(service, businessAccessResolver);
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
}
