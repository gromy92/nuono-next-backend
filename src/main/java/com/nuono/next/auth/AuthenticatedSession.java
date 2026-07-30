package com.nuono.next.auth;

public class AuthenticatedSession {

    private final Long userId;
    private final Long roleId;
    private final Integer level;
    private final Long credentialVersion;

    public AuthenticatedSession(Long userId, Long roleId, Integer level) {
        this(userId, roleId, level, null);
    }

    public AuthenticatedSession(
            Long userId,
            Long roleId,
            Integer level,
            Long credentialVersion
    ) {
        this.userId = userId;
        this.roleId = roleId;
        this.level = level;
        this.credentialVersion = credentialVersion;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public Integer getLevel() {
        return level;
    }

    public Long getCredentialVersion() {
        return credentialVersion;
    }
}
