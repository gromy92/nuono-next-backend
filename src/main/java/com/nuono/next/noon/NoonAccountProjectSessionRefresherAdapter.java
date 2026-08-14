package com.nuono.next.noon;

import com.nuono.next.infrastructure.mapper.NoonAccountSessionMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Concrete adapter that distributes one account grant to Project sessions already bound in Nuono.
 * A Project failure is reported as a failed projection; it never causes a second OTP request.
 */
@Component
@Profile("local-db")
final class NoonAccountProjectSessionRefresherAdapter implements NoonAccountProjectSessionRefresher {
    private final NoonAccountSessionMapper mapper;
    private final NoonAccountManualOtpGateway gateway;

    NoonAccountProjectSessionRefresherAdapter(
            NoonAccountSessionMapper mapper,
            NoonAccountManualOtpGateway gateway
    ) {
        this.mapper = mapper;
        this.gateway = gateway;
    }

    @Override
    public RefreshResult refresh(NoonAccountManualOtpGateway.AuthenticatedGrant grant, Long operatorUserId) {
        List<NoonAccountSessionProjectTarget> targets = mapper.listBoundProjects();
        int refreshed = 0;
        int failed = 0;
        for (NoonAccountSessionProjectTarget target : targets) {
            if (!isComplete(target)) {
                failed++;
                continue;
            }
            try {
                NoonAccountManualOtpGateway.VerifiedProjectSession session =
                        gateway.createVerifiedProjectSession(
                                grant, target.getProjectCode(), target.getStoreCode()
                        );
                if (mapper.persistProjectSession(
                        target.getOwnerUserId(),
                        target.getProjectCode(),
                        session.getCookie(),
                        session.getUserCode(),
                        operatorUserId
                ) == 1) {
                    refreshed++;
                } else {
                    failed++;
                }
            } catch (RuntimeException exception) {
                // Do not retry a Project session or request another OTP in this account login.
                failed++;
            }
        }
        return new RefreshResult(refreshed, failed);
    }

    private static boolean isComplete(NoonAccountSessionProjectTarget target) {
        return target != null
                && target.getOwnerUserId() != null
                && StringUtils.hasText(target.getProjectCode())
                && StringUtils.hasText(target.getStoreCode());
    }
}
