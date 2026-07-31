package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.BatchProductSummaryView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseBatchSummaryControllerTest {

    @Mock
    private ObjectProvider<OfficialWarehouseBatchSummaryService> serviceProvider;
    @Mock
    private OfficialWarehouseBatchSummaryService service;
    @Mock
    private BusinessAccessResolver accessResolver;

    @Test
    void requiresStoreAccessAndReturnsSummary() {
        OfficialWarehouseBatchSummaryController controller =
                new OfficialWarehouseBatchSummaryController(serviceProvider, accessResolver);
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(900L)
                .businessOwnerUserId(307L)
                .build();
        BatchProductSummaryView expected = new BatchProductSummaryView();
        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireStoreAccess(
                request,
                BusinessCapability.OFFICIAL_WAREHOUSE,
                "STR108065-NSA"
        )).thenReturn(access);
        when(service.summarize(
                access,
                "STR108065-NSA",
                "SA",
                List.of("901235")
        )).thenReturn(expected);

        BatchProductSummaryView result = controller.productSummary(
                "STR108065-NSA",
                "SA",
                List.of("901235"),
                request
        );

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(
                request,
                BusinessCapability.OFFICIAL_WAREHOUSE,
                "STR108065-NSA"
        );
    }
}
