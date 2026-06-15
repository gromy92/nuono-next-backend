package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nuono.next.intransit.InTransitBatchCommands.InTransitBatchQuery;
import com.nuono.next.intransit.InTransitBatchCommands.SaveBatchCommand;
import com.nuono.next.intransit.InTransitBatchCommands.SaveLineCommand;
import com.nuono.next.operationsconfig.OperationConfigBossOption;
import com.nuono.next.operationsconfig.OperationConfigScopeRepository;
import com.nuono.next.operationsconfig.OperationConfigStoreScope;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccountType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InTransitGoodsAccessScopeServiceTest {

    private InMemoryScopeRepository repository;
    private InTransitGoodsAccessScopeService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryScopeRepository();
        repository.addStore(10002L, "STR245027-NAE", "AE");
        repository.addStore(10002L, "STR245027-NSA", "SA");
        repository.addStore(20002L, "STR999999-NAE", "AE");
        service = new InTransitGoodsAccessScopeService(repository);
    }

    @Test
    void shouldKeepBatchQueryOwnerWideWhenNoDestinationFilterSupplied() {
        InTransitBatchQuery query = new InTransitBatchQuery();

        service.applyReadableBatchScope(operatorContext(), query);

        assertFalse(query.isAccessScopeRestricted());
        assertEquals(0, query.getAllowedStoreSites().size());
    }

    @Test
    void shouldNormalizeBatchQueryDestinationFilter() {
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setTargetStoreCode(" str245027-nae ");
        query.setTargetSiteCode(" ae ");

        service.applyReadableBatchScope(operatorContext(), query);

        assertEquals("DB", query.getTargetStoreCode());
        assertEquals("AE", query.getTargetSiteCode());
        assertFalse(query.isAccessScopeRestricted());
        assertEquals(0, query.getAllowedStoreSites().size());
    }

    @Test
    void shouldNormalizeBossBatchDestinationFilterWithoutStoreOwnerMap() {
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setTargetStoreCode("STR245027-NAE");
        query.setTargetSiteCode("AE");

        service.applyReadableBatchScope(bossContext(), query);

        assertEquals("DB", query.getTargetStoreCode());
        assertEquals("AE", query.getTargetSiteCode());
    }

    @Test
    void shouldRejectUnsupportedBatchDestinationFilter() {
        InTransitBatchQuery query = new InTransitBatchQuery();
        query.setTargetStoreCode("JED");
        query.setTargetSiteCode("AE");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.applyReadableBatchScope(operatorContext(), query)
        );

        assertEquals("目的地只支持 RUH 利雅得或 DB 迪拜。", exception.getMessage());
    }

    @Test
    void shouldNormalizeBatchWriteDestinationWithoutStoreScopeCheck() {
        SaveBatchCommand command = new SaveBatchCommand();
        command.setTargetStoreCode("STR245027-NAE");
        command.setTargetSiteCode("SA");

        service.requireWritableBatchScope(operatorContext(), command);

        assertEquals("DB", command.getTargetStoreCode());
        assertEquals("SA", command.getTargetSiteCode());
    }

    @Test
    void shouldAllowIncompleteDraftBatchBeforeStoreSiteIsSelected() {
        SaveBatchCommand command = new SaveBatchCommand();

        service.requireWritableBatchScope(operatorContext(), command);

        assertEquals(null, command.getTargetStoreCode());
        assertEquals(null, command.getTargetSiteCode());
    }

    @Test
    void shouldRejectLineWriteForUnauthorizedStoreSite() {
        SaveLineCommand command = new SaveLineCommand();
        command.setStoreCode("STR999999-NAE");
        command.setSiteCode("AE");

        BusinessAccessDeniedException exception = assertThrows(
                BusinessAccessDeniedException.class,
                () -> service.requireWritableLineScope(operatorContext(), command)
        );

        assertEquals("当前账号不能操作该店铺。", exception.getMessage());
    }

    @Test
    void shouldNormalizeLineWriteStoreSiteWhenAuthorized() {
        SaveLineCommand command = new SaveLineCommand();
        command.setStoreCode(" str245027-nae ");
        command.setSiteCode(" ae ");

        service.requireWritableLineScope(operatorContext(), command);

        assertEquals("STR245027-NAE", command.getStoreCode());
        assertEquals("AE", command.getSiteCode());
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(90001L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR245027-NAE", "STR245027-NSA"))
                .storeOwnerUserIds(Map.of(
                        "STR245027-NAE", 10002L,
                        "STR245027-NSA", 10002L
                ))
                .build();
    }

    private static BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10002L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.BOSS)
                .storeCodes(Set.of("STR245027-NAE"))
                .build();
    }

    private static final class InMemoryScopeRepository implements OperationConfigScopeRepository {
        private final List<OperationConfigStoreScope> stores = new ArrayList<>();

        void addStore(Long ownerUserId, String storeCode, String siteCode) {
            stores.add(new OperationConfigStoreScope(ownerUserId, 700L + stores.size(), "PRJ", "项目", storeCode, siteCode));
        }

        @Override
        public List<OperationConfigBossOption> listBossOptions() {
            return List.of();
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds) {
            return stores.stream()
                    .filter(store -> bossUserIds.contains(store.getOwnerUserId()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes) {
            return stores.stream()
                    .filter(store -> storeCodes.contains(store.getStoreCode()))
                    .collect(Collectors.toList());
        }
    }
}
