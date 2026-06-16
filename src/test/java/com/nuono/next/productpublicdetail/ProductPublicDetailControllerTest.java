package com.nuono.next.productpublicdetail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class ProductPublicDetailControllerTest {
    @Mock
    private ObjectProvider<ProductPublicDetailSyncService> serviceProvider;

    @Mock
    private ProductPublicDetailSyncService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private ProductPublicDetailController controller;

    @BeforeEach
    void setUp() {
        controller = new ProductPublicDetailController(serviceProvider, businessAccessResolver);
    }

    @Test
    void submitSyncTaskRequiresProductMasterStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ProductPublicDetailSyncTaskRequest command = new ProductPublicDetailSyncTaskRequest();
        command.setStoreCode("canman");
        command.setSiteCode("sa");
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(901L)
                .businessOwnerUserId(501L)
                .storeCodes(java.util.Set.of("CANMAN"))
                .storeOwnerUserIds(Map.of("CANMAN", 501L))
                .build();
        ProductPublicDetailTaskView expected = new ProductPublicDetailTaskView();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "canman")).thenReturn(context);
        when(service.submitManual(context, "canman", "sa")).thenReturn(expected);

        ProductPublicDetailTaskView result = controller.submitSyncTask(request, command);

        assertEquals(expected, result);
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.PRODUCT_MASTER, "canman");
    }
}
