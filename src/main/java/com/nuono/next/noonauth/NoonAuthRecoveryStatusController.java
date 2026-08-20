package com.nuono.next.noonauth;

import static com.nuono.next.auth.RoleAccessSupport.isSystemAdmin;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import com.nuono.next.noon.NoonAccountSessionAuditResult;
import com.nuono.next.noon.NoonAccountSessionDailyVerifier;
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

@RestController
@Profile("local-db")
@RequestMapping("/api/noon/account-session")
public final class NoonAuthRecoveryStatusController {
    private final NoonAuthRecoveryRepository repository;
    private final NoonAuthRecoveryProperties properties;
    private final AuthSessionTokenService sessionTokenService;
    private final NoonAccountSessionDailyVerifier sessionVerifier;
    private final String configuredEmail;

    public NoonAuthRecoveryStatusController(
            NoonAuthRecoveryRepository repository,
            NoonAuthRecoveryProperties properties,
            AuthSessionTokenService sessionTokenService,
            NoonAccountSessionDailyVerifier sessionVerifier,
            @Value("${nuono.noon.auth.email-otp.email:}") String configuredEmail
    ) {
        this.repository = repository;
        this.properties = properties;
        this.sessionTokenService = sessionTokenService;
        this.sessionVerifier = sessionVerifier;
        this.configuredEmail = configuredEmail;
    }

    @GetMapping
    public NoonAuthRecoveryStatusView status(HttpServletRequest request) {
        AuthenticatedSession session = sessionTokenService.requireSession(request);
        if (!isSystemAdmin(session)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "只有系统管理员可以查看共享 Noon 登录状态。"
            );
        }
        if (!properties.isEnabled()) {
            return view(false, "DISABLED", null);
        }
        if (!StringUtils.hasText(configuredEmail)) {
            return view(true, "MISCONFIGURED", null);
        }
        NoonAuthIdentityRecoveryRecord active = repository.selectActiveRecovery(
                NoonAuthIdentityKey.fromEmail(configuredEmail)
        );
        return view(
                true,
                active == null ? "IDLE"
                        : active.getStatus() == null ? "UNKNOWN" : active.getStatus().name(),
                active
        );
    }

    private NoonAuthRecoveryStatusView view(
            boolean enabled,
            String status,
            NoonAuthIdentityRecoveryRecord active
    ) {
        NoonAccountSessionAuditResult audit = sessionVerifier.latestResult();
        return new NoonAuthRecoveryStatusView(
                enabled,
                status,
                active == null ? null : active.getId(),
                active == null ? null : active.getGenerationNo(),
                active == null ? null : active.getSendAttemptCount(),
                active == null ? null : active.getNextAttemptAt(),
                active == null ? null : active.getFailureCode(),
                properties.isAllProjectsEnabled(),
                properties.isSessionAuditEnabled(),
                properties.isStartupAuditEnabled(),
                audit.isReady(),
                audit.getScopeMode(),
                audit.getStatus(),
                audit.getTotalProjects(),
                audit.getScopedProjects(),
                audit.getVerifiedProjects(),
                audit.getExcludedProjects(),
                audit.getUnverifiedProjects()
        );
    }
}
