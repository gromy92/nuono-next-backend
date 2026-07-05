package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSelectionAccessAdapterTest {

    @Mock
    private ProductSelectionPermissionGuard permissionGuard;

    @Test
    void derivesReadableStoreScopeFromBusinessAccessContext() {
        ProductSelectionAccessAdapter adapter = new ProductSelectionAccessAdapter(permissionGuard);
        BusinessAccessContext access = bossAccess("STR108065-NAE");
        ProductSelectionStoreScope storeScope = storeScope("STR108065-NAE", 50005L);

        when(permissionGuard.requireReadableStore(307L, "STR108065-NAE")).thenReturn(storeScope);

        ProductSelectionAccessScope result = adapter.requireReadableStore(access, null);

        assertEquals(307L, result.getOperatorUserId());
        assertEquals("STR108065-NAE", result.getStoreCode());
        assertEquals(50005L, result.getLogicalStoreId());
    }

    @Test
    void rejectsStoreOutsideBusinessAccessContextBeforeLegacyGuard() {
        ProductSelectionAccessAdapter adapter = new ProductSelectionAccessAdapter(permissionGuard);
        BusinessAccessContext access = bossAccess("STR108065-NAE");

        ProductSelectionAccessDeniedException exception = assertThrows(
                ProductSelectionAccessDeniedException.class,
                () -> adapter.requireReadableStore(access, "STR245027-NSA")
        );

        assertEquals("当前账号不能访问该店铺。", exception.getMessage());
        verify(permissionGuard, never()).requireReadableStore(307L, "STR245027-NSA");
    }

    @Test
    void rejectsSystemAdminBeforeLegacyGuard() {
        ProductSelectionAccessAdapter adapter = new ProductSelectionAccessAdapter(permissionGuard);
        BusinessAccessContext access = BusinessAccessContext.builder()
                .sessionUserId(1L)
                .businessOwnerUserId(1L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .storeCodes(Set.of("STR108065-NAE"))
                .storeOwnerUserIds(Map.of("STR108065-NAE", 307L))
                .build();

        ProductSelectionAccessDeniedException exception = assertThrows(
                ProductSelectionAccessDeniedException.class,
                () -> adapter.requireReadableStore(access, "STR108065-NAE")
        );

        assertEquals("系统管理员不能操作店铺业务。", exception.getMessage());
        verify(permissionGuard, never()).requireReadableStore(1L, "STR108065-NAE");
    }

    @Test
    void writableStoreStillDelegatesToLegacyGuard() {
        ProductSelectionAccessAdapter adapter = new ProductSelectionAccessAdapter(permissionGuard);
        BusinessAccessContext access = bossAccess("STR108065-NAE");
        ProductSelectionStoreScope storeScope = storeScope("STR108065-NAE", 50005L);

        when(permissionGuard.requireWritableStore(307L, "STR108065-NAE")).thenReturn(storeScope);

        ProductSelectionAccessScope result = adapter.requireWritableStore(access, "str108065-nae");

        assertEquals(307L, result.getOperatorUserId());
        assertEquals("STR108065-NAE", result.getStoreCode());
        verify(permissionGuard).requireWritableStore(307L, "STR108065-NAE");
    }

    private BusinessAccessContext bossAccess(String storeCode) {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of(storeCode))
                .storeOwnerUserIds(Map.of(storeCode, 307L))
                .build();
    }

    private ProductSelectionStoreScope storeScope(String storeCode, Long logicalStoreId) {
        ProductSelectionStoreScope scope = new ProductSelectionStoreScope();
        scope.setOperatorUserId(307L);
        scope.setOwnerUserId(307L);
        scope.setLogicalStoreId(logicalStoreId);
        scope.setStoreCode(storeCode);
        scope.setSite("AE");
        scope.setAuthorized(true);
        return scope;
    }
}
