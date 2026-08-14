package com.nuono.next.noon;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Real adapter for a human-requested Noon OTP. It intentionally has no mailbox reader. */
@Component
@Profile("local-db")
final class NoonSessionGatewayManualOtpGateway implements NoonAccountManualOtpGateway {
    private final NoonSessionGateway sessionGateway;

    NoonSessionGatewayManualOtpGateway(NoonSessionGateway sessionGateway) {
        this.sessionGateway = sessionGateway;
    }

    @Override
    public PreparedChallenge sendOneManualOtp() {
        NoonSessionGateway.EmailOtpGeneration generation = sessionGateway.prepareEmailOtpGeneration(
                sessionGateway.configuredMerchantLoginEmail()
        );
        sessionGateway.sendEmailOtp(generation);
        return new PreparedChallenge(generation);
    }

    @Override
    public AuthenticatedGrant validateSubmittedOtp(PreparedChallenge challenge, String otpCode) {
        if (challenge == null || !(challenge.getOpaqueState() instanceof NoonSessionGateway.EmailOtpGeneration)) {
            throw new IllegalArgumentException("验证码挑战已失效，请重新人工发送。"
            );
        }
        NoonSessionGateway.EmailIdentityGrant grant = sessionGateway.validateEmailOtp(
                (NoonSessionGateway.EmailOtpGeneration) challenge.getOpaqueState(),
                otpCode
        );
        return new AuthenticatedGrant(grant);
    }

    @Override
    public VerifiedProjectSession createVerifiedProjectSession(
            AuthenticatedGrant grant,
            String projectCode,
            String storeCode
    ) {
        if (grant == null || !(grant.getOpaqueGrant() instanceof NoonSessionGateway.EmailIdentityGrant)) {
            throw new IllegalArgumentException("Noon 账号授权结果无效。");
        }
        NoonSessionGateway.EmailIdentityGrant identityGrant =
                (NoonSessionGateway.EmailIdentityGrant) grant.getOpaqueGrant();
        NoonSessionGateway.ProjectSessionCookie session = sessionGateway.createEmailOtpProjectSession(
                identityGrant, projectCode, storeCode
        );
        JsonNode whoami = sessionGateway.whoamiWithProjectSession(session, storeCode);
        if (!NoonProjectSessionValidator.validatesProjectSession(
                whoami,
                sessionGateway.configuredMerchantLoginEmail(),
                projectCode,
                session
        )) {
            throw new IllegalStateException("Noon Project 会话校验失败。");
        }
        sessionGateway.validateCatalogProjectSession(session, storeCode);
        return new VerifiedProjectSession(session.getCookie(), identityGrant.getUserCode());
    }
}
