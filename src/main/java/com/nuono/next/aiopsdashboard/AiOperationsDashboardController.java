package com.nuono.next.aiopsdashboard;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-operations-dashboard")
public class AiOperationsDashboardController {

    private final AiOperationsDashboardService service;
    private final BusinessAccessResolver businessAccessResolver;

    public AiOperationsDashboardController(
            AiOperationsDashboardService service,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.service = service;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/overview")
    public AiOperationsDashboardOverview overview(
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String datePreset,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = StringUtils.hasText(storeCode)
                ? businessAccessResolver.requireStoreAccess(
                        request,
                        BusinessCapability.AI_OPERATIONS_DASHBOARD,
                        storeCode
                )
                : businessAccessResolver.requireBusinessContext(
                        request,
                        BusinessCapability.AI_OPERATIONS_DASHBOARD
                );
        Long ownerUserId = StringUtils.hasText(storeCode)
                ? context.resolveOwnerUserIdForStore(storeCode)
                : context.getBusinessOwnerUserId();
        if (ownerUserId == null) {
            ownerUserId = context.getBusinessOwnerUserId();
        }

        return service.overview(new AiOperationsDashboardQuery(
                ownerUserId,
                context.getSessionUserId(),
                storeCode,
                siteCode,
                StringUtils.hasText(datePreset) ? datePreset : "last7Days"
        ));
    }
}
