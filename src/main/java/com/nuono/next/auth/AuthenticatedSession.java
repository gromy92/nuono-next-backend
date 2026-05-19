package com.nuono.next.auth;

public class AuthenticatedSession {

    private final Long userId;
    private final Long roleId;
    private final Integer level;

    public AuthenticatedSession(Long userId, Long roleId, Integer level) {
        this.userId = userId;
        this.roleId = roleId;
        this.level = level;
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
}
