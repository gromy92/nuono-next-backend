package com.nuono.next.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class AuthChangePasswordCommand {

    private Long userId;

    @JsonIgnore
    private Long expectedCredentialVersion;

    private String currentPassword;

    private String newPassword;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getExpectedCredentialVersion() {
        return expectedCredentialVersion;
    }

    public void setExpectedCredentialVersion(Long expectedCredentialVersion) {
        this.expectedCredentialVersion = expectedCredentialVersion;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
