package com.nuono.next.auth;

import javax.servlet.http.HttpServletRequest;

public final class RoleAccessSupport {

    public static final String ROLE_VIEW_HEADER = "X-Nuono-Role-View";
    private static final Long SYSTEM_ADMIN_ROLE_ID = 1L;

    private RoleAccessSupport() {
    }

    public static boolean isOperatorRoleView(HttpServletRequest request) {
        return request != null && "operator".equalsIgnoreCase(request.getHeader(ROLE_VIEW_HEADER));
    }

    public static boolean isSystemAdmin(AuthenticatedSession session) {
        return session != null && isSystemAdmin(session.getRoleId(), session.getLevel());
    }

    public static boolean isSystemAdmin(Long roleId, Integer level) {
        return (level != null && level <= 0) || SYSTEM_ADMIN_ROLE_ID.equals(roleId);
    }
}
