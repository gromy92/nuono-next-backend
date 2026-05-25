package com.nuono.next.noonpull;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.auth.RoleAccessSupport;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/noon-pull")
public class NoonProductionSchedulerEnablementController {
    private final ObjectProvider<NoonProductionSchedulerEnablementGate> gateProvider;
    private final AuthSessionTokenService sessionTokenService;

    public NoonProductionSchedulerEnablementController(
            ObjectProvider<NoonProductionSchedulerEnablementGate> gateProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.gateProvider = gateProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/production-scheduler-enablement")
    public NoonProductionSchedulerEnablementResult enable(
            @RequestBody NoonProductionSchedulerEnablementCommand command,
            HttpServletRequest request
    ) {
        NoonProductionSchedulerEnablementGate gate = gateProvider.getIfAvailable();
        if (gate == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Noon production scheduler enablement gate is not available."
            );
        }
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        if (!RoleAccessSupport.isSystemAdmin(session)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Noon production scheduler enablement requires system administrator access."
            );
        }
        return gate.enable(command, session.getUserId());
    }
}
