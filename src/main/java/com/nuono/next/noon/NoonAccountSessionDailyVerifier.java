package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Daily keep-alive check for the single configured Noon account.
 *
 * <p>This deliberately validates existing Project sessions only. It never sends, reads, retries,
 * or stores an OTP and it never starts a business task.</p>
 */
@Component
@Profile("local-db")
final class NoonAccountSessionDailyVerifier {
    private final NoonAccountSessionMapper mapper;
    private final NoonSessionGateway sessionGateway;
    private final NoonAccountManualOtpService manualOtpService;

    @Value("${nuono.noon.account-session.daily-check.enabled:true}")
    private boolean enabled;

    NoonAccountSessionDailyVerifier(
            NoonAccountSessionMapper mapper,
            NoonSessionGateway sessionGateway,
            NoonAccountManualOtpService manualOtpService
    ) {
        this.mapper = mapper;
        this.sessionGateway = sessionGateway;
        this.manualOtpService = manualOtpService;
    }

    @Scheduled(cron = "${nuono.noon.account-session.daily-check.cron:0 10 4 * * *}")
    void verifyDailySession() {
        if (!enabled) {
            return;
        }
        verifyNow();
    }

    void verifyNow() {
        List<NoonAccountSessionProjectTarget> targets = mapper.listBoundProjects();
        if (targets.isEmpty()) {
            manualOtpService.recordDailySessionCheck(false);
            return;
        }
        String noonEmail = sessionGateway.configuredMerchantLoginEmail();
        for (NoonAccountSessionProjectTarget target : targets) {
            if (!isUsable(target)) {
                manualOtpService.recordDailySessionCheck(false);
                return;
            }
            try {
                JsonNode whoami = sessionGateway.whoamiWithCookie(
                        target.getSessionCookie(), target.getProjectCode(), target.getStoreCode()
                );
                if (!NoonProjectSessionValidator.matchesTargetProject(whoami, target.getProjectCode())) {
                    manualOtpService.recordDailySessionCheck(false);
                    return;
                }
                sessionGateway.validateCatalogSessionWithCookie(
                        target.getSessionCookie(), target.getProjectCode(), target.getStoreCode(), noonEmail
                );
            } catch (RuntimeException exception) {
                manualOtpService.recordDailySessionCheck(false);
                return;
            }
        }
        manualOtpService.recordDailySessionCheck(true);
    }

    private static boolean isUsable(NoonAccountSessionProjectTarget target) {
        return target != null
                && StringUtils.hasText(target.getProjectCode())
                && StringUtils.hasText(target.getStoreCode())
                && StringUtils.hasText(target.getSessionCookie());
    }
}
