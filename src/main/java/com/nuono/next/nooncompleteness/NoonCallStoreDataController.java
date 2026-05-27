package com.nuono.next.nooncompleteness;

import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/noon-call/store-data")
public class NoonCallStoreDataController {
    private final NoonCallStoreDataService storeDataService;
    private final BusinessAccessResolver businessAccessResolver;
    private final NoonGapPatrolActionService actionService;
    private final NoonDataCompletenessPermissionGuard permissionGuard;

    public NoonCallStoreDataController(
            NoonCallStoreDataService storeDataService,
            BusinessAccessResolver businessAccessResolver,
            NoonGapPatrolActionService actionService,
            NoonDataCompletenessPermissionGuard permissionGuard
    ) {
        this.storeDataService = storeDataService;
        this.businessAccessResolver = businessAccessResolver;
        this.actionService = actionService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping
    public NoonCallStoreDataView storeData(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String latestStatus,
            @RequestParam(required = false) String historyStatus,
            HttpServletRequest request
    ) {
        businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS);
        return storeDataService.view(NoonDataCompletenessQuery.fromRequest(
                ownerUserId,
                storeCode,
                siteCode,
                category,
                latestStatus,
                historyStatus
        ));
    }

    @PostMapping("/{ownerUserId}/{storeCode}/{siteCode}/{category}/sync")
    public NoonGapPatrolActionResult syncCategory(
            @PathVariable Long ownerUserId,
            @PathVariable String storeCode,
            @PathVariable String siteCode,
            @PathVariable String category,
            HttpServletRequest request
    ) {
        permissionGuard.requireActionAccess(
                businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)
        );
        return actionService.syncCategory(
                ownerUserId,
                storeCode,
                siteCode,
                NoonDataCompletenessQuery.parseEnum(NoonDataCategory.class, category)
        );
    }
}
