package com.nuono.next.officialwarehouse;

import com.nuono.next.officialwarehouse.OfficialWarehouseBatchSummaryViews.BatchProductSummaryView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/warehouse/official-warehouse")
public class OfficialWarehouseBatchSummaryController {

    private final ObjectProvider<OfficialWarehouseBatchSummaryService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public OfficialWarehouseBatchSummaryController(
            ObjectProvider<OfficialWarehouseBatchSummaryService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/shipping-batches/product-summary")
    public BatchProductSummaryView productSummary(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam List<String> shippingBatchIds,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext access = accessResolver.requireStoreAccess(
                    request,
                    BusinessCapability.OFFICIAL_WAREHOUSE,
                    storeCode
            );
            return service().summarize(access, storeCode, siteCode, shippingBatchIds);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private OfficialWarehouseBatchSummaryService service() {
        OfficialWarehouseBatchSummaryService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓服务未启用。");
        }
        return service;
    }
}
