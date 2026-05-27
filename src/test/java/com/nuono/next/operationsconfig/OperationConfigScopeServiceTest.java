package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationConfigScopeServiceTest {

    @Test
    void systemAdminSelectedBossesResolveToBossWideScopesInsteadOfStoreChoices() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        repository.addBoss(201L, "Boss A", "boss-a");
        repository.addBoss(202L, "Boss B", "boss-b");
        repository.addStore(201L, 301L, "PRJ-A", "Xingyao", "STR-A-NAE", "AE");
        repository.addStore(201L, 301L, "PRJ-A", "Xingyao", "STR-A-NSA", "SA");
        repository.addStore(202L, 302L, "PRJ-B", "Canman", "STR-B-NAE", "AE");
        OperationConfigScopeService service = new OperationConfigScopeService(repository);

        OperationConfigScopeView view = service.resolveScope(
                systemAdminContext(),
                List.of(201L, 202L)
        );

        assertTrue(view.isSystemAdmin());
        assertIterableEquals(List.of(201L, 202L), view.getSelectedBossUserIds());
        assertEquals(2, view.getBossOptions().size());
        assertIterableEquals(
                List.of("*", "*"),
                view.getStores().stream().map(OperationConfigStoreScope::getStoreCode).collect(Collectors.toList())
        );
        assertIterableEquals(
                List.of("*", "*"),
                view.getStores().stream().map(OperationConfigStoreScope::getSiteCode).collect(Collectors.toList())
        );
        assertEquals(201L, view.getDefaultOwnerUserId());
        assertEquals("*", view.getDefaultStoreCode());
        assertEquals("*", view.getDefaultSiteCode());
        OperationConfigStoreScope systemScope = service.requireStoreSiteScope(
                systemAdminContext(),
                List.of(201L, 202L),
                201L,
                "*",
                "*"
        );
        assertEquals(201L, systemScope.getOwnerUserId());
        assertEquals("*", systemScope.getStoreCode());
    }

    @Test
    void businessRoleDefaultsToAuthorizedStoreSiteOnly() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        repository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
        repository.addStore(999L, 999L, "PRJ-Z", "Other", "STR-Z-NAE", "AE");
        OperationConfigScopeService service = new OperationConfigScopeService(repository);

        OperationConfigScopeView view = service.resolveScope(operatorContext(), List.of());

        assertEquals(false, view.isSystemAdmin());
        assertEquals(List.of(), view.getBossOptions());
        assertEquals(1, view.getStores().size());
        assertEquals(501L, view.getDefaultOwnerUserId());
        assertEquals("STR-X-NAE", view.getDefaultStoreCode());
        assertEquals("AE", view.getDefaultSiteCode());
    }

    @Test
    void backendRejectsBusinessRoleCrossOwnerStoreIntent() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        repository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
        repository.addStore(999L, 999L, "PRJ-Z", "Other", "STR-Z-NAE", "AE");
        OperationConfigScopeService service = new OperationConfigScopeService(repository);

        BusinessAccessDeniedException error = assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.requireStoreSiteScope(operatorContext(), List.of(), 999L, "STR-Z-NAE", "AE")
        );

        assertTrue(error.getMessage().contains("当前账号不能操作该店铺"));
    }

    @Test
    void rejectsForgedOwnerWhenSessionStoreOwnerMappingIsMissing() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        repository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
        repository.addStore(999L, 999L, "PRJ-Z", "Other", "STR-X-NAE", "AE");
        OperationConfigScopeService service = new OperationConfigScopeService(repository);
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR-X-NAE"))
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();

        BusinessAccessDeniedException error = assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.requireStoreSiteScope(context, List.of(), 999L, "STR-X-NAE", "AE")
        );

        assertTrue(error.getMessage().contains("当前账号不能操作该店铺"));
    }

    @Test
    void resolveScopeOmitsStoresWithoutSessionOwnerMapping() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        repository.addStore(501L, 701L, "PRJ-X", "Xingyao", "STR-X-NAE", "AE");
        repository.addStore(999L, 999L, "PRJ-Z", "Other", "STR-X-NAE", "AE");
        OperationConfigScopeService service = new OperationConfigScopeService(repository);
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR-X-NAE"))
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();

        OperationConfigScopeView view = service.resolveScope(context, List.of());

        assertTrue(view.getStores().isEmpty());
        assertEquals("NO_STORE", view.getEmptyReason());
    }

    @Test
    void rejectsAccessWithoutOperationConfigMenu() {
        InMemoryOperationConfigScopeRepository repository = new InMemoryOperationConfigScopeRepository();
        OperationConfigScopeService service = new OperationConfigScopeService(repository);
        BusinessAccessContext context = BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR-X-NAE"))
                .storeOwnerUserIds(Map.of("STR-X-NAE", 501L))
                .menuPaths(Set.of("/data/sales-analysis"))
                .build();

        BusinessAccessDeniedException error = assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.resolveScope(context, List.of())
        );

        assertTrue(error.getMessage().contains("当前账号没有对应业务菜单权限"));
    }

    private static BusinessAccessContext systemAdminContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(1L)
                .accountType(BusinessAccountType.SYSTEM_ADMIN)
                .roleLevel(0)
                .roleName("系统管理员")
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR-X-NAE"))
                .storeOwnerUserIds(Map.of("STR-X-NAE", 501L))
                .menuPaths(Set.of("/operations/config/business-calendar"))
                .build();
    }

    private static class InMemoryOperationConfigScopeRepository implements OperationConfigScopeRepository {
        private final List<OperationConfigBossOption> bosses = new ArrayList<>();
        private final Map<Long, List<OperationConfigStoreScope>> storesByBoss = new LinkedHashMap<>();

        void addBoss(Long ownerUserId, String displayName, String accountNo) {
            bosses.add(new OperationConfigBossOption(ownerUserId, displayName, accountNo));
        }

        void addStore(
                Long ownerUserId,
                Long logicalStoreId,
                String projectCode,
                String projectName,
                String storeCode,
                String siteCode
        ) {
            storesByBoss.computeIfAbsent(ownerUserId, ignored -> new ArrayList<>())
                    .add(new OperationConfigStoreScope(
                            ownerUserId,
                            logicalStoreId,
                            projectCode,
                            projectName,
                            storeCode,
                            siteCode
                    ));
        }

        @Override
        public List<OperationConfigBossOption> listBossOptions() {
            return bosses;
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds) {
            return bossUserIds.stream()
                    .flatMap(ownerUserId -> storesByBoss.getOrDefault(ownerUserId, List.of()).stream())
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes) {
            return storesByBoss.values().stream()
                    .flatMap(List::stream)
                    .filter(store -> storeCodes.contains(store.getStoreCode()))
                    .collect(Collectors.toList());
        }
    }
}
