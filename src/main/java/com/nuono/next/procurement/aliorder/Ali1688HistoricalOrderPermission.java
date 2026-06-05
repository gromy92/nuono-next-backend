package com.nuono.next.procurement.aliorder;

import com.nuono.next.permission.access.BusinessAccessContext;
import org.springframework.util.StringUtils;

final class Ali1688HistoricalOrderPermission {

    private Ali1688HistoricalOrderPermission() {
    }

    static boolean canTriggerSync(BusinessAccessContext context) {
        if (context == null) {
            return false;
        }
        if (context.isBossAccount()) {
            return true;
        }
        if (!context.isOperatorAccount()) {
            return false;
        }
        Integer level = context.getRoleLevel();
        if (level != null && level <= 2) {
            return true;
        }
        String roleName = context.getRoleName();
        return StringUtils.hasText(roleName) && roleName.contains("管理");
    }
}
