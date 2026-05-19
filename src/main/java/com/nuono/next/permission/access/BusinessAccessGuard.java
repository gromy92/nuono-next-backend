package com.nuono.next.permission.access;

import org.springframework.stereotype.Component;

@Component
public class BusinessAccessGuard {

    public BusinessAccessContext requireBusinessCapability(
            BusinessAccessContext context,
            BusinessCapability capability
    ) {
        requireBusinessAccount(context);
        if (!context.hasCapability(capability)) {
            throw new BusinessAccessDeniedException("当前账号没有对应业务菜单权限。");
        }
        return context;
    }

    public BusinessAccessContext requireStore(BusinessAccessContext context, String storeCode) {
        requireBusinessAccount(context);
        if (!context.canAccessStore(storeCode)) {
            throw new BusinessAccessDeniedException("当前账号不能操作该店铺。");
        }
        return context;
    }

    public BusinessAccessContext requireBusinessAccount(BusinessAccessContext context) {
        if (context == null) {
            throw new BusinessAccessDeniedException("缺少业务访问上下文。");
        }
        if (context.isSystemAdmin()) {
            throw new BusinessAccessDeniedException("系统管理员不能操作店铺业务。");
        }
        if (!context.isBusinessAccount()) {
            throw new BusinessAccessDeniedException("当前账号不能操作店铺业务。");
        }
        return context;
    }
}
