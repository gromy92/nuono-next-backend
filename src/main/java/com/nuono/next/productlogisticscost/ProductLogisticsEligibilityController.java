package com.nuono.next.productlogisticscost;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import com.nuono.next.productlogisticscost.ProductLogisticsCostCommands.ManualCurrentQuoteWithEligibilityCommand;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.EligibilityView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.EligibilityListView;
import com.nuono.next.productlogisticscost.ProductLogisticsCostRecords.ManualCurrentQuoteWithEligibilityResult;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product-logistics-costs")
public class ProductLogisticsEligibilityController {

    private final ProductLogisticsCurrentQuoteMaintenanceService service;
    private final BusinessAccessResolver accessResolver;

    public ProductLogisticsEligibilityController(
            ProductLogisticsCurrentQuoteMaintenanceService service,
            BusinessAccessResolver accessResolver
    ) {
        this.service = service;
        this.accessResolver = accessResolver;
    }

    @GetMapping("/eligibility/current")
    public EligibilityView currentEligibility(
            @RequestParam String storeCode,
            @RequestParam String partnerSku,
            @RequestParam String siteCode,
            @RequestParam String forwarderCode,
            @RequestParam String transportMode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = accessResolver.requireStoreAccess(
                request,
                BusinessCapability.IN_TRANSIT_GOODS,
                storeCode
        );
        return service.currentEligibility(
                context.getBusinessOwnerUserId(),
                storeCode,
                partnerSku,
                siteCode,
                forwarderCode,
                transportMode
        );
    }

    @GetMapping("/eligibility/current-list")
    public EligibilityListView currentEligibilities(
            @RequestParam String storeCode,
            @RequestParam String siteCode,
            @RequestParam String forwarderCode,
            @RequestParam String transportMode,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = accessResolver.requireStoreAccess(
                request,
                BusinessCapability.IN_TRANSIT_GOODS,
                storeCode
        );
        return service.currentEligibilities(
                context.getBusinessOwnerUserId(),
                storeCode,
                siteCode,
                forwarderCode,
                transportMode
        );
    }

    @PostMapping("/current/manual-with-eligibility")
    public ManualCurrentQuoteWithEligibilityResult maintainCurrentQuote(
            @RequestBody ManualCurrentQuoteWithEligibilityCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = accessResolver.requireStoreAccess(
                request,
                BusinessCapability.LOGISTICS_QUOTE,
                command == null ? null : command.storeCode
        );
        return service.maintainCurrentQuote(
                context.getBusinessOwnerUserId(),
                context.getSessionUserId(),
                command
        );
    }
}
