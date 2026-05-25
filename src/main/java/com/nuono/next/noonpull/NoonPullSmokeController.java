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
public class NoonPullSmokeController {
    private final ObjectProvider<NoonPullSmokeRunner> runnerProvider;
    private final AuthSessionTokenService sessionTokenService;

    public NoonPullSmokeController(
            ObjectProvider<NoonPullSmokeRunner> runnerProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.runnerProvider = runnerProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @PostMapping("/smoke-runs")
    public NoonPullSmokeRunResult runSmoke(
            @RequestBody NoonPullSmokeRunCommand command,
            HttpServletRequest request
    ) {
        NoonPullSmokeRunner runner = runnerProvider.getIfAvailable();
        if (runner == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Noon pull smoke runner is not available.");
        }
        requireSystemAdmin(request);
        try {
            return runner.run(command);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void requireSystemAdmin(HttpServletRequest request) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        if (!RoleAccessSupport.isSystemAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Noon pull smoke runs require system administrator access.");
        }
    }
}
