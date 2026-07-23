package com.nuono.next.warehousedispatch;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

abstract class WarehouseDispatchEndpointSupport {

    private final ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider;
    private final BusinessAccessResolver accessResolver;

    protected WarehouseDispatchEndpointSupport(
            ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider,
            BusinessAccessResolver accessResolver
    ) {
        this.serviceProvider = serviceProvider;
        this.accessResolver = accessResolver;
    }

    protected BusinessAccessContext access(HttpServletRequest request) {
        return accessResolver.requireBusinessContext(request, BusinessCapability.WAREHOUSE_DISPATCH);
    }

    protected LocalDbWarehouseDispatchService service() {
        LocalDbWarehouseDispatchService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "仓库发运服务未启用。");
        }
        return service;
    }

    protected ResponseStatusException badRequest(IllegalArgumentException exception) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
}
