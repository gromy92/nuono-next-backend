package com.nuono.next.intransit.autosync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessGuard;
import com.nuono.next.permission.access.BusinessAccessMapper;
import com.nuono.next.permission.access.BusinessStoreScopeRow;
import com.nuono.next.permission.access.BusinessUserAccessRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogisticsAutoSyncAccessContextFactoryTest {

    @Mock
    private BusinessAccessMapper accessMapper;

    @Test
    void operatorWithInTransitPermissionBuildsRestrictedBusinessContext() {
        LogisticsAutoSyncAccessContextFactory factory = factory();
        when(accessMapper.selectUserAccess(408L)).thenReturn(operator(408L, 307L));
        when(accessMapper.selectGrantedMenuPaths(408L)).thenReturn(List.of("/purchase/in-transit-goods"));
        when(accessMapper.selectStoreScope(408L)).thenReturn(List.of(
                storeScope(999L, "STR999999-NSA"),
                storeScope(307L, "str108065-nsa")
        ));

        BusinessAccessContext context = factory.requireAccessContext(account(307L, 408L));

        assertThat(context.getSessionUserId()).isEqualTo(408L);
        assertThat(context.getBusinessOwnerUserId()).isEqualTo(307L);
        assertThat(context.isOperatorAccount()).isTrue();
        assertThat(context.canAccessStore("STR108065-NSA")).isTrue();
    }

    @Test
    void operatorWithoutInTransitPermissionIsRejected() {
        LogisticsAutoSyncAccessContextFactory factory = factory();
        when(accessMapper.selectUserAccess(408L)).thenReturn(operator(408L, 307L));
        when(accessMapper.selectGrantedMenuPaths(408L)).thenReturn(List.of("/purchase/order"));
        when(accessMapper.selectStoreScope(408L)).thenReturn(List.of(storeScope(307L, "STR108065-NSA")));

        assertThatThrownBy(() -> factory.requireAccessContext(account(307L, 408L)))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("菜单权限");
    }

    @Test
    void systemAdminIsRejectedForStoreBusinessExecution() {
        LogisticsAutoSyncAccessContextFactory factory = factory();
        when(accessMapper.selectUserAccess(1L)).thenReturn(systemAdmin(1L));
        when(accessMapper.selectGrantedMenuPaths(1L)).thenReturn(List.of("/purchase/in-transit-goods"));
        when(accessMapper.selectStoreScope(1L)).thenReturn(List.of(storeScope(307L, "STR108065-NSA")));

        assertThatThrownBy(() -> factory.requireAccessContext(account(307L, 1L)))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("系统管理员不能操作店铺业务");
    }

    @Test
    void operatorResolvedToDifferentOwnerIsRejected() {
        LogisticsAutoSyncAccessContextFactory factory = factory();
        when(accessMapper.selectUserAccess(408L)).thenReturn(operator(408L, 999L));
        when(accessMapper.selectGrantedMenuPaths(408L)).thenReturn(List.of("/purchase/in-transit-goods"));
        when(accessMapper.selectStoreScope(408L)).thenReturn(List.of(storeScope(999L, "STR999999-NSA")));

        assertThatThrownBy(() -> factory.requireAccessContext(account(307L, 408L)))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("业务归属");
    }

    private LogisticsAutoSyncAccessContextFactory factory() {
        return new LogisticsAutoSyncAccessContextFactory(accessMapper, new BusinessAccessGuard());
    }

    private static LogisticsAutoSyncAccount account(Long ownerUserId, Long operatorUserId) {
        LogisticsAutoSyncAccount account = new LogisticsAutoSyncAccount();
        account.setId(180001L);
        account.setOwnerUserId(ownerUserId);
        account.setOperatorUserId(operatorUserId);
        return account;
    }

    private static BusinessUserAccessRow operator(Long userId, Long createdBy) {
        BusinessUserAccessRow row = new BusinessUserAccessRow();
        row.setUserId(userId);
        row.setCreatedBy(createdBy);
        row.setRoleId(3L);
        row.setRoleName("运营");
        row.setRoleLevel(2);
        row.setStatus(1);
        row.setAccountType("internal");
        return row;
    }

    private static BusinessUserAccessRow systemAdmin(Long userId) {
        BusinessUserAccessRow row = new BusinessUserAccessRow();
        row.setUserId(userId);
        row.setRoleId(1L);
        row.setRoleName("系统管理员");
        row.setRoleLevel(0);
        row.setStatus(1);
        row.setAccountType("internal");
        return row;
    }

    private static BusinessStoreScopeRow storeScope(Long ownerUserId, String storeCode) {
        BusinessStoreScopeRow row = new BusinessStoreScopeRow();
        row.setOwnerUserId(ownerUserId);
        row.setStoreCode(storeCode);
        return row;
    }
}
