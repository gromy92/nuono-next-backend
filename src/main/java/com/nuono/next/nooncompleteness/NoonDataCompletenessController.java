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
@RequestMapping("/api/system-reports/noon-data-completeness")
public class NoonDataCompletenessController {
    private final NoonDataCompletenessOverviewService overviewService;
    private final NoonDataGapPatrolViewService gapPatrolViewService;
    private final BusinessAccessResolver businessAccessResolver;
    private final NoonGapPatrolActionService actionService;
    private final NoonDataCompletenessBootstrapService bootstrapService;
    private final NoonDataCompletenessPermissionGuard permissionGuard;

    public NoonDataCompletenessController(
            NoonDataCompletenessOverviewService overviewService,
            NoonDataGapPatrolViewService gapPatrolViewService,
            BusinessAccessResolver businessAccessResolver,
            NoonGapPatrolActionService actionService,
            NoonDataCompletenessBootstrapService bootstrapService,
            NoonDataCompletenessPermissionGuard permissionGuard
    ) {
        this.overviewService = overviewService;
        this.gapPatrolViewService = gapPatrolViewService;
        this.businessAccessResolver = businessAccessResolver;
        this.actionService = actionService;
        this.bootstrapService = bootstrapService;
        this.permissionGuard = permissionGuard;
    }

    @GetMapping("/overview")
    public NoonDataCompletenessOverviewView overview(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String latestStatus,
            @RequestParam(required = false) String historyStatus,
            HttpServletRequest request
    ) {
        businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS);
        return overviewService.overview(NoonDataCompletenessQuery.fromRequest(
                ownerUserId,
                storeCode,
                siteCode,
                category,
                latestStatus,
                historyStatus
        ));
    }

    @GetMapping("/gaps")
    public NoonDataGapPatrolView gaps(
            @RequestParam(required = false) Long ownerUserId,
            @RequestParam(required = false) String storeCode,
            @RequestParam(required = false) String siteCode,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String failureType,
            @RequestParam(required = false) Boolean retryable,
            HttpServletRequest request
    ) {
        businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS);
        return gapPatrolViewService.gaps(NoonDataGapQuery.fromRequest(
                ownerUserId,
                storeCode,
                siteCode,
                category,
                status,
                failureType,
                retryable
        ));
    }

    @PostMapping("/audit-existing-scopes")
    public NoonDataCompletenessBootstrapResult auditExistingScopes(HttpServletRequest request) {
        permissionGuard.requireActionAccess(
                businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)
        );
        return bootstrapService.auditExistingScopes();
    }

    @PostMapping("/gaps/{gapId}/retry")
    public NoonGapPatrolActionResult retryGap(@PathVariable Long gapId, HttpServletRequest request) {
        permissionGuard.requireActionAccess(
                businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)
        );
        return actionService.retry(gapId);
    }

    @PostMapping("/gaps/{gapId}/pause")
    public NoonGapPatrolActionResult pauseGap(
            @PathVariable Long gapId,
            @RequestParam(required = false) String reason,
            HttpServletRequest request
    ) {
        permissionGuard.requireActionAccess(
                businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)
        );
        return actionService.pause(gapId, reason);
    }

    @PostMapping("/gaps/{gapId}/resume")
    public NoonGapPatrolActionResult resumeGap(@PathVariable Long gapId, HttpServletRequest request) {
        permissionGuard.requireActionAccess(
                businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)
        );
        return actionService.resume(gapId);
    }

    @PostMapping("/gaps/{gapId}/reaudit")
    public NoonGapPatrolActionResult reAuditGap(@PathVariable Long gapId, HttpServletRequest request) {
        permissionGuard.requireActionAccess(
                businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)
        );
        return actionService.reAudit(gapId);
    }
}
