package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
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
    private final NoonAuthWaitQueue authWaitQueue;

    @Value("${nuono.noon.account-session.daily-check.enabled:true}")
    private boolean enabled;

    NoonAccountSessionDailyVerifier(
            NoonAccountSessionMapper mapper,
            NoonSessionGateway sessionGateway,
            NoonAuthWaitQueue authWaitQueue
    ) {
        this.mapper = mapper;
        this.sessionGateway = sessionGateway;
        this.authWaitQueue = authWaitQueue;
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
            return;
        }
        String noonEmail = sessionGateway.configuredMerchantLoginEmail();
        for (NoonAccountSessionProjectTarget target : targets) {
            if (!isUsable(target)) {
                enqueue(target);
                return;
            }
            try {
                JsonNode whoami = sessionGateway.whoamiWithCookie(
                        target.getSessionCookie(), target.getProjectCode(), target.getStoreCode()
                );
                if (!NoonProjectSessionValidator.matchesTargetProject(whoami, target.getProjectCode())) {
                    enqueue(target);
                    return;
                }
                sessionGateway.validateCatalogSessionWithCookie(
                        target.getSessionCookie(), target.getProjectCode(), target.getStoreCode(), noonEmail
                );
            } catch (RuntimeException exception) {
                enqueue(target);
                return;
            }
        }
    }

    private void enqueue(NoonAccountSessionProjectTarget target) {
        if (target != null && target.getOwnerUserId() != null && StringUtils.hasText(target.getStoreCode())) {
            authWaitQueue.enqueue(NoonAuthWaitRequest.binding(
                    target.getOwnerUserId(), target.getProjectCode(), target.getStoreCode()
            ));
        }
    }

    private static boolean isUsable(NoonAccountSessionProjectTarget target) {
        return target != null
                && StringUtils.hasText(target.getProjectCode())
                && StringUtils.hasText(target.getStoreCode())
                && StringUtils.hasText(target.getSessionCookie());
    }
}
