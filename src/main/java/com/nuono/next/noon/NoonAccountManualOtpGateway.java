package com.nuono.next.noon;

/**
 * One explicit manual Noon account challenge. Implementations must never poll a mailbox or retry
 * a send after an outbound attempt.
 */
interface NoonAccountManualOtpGateway {
    PreparedChallenge sendOneManualOtp();

    AuthenticatedGrant validateSubmittedOtp(PreparedChallenge challenge, String otpCode);

    VerifiedProjectSession createVerifiedProjectSession(
            AuthenticatedGrant grant,
            String projectCode,
            String storeCode
    );

    final class PreparedChallenge {
        private final Object opaqueState;

        PreparedChallenge(Object opaqueState) {
            this.opaqueState = opaqueState;
        }

        Object getOpaqueState() {
            return opaqueState;
        }
    }

    final class AuthenticatedGrant {
        private final Object opaqueGrant;

        AuthenticatedGrant(Object opaqueGrant) {
            this.opaqueGrant = opaqueGrant;
        }

        Object getOpaqueGrant() {
            return opaqueGrant;
        }
    }

    final class VerifiedProjectSession {
        private final String cookie;
        private final String userCode;

        VerifiedProjectSession(String cookie, String userCode) {
            this.cookie = cookie;
            this.userCode = userCode;
        }

        String getCookie() {
            return cookie;
        }

        String getUserCode() {
            return userCode;
        }
    }
}
