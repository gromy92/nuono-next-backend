package com.nuono.next.nooncompleteness;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class NoonDataCompletenessPermissionGuard {

    public void requireActionAccess(BusinessAccessContext context) {
        if (context == null || (!context.isBossAccount() && !context.isSystemAdmin())) {
            throw new BusinessAccessDeniedException("当前账号只能查看数据完整性报表，不能执行巡检动作。");
        }
    }
}
