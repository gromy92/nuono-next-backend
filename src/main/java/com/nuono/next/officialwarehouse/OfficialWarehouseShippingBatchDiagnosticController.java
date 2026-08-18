package com.nuono.next.officialwarehouse;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/warehouse/official-warehouse/shipping-batches")
public class OfficialWarehouseShippingBatchDiagnosticController {
    private final ObjectProvider<OfficialWarehouseShippingBatchDiagnosticService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    public OfficialWarehouseShippingBatchDiagnosticController(
            ObjectProvider<OfficialWarehouseShippingBatchDiagnosticService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/diagnostic")
    public OfficialWarehouseShippingBatchDiagnosticView diagnose(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String keyword,
            HttpServletRequest request
    ) {
        try {
            BusinessAccessContext access = accessResolver.requireStoreAccess(
                    request, BusinessCapability.OFFICIAL_WAREHOUSE, storeCode
            );
            return service().diagnose(access, storeCode, siteCode, keyword);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private OfficialWarehouseShippingBatchDiagnosticService service() {
        OfficialWarehouseShippingBatchDiagnosticService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon 官方仓服务未启用。");
        }
        return service;
    }
}
