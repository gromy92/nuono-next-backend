package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operations-config")
public class OperationConfigScopeController {

    private final BusinessAccessResolver accessResolver;
    private final OperationConfigScopeService scopeService;

    public OperationConfigScopeController(
            BusinessAccessResolver accessResolver,
            OperationConfigScopeService scopeService
    ) {
        this.accessResolver = accessResolver;
        this.scopeService = scopeService;
    }

    @GetMapping("/scope")
    public OperationConfigScopeView scope(
            HttpServletRequest request,
            @RequestParam(value = "bossUserIds", required = false) List<Long> bossUserIds
    ) {
        return withAccessDeniedMapping(() -> scopeService.resolveScope(resolveContext(request), bossUserIds));
    }

    @PostMapping("/scope/validate")
    public OperationConfigStoreScope validateScope(
            HttpServletRequest request,
            @RequestBody OperationConfigScopeValidationCommand command
    ) {
        return withAccessDeniedMapping(() -> scopeService.requireStoreSiteScope(
                resolveContext(request),
                command.getBossUserIds(),
                command.getOwnerUserId(),
                command.getStoreCode(),
                command.getSiteCode()
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
