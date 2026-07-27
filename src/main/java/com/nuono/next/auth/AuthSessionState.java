package com.nuono.next.auth;

public class AuthSessionState {

    private Long credentialVersion;
    private Long roleId;
    private Integer level;

    public Long getCredentialVersion() {
        return credentialVersion;
    }

    public void setCredentialVersion(Long credentialVersion) {
        this.credentialVersion = credentialVersion;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }
}
