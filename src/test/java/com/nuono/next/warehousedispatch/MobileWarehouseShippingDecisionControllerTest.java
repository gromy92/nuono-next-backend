package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.mobile.MobileApiResponse;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionConfirmCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionPreviewCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.MobileShippingDecisionConfirmView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.MobileShippingDecisionPreviewView;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class MobileWarehouseShippingDecisionControllerTest {

    @Test
    void previewReturnsGoneBecauseWebOwnsShippingDecision() {
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        BusinessAccessContext access = warehouseAccess();
        MobileShippingDecisionPreviewCommand command = new MobileShippingDecisionPreviewCommand();
        command.siteCode = "SA";
        command.transportMode = "AIR";

        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH)).thenReturn(access);

        MobileWarehouseShippingDecisionController controller =
                new MobileWarehouseShippingDecisionController(accessResolver);
        MobileApiResponse<MobileShippingDecisionPreviewView> response = controller.preview(command, request);

        assertThat(response.getCode()).isEqualTo(410);
        assertThat(response.getMsg()).contains("APP 端不再生成物流计划");
        assertThat(response.getData()).isNull();
        verify(accessResolver).requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH);
    }

    @Test
    void confirmReturnsGoneBecauseWebOwnsShippingDecision() {
        BusinessAccessResolver accessResolver = mock(BusinessAccessResolver.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        BusinessAccessContext access = warehouseAccess();
        MobileShippingDecisionConfirmCommand command = new MobileShippingDecisionConfirmCommand();
        command.siteCode = "SA";
        command.transportMode = "SEA";
        command.acceptedOptionKey = "MOBILE_SA_SEA_ET";

        when(accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH)).thenReturn(access);

        MobileWarehouseShippingDecisionController controller =
                new MobileWarehouseShippingDecisionController(accessResolver);
        MobileApiResponse<MobileShippingDecisionConfirmView> response = controller.confirm(command, request);

        assertThat(response.getCode()).isEqualTo(410);
        assertThat(response.getMsg()).contains("APP 端不再生成物流计划");
        assertThat(response.getData()).isNull();
        verify(accessResolver).requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH);
    }

    private BusinessAccessContext warehouseAccess() {
        return BusinessAccessContext.builder()
                .sessionUserId(90004L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR69486-NSA"))
                .build();
    }
}
