package com.nuono.next.warehousedispatch;

import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/warehouse/dispatch")
public class WarehouseOrderJourneyController extends WarehouseDispatchEndpointSupport {

    private final WarehouseOrderJourneyQuery query;

    public WarehouseOrderJourneyController(
            ObjectProvider<LocalDbWarehouseDispatchService> serviceProvider,
            BusinessAccessResolver accessResolver,
            WarehouseOrderJourneyQuery query
    ) {
        super(serviceProvider, accessResolver);
        this.query = query;
    }

    @GetMapping("/warehouse-order-journeys")
    public List<WarehouseOrderJourneyView> warehouseOrderJourneys(HttpServletRequest request) {
        return query.list(access(request));
    }
}
