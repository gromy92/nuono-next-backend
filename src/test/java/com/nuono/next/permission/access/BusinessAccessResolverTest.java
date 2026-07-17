package com.nuono.next.permission.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nuono.next.auth.AuthSessionTokenService;
import com.nuono.next.auth.AuthenticatedSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BusinessAccessResolverTest {

    @Mock
    private AuthSessionTokenService sessionTokenService;

    @Mock
    private BusinessAccessMapper accessMapper;

    @Test
    void bossContextOwnsAllResolvedStores() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR-A", "PROJECT-A"))
                .menuPaths(Set.of("/api/sku/manage"))
                .build();

        assertThat(context.isBossAccount()).isTrue();
        assertThat(context.isSystemAdmin()).isFalse();
        assertThat(context.canAccessStore("STR-A")).isTrue();
        assertThat(context.canAccessStore("PROJECT-A")).isTrue();
        assertThat(context.canAccessStore("STR-B")).isFalse();
    }

    @Test
    void storeOwnerMapNormalizesKeysAndExtendsAccessibleStores() {
        Map<String, Long> storeOwnerUserIds = new LinkedHashMap<>();
        storeOwnerUserIds.put(" str-a ", 307L);
        storeOwnerUserIds.put("project-b", 408L);
        storeOwnerUserIds.put(" ", 509L);
        storeOwnerUserIds.put("STR-C", null);

        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(501L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of(" explicit-store "))
                .storeOwnerUserIds(storeOwnerUserIds)
                .build();

        assertThat(context.getStoreCodes()).containsExactlyInAnyOrder("EXPLICIT-STORE", "STR-A", "PROJECT-B");
        assertThat(context.getStoreOwnerUserIds())
                .containsEntry("STR-A", 307L)
                .containsEntry("PROJECT-B", 408L)
                .doesNotContainKeys("", "STR-C");
        assertThat(context.canAccessStore("str-a")).isTrue();
        assertThat(context.canAccessStore(" PROJECT-B ")).isTrue();
        assertThat(context.canAccessStore("explicit-store")).isTrue();
        assertThat(context.resolveOwnerUserIdForStore("str-a")).isEqualTo(307L);
        assertThat(context.resolveOwnerUserIdForStore(" PROJECT-B ")).isEqualTo(408L);
        assertThat(context.resolveOwnerUserIdForStore("explicit-store")).isNull();
        assertThat(context.resolveOwnerUserIdForStore("unknown")).isNull();
    }

    @Test
    void capabilityRequiresExactPathOrSubPathBoundary() {
        BusinessAccessContext exact = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/sku/manage"))
                .build();
        BusinessAccessContext subPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/sku/manage/detail"))
                .build();
        BusinessAccessContext siblingPrefix = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/sku/manage-archive"))
                .build();
        BusinessAccessContext noonCall = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/noon-call/store-data"))
                .build();

        assertThat(exact.hasCapability(BusinessCapability.PRODUCT_MASTER)).isTrue();
        assertThat(subPath.hasCapability(BusinessCapability.PRODUCT_MASTER)).isTrue();
        assertThat(siblingPrefix.hasCapability(BusinessCapability.PRODUCT_MASTER)).isFalse();
        assertThat(noonCall.hasCapability(BusinessCapability.SYSTEM_REPORTS)).isTrue();
    }

    @Test
    void salesDataCapabilityIncludesPostSaleProfitCenterPaths() {
        BusinessAccessContext postSaleProfitApi = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/post-sale-profit/batches"))
                .build();
        BusinessAccessContext postSaleProfitPage = BusinessAccessContext.builder()
                .menuPaths(Set.of("/data/post-sale-profit"))
                .build();

        assertThat(postSaleProfitApi.hasCapability(BusinessCapability.SALES_DATA)).isTrue();
        assertThat(postSaleProfitPage.hasCapability(BusinessCapability.SALES_DATA)).isTrue();
    }

    @Test
    void ali1688HistoricalOrdersCapabilityUsesDedicatedPurchaseMenu() {
        BusinessAccessContext exact = BusinessAccessContext.builder()
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
        BusinessAccessContext skuPurchaseHistory = BusinessAccessContext.builder()
                .menuPaths(Set.of("/purchase/ali1688-sku-purchase-history"))
                .build();
        BusinessAccessContext apiPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/procurement/ali1688-orders/workbench"))
                .build();
        BusinessAccessContext oldCandidateCollection = BusinessAccessContext.builder()
                .menuPaths(Set.of("/purchase/1688-collection"))
                .build();

        assertThat(exact.hasCapability(BusinessCapability.ALI1688_HISTORICAL_ORDERS)).isTrue();
        assertThat(skuPurchaseHistory.hasCapability(BusinessCapability.ALI1688_HISTORICAL_ORDERS)).isTrue();
        assertThat(apiPath.hasCapability(BusinessCapability.ALI1688_HISTORICAL_ORDERS)).isTrue();
        assertThat(oldCandidateCollection.hasCapability(BusinessCapability.ALI1688_HISTORICAL_ORDERS)).isFalse();
    }

    @Test
    void productLogisticsCostCapabilityUsesInTransitBusinessAccess() {
        BusinessAccessContext pagePath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/purchase/product-logistics-costs"))
                .build();
        BusinessAccessContext apiPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/product-logistics-costs/current"))
                .build();

        assertThat(pagePath.hasCapability(BusinessCapability.IN_TRANSIT_GOODS)).isTrue();
        assertThat(apiPath.hasCapability(BusinessCapability.IN_TRANSIT_GOODS)).isTrue();
    }

    @Test
    void procurementCapabilityIncludesCombinedPurchaseOrderAndReplenishmentApis() {
        BusinessAccessContext legacyApiPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/purchase/order"))
                .build();
        BusinessAccessContext purchaseOrderApiPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/procurement/purchase-orders/800035"))
                .build();
        BusinessAccessContext replenishmentApiPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/replenishment-plan/overview"))
                .build();
        BusinessAccessContext shippingOrderCompatibilityPath = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/procurement/purchase-orders/shipping-orders/830035"))
                .build();

        assertThat(legacyApiPath.hasCapability(BusinessCapability.PROCUREMENT)).isTrue();
        assertThat(purchaseOrderApiPath.hasCapability(BusinessCapability.PROCUREMENT)).isTrue();
        assertThat(replenishmentApiPath.hasCapability(BusinessCapability.PROCUREMENT)).isTrue();
        assertThat(shippingOrderCompatibilityPath.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isTrue();
    }

    @Test
    void resolverAllowsAnyMatchingBusinessCapability() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(307L);
        user.setAccountType("internal");
        user.setRoleId(2L);
        user.setRoleName("老板");
        user.setUserLevel(1);
        user.setRoleLevel(1);
        user.setStatus(1);

        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(307L, 2L, 1));
        when(accessMapper.selectUserAccess(307L)).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(307L)).thenReturn(List.of("/warehouse/dispatch"));
        when(accessMapper.selectStoreScope(307L)).thenReturn(List.of(storeScope(307L, "STR-A")));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                accessMapper,
                new BusinessAccessGuard()
        );

        BusinessAccessContext context = resolver.requireAnyBusinessContext(
                request,
                BusinessCapability.PROCUREMENT,
                BusinessCapability.WAREHOUSE_DISPATCH
        );

        assertThat(context.getSessionUserId()).isEqualTo(307L);
        assertThat(context.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isTrue();
        assertThat(context.hasCapability(BusinessCapability.PROCUREMENT)).isFalse();
    }

    @Test
    void resolverAllowsAnyMatchingCapabilityWithinStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(307L);
        user.setAccountType("internal");
        user.setRoleId(2L);
        user.setRoleName("老板");
        user.setUserLevel(1);
        user.setRoleLevel(1);
        user.setStatus(1);

        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(307L, 2L, 1));
        when(accessMapper.selectUserAccess(307L)).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(307L)).thenReturn(List.of("/purchase/order"));
        when(accessMapper.selectStoreScope(307L)).thenReturn(List.of(storeScope(307L, "STR-A")));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                accessMapper,
                new BusinessAccessGuard()
        );

        BusinessAccessContext context = resolver.requireAnyStoreAccess(
                request,
                "STR-A",
                BusinessCapability.PRODUCT_MASTER,
                BusinessCapability.PROCUREMENT
        );

        assertThat(context.getSessionUserId()).isEqualTo(307L);
        assertThat(context.canAccessStore("STR-A")).isTrue();
        assertThat(context.hasCapability(BusinessCapability.PROCUREMENT)).isTrue();
    }

    @Test
    void warehouseDispatchCapabilityOwnsWarehouseOrderCompatibilityPaths() {
        BusinessAccessContext dispatchPage = BusinessAccessContext.builder()
                .menuPaths(Set.of("/warehouse/dispatch"))
                .build();
        BusinessAccessContext legacyWarehouseOrderPage = BusinessAccessContext.builder()
                .menuPaths(Set.of("/warehouse/shipping-orders"))
                .build();
        BusinessAccessContext legacyShippingOrderApi = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/procurement/purchase-orders/shipping-orders/830035"))
                .build();
        BusinessAccessContext legacyLogisticsBillApi = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/procurement/purchase-orders/logistics-bills/90001"))
                .build();
        BusinessAccessContext procurementOnly = BusinessAccessContext.builder()
                .menuPaths(Set.of("/api/procurement/purchase-orders"))
                .build();

        assertThat(dispatchPage.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isTrue();
        assertThat(legacyWarehouseOrderPage.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isTrue();
        assertThat(legacyShippingOrderApi.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isTrue();
        assertThat(legacyLogisticsBillApi.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isTrue();
        assertThat(procurementOnly.hasCapability(BusinessCapability.WAREHOUSE_DISPATCH)).isFalse();
    }

    @Test
    void exposedCollectionsAreImmutable() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .storeCodes(Set.of("STR-A"))
                .storeOwnerUserIds(Map.of("STR-B", 307L))
                .menuPaths(Set.of("/api/sku/manage"))
                .build();

        assertThatThrownBy(() -> context.getStoreCodes().add("STR-C"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.getStoreOwnerUserIds().put("STR-C", 408L))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> context.getMenuPaths().add("/api/sku/manage/detail"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void systemAdminIsNeverBusinessAccount() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(346L)
                .businessOwnerUserId(null)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .storeCodes(Set.of())
                .menuPaths(Set.of("/system/role"))
                .build();

        assertThat(context.isSystemAdmin()).isTrue();
        assertThat(context.isBusinessAccount()).isFalse();
    }

    @Test
    void guardRejectsSystemAdminForBusinessCapability() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(346L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/system/role"))
                .storeCodes(Set.of())
                .build();

        BusinessAccessGuard guard = new BusinessAccessGuard();

        assertThatThrownBy(() -> guard.requireBusinessCapability(context, BusinessCapability.PRODUCT_MASTER))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("系统管理员不能操作店铺业务");
    }

    @Test
    void guardAllowsSystemAdminForSystemReportsCapability() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(346L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/api/noon-call/store-data"))
                .storeCodes(Set.of())
                .build();

        BusinessAccessGuard guard = new BusinessAccessGuard();

        assertThat(guard.requireBusinessCapability(context, BusinessCapability.SYSTEM_REPORTS))
                .isSameAs(context);
    }

    @Test
    void guardRequiresMenuAndStoreForOperator() {
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(357L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("采购")
                .menuPaths(Set.of("/api/purchase/order"))
                .storeCodes(Set.of("STR-A"))
                .build();

        BusinessAccessGuard guard = new BusinessAccessGuard();

        assertThat(guard.requireStore(context, "STR-A")).isSameAs(context);
        assertThat(guard.requireBusinessCapability(context, BusinessCapability.PROCUREMENT)).isSameAs(context);
        assertThatThrownBy(() -> guard.requireStore(context, "STR-B"))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("当前账号不能操作该店铺");
        assertThatThrownBy(() -> guard.requireStore(context, " "))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("当前账号不能操作该店铺");
    }

    @Test
    void guardRequiresCapabilityForBoss() {
        BusinessAccessContext bossWithoutCapability = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleLevel(1)
                .roleName("老板")
                .menuPaths(Set.of())
                .storeCodes(Set.of("STR-A"))
                .build();
        BusinessAccessContext bossWithCapability = BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleLevel(1)
                .roleName("老板")
                .menuPaths(Set.of("/api/sku/manage"))
                .storeCodes(Set.of("STR-A"))
                .build();

        BusinessAccessGuard guard = new BusinessAccessGuard();

        assertThatThrownBy(() -> guard.requireBusinessCapability(
                bossWithoutCapability,
                BusinessCapability.PRODUCT_MASTER
        ))
                .isInstanceOf(BusinessAccessDeniedException.class)
                .hasMessageContaining("当前账号没有对应业务菜单权限");
        assertThat(guard.requireBusinessCapability(bossWithCapability, BusinessCapability.PRODUCT_MASTER))
                .isSameAs(bossWithCapability);
    }

    @Test
    void resolverMapsStoreOwnerPerStoreForOperatorAcrossOwners() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(357L);
        user.setAccountType("internal");
        user.setCreatedBy(307L);
        user.setRoleId(3L);
        user.setRoleName("采购");
        user.setUserLevel(3);
        user.setRoleLevel(3);
        user.setStatus(1);
        BusinessStoreScopeRow firstOwnerStore = storeScope(307L, "STR-A");
        BusinessStoreScopeRow secondOwnerStore = storeScope(408L, "str-b");

        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(357L, 3L, 3));
        when(accessMapper.selectUserAccess(357L)).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(357L)).thenReturn(List.of("/api/purchase/order"));
        when(accessMapper.selectStoreScope(357L)).thenReturn(List.of(firstOwnerStore, secondOwnerStore));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                accessMapper,
                new BusinessAccessGuard()
        );

        BusinessAccessContext context = resolver.resolve(request);

        assertThat(context.getAccountType()).isEqualTo(BusinessAccountType.OPERATOR);
        assertThat(context.getBusinessOwnerUserId()).isEqualTo(307L);
        assertThat(context.getStoreCodes()).containsExactlyInAnyOrder("STR-A", "STR-B");
        assertThat(context.getStoreOwnerUserIds())
                .containsEntry("STR-A", 307L)
                .containsEntry("STR-B", 408L);
        assertThat(context.resolveOwnerUserIdForStore("str-a")).isEqualTo(307L);
        assertThat(context.resolveOwnerUserIdForStore(" STR-B ")).isEqualTo(408L);
    }

    @Test
    void resolverUsesRoleLevelBeforeUserLevelForBusinessRole() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(346L);
        user.setAccountType("internal");
        user.setRoleId(3L);
        user.setRoleName("运营");
        user.setUserLevel(1);
        user.setRoleLevel(3);
        user.setStatus(1);

        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(346L, 3L, 1));
        when(accessMapper.selectUserAccess(346L)).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(346L)).thenReturn(List.of("/api/purchase/order"));
        when(accessMapper.selectStoreScope(346L)).thenReturn(List.of(storeScope(307L, "STR-A")));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                accessMapper,
                new BusinessAccessGuard()
        );

        BusinessAccessContext context = resolver.resolve(request);

        assertThat(context.getRoleLevel()).isEqualTo(3);
        assertThat(context.getAccountType()).isEqualTo(BusinessAccountType.OPERATOR);
    }

    @Test
    void resolverTreatsExternalBossLikeAccountAsUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(48435L);
        user.setAccountType("external");
        user.setCreatedBy(307L);
        user.setRoleId(2L);
        user.setRoleName("老板");
        user.setUserLevel(1);
        user.setRoleLevel(1);
        user.setStatus(1);

        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(48435L, 2L, 1));
        when(accessMapper.selectUserAccess(48435L)).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(48435L)).thenReturn(List.of("/api/sku/manage"));
        when(accessMapper.selectStoreScope(48435L)).thenReturn(List.of(storeScope(307L, "STR-A")));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                accessMapper,
                new BusinessAccessGuard()
        );

        BusinessAccessContext context = resolver.resolve(request);

        assertThat(context.getAccountType()).isEqualTo(BusinessAccountType.UNKNOWN);
        assertThat(context.isBusinessAccount()).isFalse();
    }

    @Test
    void requireStoreAccessRejectsBlankStoreCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessUserAccessRow user = new BusinessUserAccessRow();
        user.setUserId(357L);
        user.setAccountType("internal");
        user.setCreatedBy(307L);
        user.setRoleId(3L);
        user.setRoleName("采购");
        user.setUserLevel(3);
        user.setRoleLevel(3);
        user.setStatus(1);

        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(357L, 3L, 3));
        when(accessMapper.selectUserAccess(357L)).thenReturn(user);
        when(accessMapper.selectGrantedMenuPaths(357L)).thenReturn(List.of("/api/purchase/order"));
        when(accessMapper.selectStoreScope(357L)).thenReturn(List.of(storeScope(307L, "STR-A")));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                accessMapper,
                new BusinessAccessGuard()
        );

        assertThatThrownBy(() -> resolver.requireStoreAccess(request, BusinessCapability.PROCUREMENT, " "))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getReason()).isEqualTo("当前账号不能操作该店铺。");
                });
    }

    @Test
    void resolveReturnsServiceUnavailableWhenMapperIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(sessionTokenService.requireSession(request)).thenReturn(new AuthenticatedSession(357L, 3L, 3));

        BusinessAccessResolver resolver = new BusinessAccessResolver(
                sessionTokenService,
                (BusinessAccessMapper) null,
                new BusinessAccessGuard()
        );

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getReason()).isEqualTo("业务访问控制暂时不可用。");
                });
    }

    private static BusinessStoreScopeRow storeScope(Long ownerUserId, String storeCode) {
        BusinessStoreScopeRow row = new BusinessStoreScopeRow();
        row.setOwnerUserId(ownerUserId);
        row.setStoreCode(storeCode);
        return row;
    }
}
