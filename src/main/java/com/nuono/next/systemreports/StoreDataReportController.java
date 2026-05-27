package com.nuono.next.systemreports;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system-reports/store-data")
public class StoreDataReportController {

    private final StoreDataReportService service;
    private final BusinessAccessResolver businessAccessResolver;

    public StoreDataReportController(
            StoreDataReportService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/overview")
    public StoreDataReportOverview overview(
            @RequestParam(required = false) String storeCode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.SYSTEM_REPORTS
        );
        return service.overview(context, storeCode);
    }
}
