package com.nuono.next.intransit.autosync;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/in-transit-goods/logistics-auto-sync")
public class LogisticsAutoSyncController {
    private final LogisticsAutoSyncAccountService accountService;
    private final BusinessAccessResolver businessAccessResolver;

    public LogisticsAutoSyncController(
            LogisticsAutoSyncAccountService accountService,
            BusinessAccessResolver businessAccessResolver
    ) {
        this.accountService = accountService;
        this.businessAccessResolver = businessAccessResolver;
    }

    @GetMapping("/accounts")
    public List<LogisticsAutoSyncAccountView> accounts(HttpServletRequest request) {
        BusinessAccessContext context = requireContext(request);
        return accountService.list(context.getBusinessOwnerUserId());
    }

    @PostMapping("/accounts")
    public LogisticsAutoSyncAccountView saveAccount(
            @RequestBody(required = false) LogisticsAutoSyncAccountCommand command,
            HttpServletRequest request
    ) {
        BusinessAccessContext context = requireContext(request);
        LogisticsAutoSyncAccountCommand resolved = command == null ? new LogisticsAutoSyncAccountCommand() : command;
        resolved.setOwnerUserId(context.getBusinessOwnerUserId());
        resolved.setOperatorUserId(context.getSessionUserId());
        try {
            return accountService.save(resolved, context.getSessionUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private BusinessAccessContext requireContext(HttpServletRequest request) {
        return businessAccessResolver.requireBusinessContext(request, BusinessCapability.IN_TRANSIT_GOODS);
    }
}
