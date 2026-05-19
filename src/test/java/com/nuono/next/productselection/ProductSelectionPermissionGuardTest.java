package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.ProductSelectionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSelectionPermissionGuardTest {

    @Mock
    private ProductSelectionMapper productSelectionMapper;

    @Test
    void allowsOwnerLogicalStoreWhenUserStoreScopeIsMissing() {
        ProductSelectionPermissionGuard guard = new ProductSelectionPermissionGuard(productSelectionMapper);
        ProductSelectionUserContext boss = user(307L, "毕翠红", 1, 1);
        ProductSelectionStoreScope ownedScope = scope(307L, 50005L, "PRJ108065", "canman", "STR108065-NAE");

        when(productSelectionMapper.selectUserContext(307L)).thenReturn(boss);
        when(productSelectionMapper.selectVisibleStoreScope(307L, "STR108065-NAE")).thenReturn(null);
        when(productSelectionMapper.selectOwnedLogicalStoreScope(307L, "STR108065-NAE")).thenReturn(ownedScope);

        ProductSelectionStoreScope result = guard.requireWritableStore(307L, "STR108065-NAE");

        assertEquals(307L, result.getOwnerUserId());
        assertEquals(50005L, result.getLogicalStoreId());
        assertEquals("STR108065-NAE", result.getStoreCode());
        verify(productSelectionMapper, never()).selectAnyStoreScope("STR108065-NAE");
    }

    @Test
    void rejectsNonOwnerLogicalStoreWhenUserStoreScopeIsMissing() {
        ProductSelectionPermissionGuard guard = new ProductSelectionPermissionGuard(productSelectionMapper);
        ProductSelectionUserContext boss = user(308L, "other-boss", 1, 1);

        when(productSelectionMapper.selectUserContext(308L)).thenReturn(boss);
        when(productSelectionMapper.selectVisibleStoreScope(308L, "STR108065-NAE")).thenReturn(null);
        when(productSelectionMapper.selectOwnedLogicalStoreScope(308L, "STR108065-NAE")).thenReturn(null);

        ProductSelectionAccessDeniedException exception = assertThrows(
                ProductSelectionAccessDeniedException.class,
                () -> guard.requireWritableStore(308L, "STR108065-NAE")
        );

        assertEquals("当前账号没有可访问的店铺范围。", exception.getMessage());
        verify(productSelectionMapper, never()).selectAnyStoreScope("STR108065-NAE");
        verify(productSelectionMapper, never()).selectLogicalStoreScope("STR108065-NAE");
    }

    @Test
    void rejectsMissingLocalAdminUser() {
        ProductSelectionPermissionGuard guard = new ProductSelectionPermissionGuard(productSelectionMapper);

        when(productSelectionMapper.selectUserContext(1L)).thenReturn(null);

        ProductSelectionAccessDeniedException exception = assertThrows(
                ProductSelectionAccessDeniedException.class,
                () -> guard.requireWritableStore(1L, "STR108065-NAE")
        );

        assertEquals("当前账号不存在或已停用。", exception.getMessage());
        verify(productSelectionMapper, never()).selectAnyStoreScope("STR108065-NAE");
    }

    @Test
    void fallsBackToFirstOwnedLogicalStoreWhenNoStoreCodeProvided() {
        ProductSelectionPermissionGuard guard = new ProductSelectionPermissionGuard(productSelectionMapper);
        ProductSelectionUserContext boss = user(307L, "毕翠红", 1, 1);
        ProductSelectionStoreScope ownedScope = scope(307L, 50005L, "PRJ108065", "canman", "STR108065-NAE");

        when(productSelectionMapper.selectUserContext(307L)).thenReturn(boss);
        when(productSelectionMapper.selectFirstVisibleStoreScope(307L)).thenReturn(null);
        when(productSelectionMapper.selectFirstOwnedLogicalStoreScope(307L)).thenReturn(ownedScope);

        ProductSelectionStoreScope result = guard.requireReadableStore(307L, null);

        assertEquals(50005L, result.getLogicalStoreId());
        assertEquals("canman", result.getProjectName());
    }

    private ProductSelectionUserContext user(Long userId, String accountNo, Integer level, Integer status) {
        ProductSelectionUserContext user = new ProductSelectionUserContext();
        user.setUserId(userId);
        user.setAccountNo(accountNo);
        user.setRealName(accountNo);
        user.setLevel(level);
        user.setStatus(status);
        return user;
    }

    private ProductSelectionStoreScope scope(
            Long ownerUserId,
            Long logicalStoreId,
            String projectCode,
            String projectName,
            String storeCode
    ) {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOperatorUserId(ownerUserId);
        scope.setOwnerUserId(ownerUserId);
        scope.setLogicalStoreId(logicalStoreId);
        scope.setProjectCode(projectCode);
        scope.setProjectName(projectName);
        scope.setStoreCode(storeCode);
        scope.setSite("AE");
        scope.setAuthorized(true);
        return scope;
    }
}
