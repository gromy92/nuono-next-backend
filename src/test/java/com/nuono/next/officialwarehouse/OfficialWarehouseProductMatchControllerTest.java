package com.nuono.next.officialwarehouse;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.intransit.InTransitProductMatchService;
import com.nuono.next.intransit.InTransitProductMatchViews.PreparationView;
import com.nuono.next.officialwarehouse.OfficialWarehouseProductMatchController.PrepareCommand;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseProductMatchControllerTest {
    @Mock
    private InTransitProductMatchService productMatchService;
    @Mock
    private BusinessAccessResolver accessResolver;
    @Mock
    private HttpServletRequest request;

    @Test
    void shouldMatchRawRowsUnderOfficialWarehouseStoreAccess() {
        OfficialWarehouseProductMatchController controller =
                new OfficialWarehouseProductMatchController(productMatchService, accessResolver);
        PrepareCommand command = new PrepareCommand();
        command.storeCode = "STR100";
        command.siteCode = "SA";
        BusinessAccessContext access = BusinessAccessContext.builder()
                .businessOwnerUserId(10002L)
                .sessionUserId(90001L)
                .build();
        PreparationView expected = new PreparationView();
        when(accessResolver.requireStoreAccess(
                request,
                BusinessCapability.OFFICIAL_WAREHOUSE,
                "STR100"
        )).thenReturn(access);
        when(productMatchService.prepareForStoreSite(access, "STR100", "SA")).thenReturn(expected);

        PreparationView result = controller.prepare(command, request);

        assertSame(expected, result);
        verify(productMatchService).prepareForStoreSite(access, "STR100", "SA");
    }
}
