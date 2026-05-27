package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operations-config/lifecycle-rules")
public class OperationLifecycleRuleController {

    private final BusinessAccessResolver accessResolver;
    private final OperationLifecycleRuleService service;
    private final OperationConfigLifecycleRecalculationService recalculationService;

    public OperationLifecycleRuleController(
            BusinessAccessResolver accessResolver,
            OperationLifecycleRuleService service,
            OperationConfigLifecycleRecalculationService recalculationService
    ) {
        this.accessResolver = accessResolver;
        this.service = service;
        this.recalculationService = recalculationService;
    }

    @GetMapping("/state")
    public OperationLifecycleRuleStateView state(
            HttpServletRequest request,
            @RequestParam(value = "bossUserIds", required = false) List<Long> bossUserIds,
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode,
            @RequestParam(value = "bundleVersionId", required = false) Long bundleVersionId
    ) {
        return withAccessDeniedMapping(() -> service.currentState(
                resolveContext(request),
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                bundleVersionId
        ));
    }

    @GetMapping("/history")
    public List<OperationLifecycleRuleView> history(
            HttpServletRequest request,
            @RequestParam(value = "bossUserIds", required = false) List<Long> bossUserIds,
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode,
            @RequestParam(value = "bundleVersionId", required = false) Long bundleVersionId
    ) {
        return withAccessDeniedMapping(() -> service.history(
                resolveContext(request),
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                bundleVersionId
        ));
    }

    @PostMapping("/drafts/from-current")
    public OperationLifecycleRule createDraftFromCurrent(
            HttpServletRequest request,
            @RequestBody OperationLifecycleRuleCreateDraftRequest command
    ) {
        return withAccessDeniedMapping(() -> service.createDraftFromCurrent(resolveContext(request), command.toCommand()));
    }

    @PostMapping("/drafts")
    public OperationLifecycleRule saveDraft(
            HttpServletRequest request,
            @RequestBody OperationLifecycleRuleDraftRequest command
    ) {
        return withAccessDeniedMapping(() -> service.saveDraft(resolveContext(request), command.toCommand()));
    }

    @PostMapping("/{ruleId}/publish")
    public OperationLifecycleRule publish(
            HttpServletRequest request,
            @PathVariable("ruleId") Long ruleId,
            @RequestBody(required = false) OperationLifecycleRulePublishCommand command
    ) {
        OperationLifecycleRulePublishCommand safeCommand = command == null ? new OperationLifecycleRulePublishCommand() : command;
        return withAccessDeniedMapping(() -> service.publish(
                resolveContext(request),
                safeCommand.getBossUserIds(),
                ruleId,
                safeCommand.getMessage()
        ));
    }

    @PostMapping("/recalculate")
    public com.nuono.next.sales.ProductLifecycleJobRecord recalculate(
            HttpServletRequest request,
            @RequestBody OperationConfigLifecycleRecalculationRequest command
    ) {
        return withAccessDeniedMapping(() -> recalculationService.recalculate(
                resolveContext(request),
                command.toCommand()
        ));
    }

    private BusinessAccessContext resolveContext(HttpServletRequest request) {
        return accessResolver.resolve(request);
    }

    private <T> T withAccessDeniedMapping(SupplierWithAccess<T> action) {
        try {
            return action.get();
        } catch (BusinessAccessDeniedException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    private interface SupplierWithAccess<T> {
        T get();
    }
}
