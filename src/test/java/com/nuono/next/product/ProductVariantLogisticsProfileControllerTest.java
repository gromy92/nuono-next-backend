package com.nuono.next.product;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;

@ExtendWith(MockitoExtension.class)
class ProductVariantLogisticsProfileControllerTest {

    @Mock
    private ObjectProvider<ProductVariantLogisticsProfileService> serviceProvider;
    @Mock
    private ObjectProvider<BusinessAccessResolver> businessAccessResolverProvider;
    @Mock
    private ProductVariantLogisticsProfileService service;
    @Mock
    private BusinessAccessResolver businessAccessResolver;
    @Mock
    private AuthSessionTokenService sessionTokenService;

    private ProductVariantLogisticsProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductVariantLogisticsProfileController(
                serviceProvider,
                sessionTokenService,
                businessAccessResolverProvider
        );
    }

    @Test
    void exposesByPskuRoutesAlongsideLegacyVariantRoute() throws Exception {
        assertArrayEquals(
                new String[]{"/by-psku"},
                ProductVariantLogisticsProfileController.class
                        .getMethod("detailByPsku", Long.class, String.class, String.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(GetMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/{variantId}"},
                ProductVariantLogisticsProfileController.class
                        .getMethod("save", Long.class, ProductVariantLogisticsProfileCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
        assertArrayEquals(
                new String[]{"/by-psku"},
                ProductVariantLogisticsProfileController.class
                        .getMethod("saveByPsku", ProductVariantLogisticsProfileCommand.class, javax.servlet.http.HttpServletRequest.class)
                        .getAnnotation(PutMapping.class)
                        .value()
        );
    }

    @Test
    void saveByPskuResolvesOwnerAndOperatorBeforeCallingService() {
        MockHttpServletRequest request = requestFor(new AuthenticatedSession(10003L, 3L, 2));
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(businessAccessResolverProvider.getIfAvailable()).thenReturn(businessAccessResolver);
        when(businessAccessResolver.requireStoreAccess(any(), any(), org.mockito.ArgumentMatchers.eq("STR69486-NSA")))
                .thenReturn(accessContext());
        when(service.saveByPsku(any())).thenReturn(new ProductVariantLogisticsProfileView());
        ProductVariantLogisticsProfileCommand command = new ProductVariantLogisticsProfileCommand();
        command.storeCode = "STR69486-NSA";
        command.partnerSku = "SGGRB113";

        controller.saveByPsku(command, request);

        ArgumentCaptor<ProductVariantLogisticsProfileCommand> captor =
                ArgumentCaptor.forClass(ProductVariantLogisticsProfileCommand.class);
        verify(service).saveByPsku(captor.capture());
        assertEquals(10002L, captor.getValue().ownerUserId);
        assertEquals(10003L, captor.getValue().operatorUserId);
        assertEquals("SGGRB113", captor.getValue().partnerSku);
    }

    private MockHttpServletRequest requestFor(AuthenticatedSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(session);
        return request;
    }

    private BusinessAccessContext accessContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeOwnerUserIds(Map.of("STR69486-NSA", 10002L))
                .build();
    }
}
