package com.nuono.next.noon;

/** Request body for one human-entered Noon OTP. The code is never persisted or logged. */
public final class NoonAccountManualOtpVerificationCommand {
    private String challengeId;
    private String otpCode;

    public String getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(String challengeId) {
        this.challengeId = challengeId;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }
}
