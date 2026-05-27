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
@RequestMapping("/api/operations-config/calendar-rules")
public class OperationCalendarRuleController {

    private final BusinessAccessResolver accessResolver;
    private final OperationCalendarRuleService service;

    public OperationCalendarRuleController(
            BusinessAccessResolver accessResolver,
            OperationCalendarRuleService service
    ) {
        this.accessResolver = accessResolver;
        this.service = service;
    }

    @GetMapping("/active")
    public List<OperationCalendarRule> activeRules(
            HttpServletRequest request,
            @RequestParam(value = "bossUserIds", required = false) List<Long> bossUserIds,
            @RequestParam("ownerUserId") Long ownerUserId,
            @RequestParam("storeCode") String storeCode,
            @RequestParam("siteCode") String siteCode,
            @RequestParam(value = "bundleVersionId", required = false) Long bundleVersionId
    ) {
        return withAccessDeniedMapping(() -> service.listActive(
                resolveContext(request),
                bossUserIds,
                ownerUserId,
                storeCode,
                siteCode,
                bundleVersionId
        ));
    }

    @GetMapping("/history")
    public List<OperationCalendarRule> history(
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

    @PostMapping("/drafts")
    public OperationCalendarRule saveDraft(
            HttpServletRequest request,
            @RequestBody OperationCalendarRuleDraftRequest command
    ) {
        return withAccessDeniedMapping(() -> service.saveDraft(resolveContext(request), command.toCommand()));
    }

    @PostMapping("/copy-previous-year")
    public List<OperationCalendarRule> copyPreviousYear(
            HttpServletRequest request,
            @RequestBody OperationCalendarRuleCopyPreviousYearRequest command
    ) {
        return withAccessDeniedMapping(() -> service.copyPreviousYear(resolveContext(request), command.toCommand()));
    }

    @PostMapping("/batch")
    public List<OperationCalendarRule> batchUpdateDrafts(
            HttpServletRequest request,
            @RequestBody OperationCalendarRuleBatchUpdateRequest command
    ) {
        return withAccessDeniedMapping(() -> service.batchUpdateDrafts(resolveContext(request), command.toCommand()));
    }

    @PostMapping("/{ruleId}/publish")
    public OperationCalendarRule publish(
            HttpServletRequest request,
            @PathVariable("ruleId") Long ruleId,
            @RequestBody OperationCalendarRulePublishCommand command
    ) {
        return withAccessDeniedMapping(() -> service.publish(
                resolveContext(request),
                command.getBossUserIds(),
                ruleId,
                command.getMessage()
        ));
    }

    @PostMapping("/{ruleId}/disable")
    public OperationCalendarRule disable(
            HttpServletRequest request,
            @PathVariable("ruleId") Long ruleId,
            @RequestBody OperationCalendarRulePublishCommand command
    ) {
        return withAccessDeniedMapping(() -> service.disable(
                resolveContext(request),
                command.getBossUserIds(),
                ruleId,
                command.getMessage()
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
