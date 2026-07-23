package com.nuono.next.warehousedispatch;

import com.nuono.next.mobile.MobileApiResponse;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionConfirmCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionPreviewCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.MobileShippingDecisionConfirmView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.MobileShippingDecisionPreviewView;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile/warehouse/shipping-decisions")
public class MobileWarehouseShippingDecisionController {

    private static final int WEB_OWNED_FLOW_CODE = 410;
    private static final String WEB_OWNED_FLOW_MESSAGE = "APP 端不再生成物流计划，请从 Web 发货申请单下发发货单。";

    private final BusinessAccessResolver accessResolver;

    public MobileWarehouseShippingDecisionController(BusinessAccessResolver accessResolver) {
        this.accessResolver = accessResolver;
    }

    @PostMapping("/preview")
    public MobileApiResponse<MobileShippingDecisionPreviewView> preview(
            @RequestBody MobileShippingDecisionPreviewCommand command,
            HttpServletRequest request
    ) {
        access(request);
        return MobileApiResponse.failure(WEB_OWNED_FLOW_CODE, WEB_OWNED_FLOW_MESSAGE);
    }

    @PostMapping("/confirm")
    public MobileApiResponse<MobileShippingDecisionConfirmView> confirm(
            @RequestBody MobileShippingDecisionConfirmCommand command,
            HttpServletRequest request
    ) {
        access(request);
        return MobileApiResponse.failure(WEB_OWNED_FLOW_CODE, WEB_OWNED_FLOW_MESSAGE);
    }

    private BusinessAccessContext access(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH);
    }
}
