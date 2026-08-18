package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseShippingBatchDiagnosticControllerTest {

    @Mock
    private ObjectProvider<OfficialWarehouseShippingBatchDiagnosticService> serviceProvider;
    @Mock
    private OfficialWarehouseShippingBatchDiagnosticService service;
    @Mock
    private BusinessAccessResolver accessResolver;
    @Mock
    private HttpServletRequest request;

    @Test
    void resolvesStoreScopeBeforeReturningTheBusinessReason() {
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeOwnerUserIds(Map.of("STR69486-NSA", 307L))
                .build();
        OfficialWarehouseShippingBatchDiagnosticView expected = new OfficialWarehouseShippingBatchDiagnosticView();
        expected.code = "NO_PRODUCT_DETAILS";
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireStoreAccess(
                request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR69486-NSA"
        )).thenReturn(access);
        when(service.diagnose(access, "STR69486-NSA", "SA", "ZDAIR8111341")).thenReturn(expected);

        OfficialWarehouseShippingBatchDiagnosticController controller =
                new OfficialWarehouseShippingBatchDiagnosticController(serviceProvider, accessResolver);
        OfficialWarehouseShippingBatchDiagnosticView actual = controller.diagnose(
                "STR69486-NSA", "SA", "ZDAIR8111341", request
        );

        assertThat(actual.code).isEqualTo("NO_PRODUCT_DETAILS");
        verify(service).diagnose(access, "STR69486-NSA", "SA", "ZDAIR8111341");
    }
}
