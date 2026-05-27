package com.nuono.next.operationsconfig;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import java.util.Locale;
import org.springframework.util.StringUtils;

final class OperationConfigVersionSource {

    static final String SYSTEM_ADMIN = "system_admin";
    static final String BOSS = "boss";
    static final String OPERATIONS_MANAGER = "operations_manager";
    static final String OPERATOR = "operator";

    private OperationConfigVersionSource() {
    }

    static String role(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        if (context.isSystemAdmin()) {
            return SYSTEM_ADMIN;
        }
        if (context.isBossAccount()) {
            return BOSS;
        }
        String roleName = context.getRoleName() == null ? "" : context.getRoleName().toLowerCase(Locale.ROOT);
        if (roleName.contains("运营管理") || roleName.contains("运营主管") || roleName.contains("manager")) {
            return OPERATIONS_MANAGER;
        }
        return OPERATOR;
    }

    static String labelForContext(BusinessAccessContext context) {
        return label(role(context));
    }

    static String label(String role) {
        if (SYSTEM_ADMIN.equals(role)) {
            return "系统发布";
        }
        if (BOSS.equals(role)) {
            return "老板发布";
        }
        if (OPERATIONS_MANAGER.equals(role)) {
            return "运营管理发布";
        }
        if (OPERATOR.equals(role)) {
            return "运营发布";
        }
        return "运营发布";
    }

    static String safeRole(String role) {
        return StringUtils.hasText(role) ? role : OPERATOR;
    }

    static String safeLabel(String role, String label) {
        return StringUtils.hasText(label) ? label : label(safeRole(role));
    }
}
