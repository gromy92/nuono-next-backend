package com.nuono.next.mobile;

public class MobileSendCodeResponse {

    private Boolean success;

    private Integer cooldownSeconds;

    private String debugCode;

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Integer getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(Integer cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public String getDebugCode() {
        return debugCode;
    }

    public void setDebugCode(String debugCode) {
        this.debugCode = debugCode;
    }
}
