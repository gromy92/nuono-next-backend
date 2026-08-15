package com.nuono.next.noon;

import static com.nuono.next.auth.RoleAccessSupport.isSystemAdmin;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Account-administrator entry point for the single configured Noon login. */
@RestController
@RequestMapping("/api/noon/account-session")
public final class NoonAccountSessionController {
    private final ObjectProvider<NoonAccountManualOtpService> serviceProvider;
    private final AuthSessionTokenService sessionTokenService;

    public NoonAccountSessionController(
            ObjectProvider<NoonAccountManualOtpService> serviceProvider,
            AuthSessionTokenService sessionTokenService
    ) {
        this.serviceProvider = serviceProvider;
        this.sessionTokenService = sessionTokenService;
    }

    @GetMapping
    public NoonAccountSessionView status(HttpServletRequest request) {
        requireAccountAdministrator(request);
        return service().status();
    }

    @PostMapping("/manual-otp")
    public NoonAccountSessionView sendManualOtp(HttpServletRequest request) {
        AuthenticatedSession session = requireAccountAdministrator(request);
        try {
            return service().send(session.getUserId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @PostMapping("/manual-otp/verify")
    public NoonAccountSessionView verifyManualOtp(
            @RequestBody NoonAccountManualOtpVerificationCommand command,
            HttpServletRequest request
    ) {
        AuthenticatedSession session = requireAccountAdministrator(request);
        try {
            return service().verify(
                    session.getUserId(),
                    command == null ? null : command.getChallengeId(),
                    command == null ? null : command.getOtpCode()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    private NoonAccountManualOtpService service() {
        NoonAccountManualOtpService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Noon 单账号登录当前不可用。"
            );
        }
        return service;
    }

    private AuthenticatedSession requireAccountAdministrator(HttpServletRequest request) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        if (!isSystemAdmin(session)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只有系统管理员可以操作共享 Noon 登录账号。"
            );
        }
        return session;
    }
}
