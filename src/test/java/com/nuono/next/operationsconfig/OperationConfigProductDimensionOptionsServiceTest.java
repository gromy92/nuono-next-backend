package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.product.ProductClassificationOptionRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class OperationConfigProductDimensionOptionsServiceTest {

    @Test
    void returnsBrandFulltypeAndDerivedCategoryOptionsFromProductManagementPersistenceForAuthorizedScope() {
        InMemoryScopeRepository scopeRepository = new InMemoryScopeRepository();
        scopeRepository.addStore(501L, "STR-X-NAE", "AE");
        InMemoryProductDimensionRepository dimensionRepository = new InMemoryProductDimensionRepository();
        dimensionRepository.brands.add(option("Acme", "Acme", 3));
        dimensionRepository.fulltypes.add(fulltypeOption("home-bedding-duvet", "home", "bedding", 2));
        dimensionRepository.fulltypes.add(fulltypeOption("home-bedding-pillow", "home", "bedding", 4));
        OperationConfigProductDimensionOptionsService service = new OperationConfigProductDimensionOptionsService(
                new OperationConfigScopeService(scopeRepository),
                dimensionRepository
        );

        OperationConfigProductDimensionOptionsView view = service.options(
                operatorContext(),
                List.of(),
                501L,
                "STR-X-NAE",
                "AE",
                "ac",
                "home",
                10
        );

        assertEquals(true, view.isReady());
        assertEquals("product_management", view.getSource());
        assertEquals("Acme", view.getBrands().get(0).getValue());
        assertEquals("home-bedding-duvet", view.getProductFulltypes().get(0).getValue());
        assertEquals("home-bedding", firstCategoryValue(view));
        assertEquals(501L, dimensionRepository.lastOwnerUserId);
        assertEquals("STR-X-NAE", dimensionRepository.lastStoreCode);
    }

    @SuppressWarnings("unchecked")
    private static String firstCategoryValue(OperationConfigProductDimensionOptionsView view) {
        Map<String, Object> payload = new ObjectMapper().convertValue(view, Map.class);
        Object categoriesValue = payload.getOrDefault("categories", List.of());
        if (!(categoriesValue instanceof List<?>)) {
            return null;
        }
        List<?> categories = (List<?>) categoriesValue;
        if (categories.isEmpty()) {
            return null;
        }
        Object first = categories.get(0);
        if (!(first instanceof Map<?, ?>)) {
            return null;
        }
        Map<?, ?> category = (Map<?, ?>) first;
        Object value = category.get("value");
        return value == null ? null : String.valueOf(value);
    }

    private static ProductClassificationOptionRecord option(String value, String label, int usageCount) {
        ProductClassificationOptionRecord record = new ProductClassificationOptionRecord();
        record.setValue(value);
        record.setLabel(label);
        record.setUsageCount(usageCount);
        return record;
    }

    private static ProductClassificationOptionRecord fulltypeOption(
            String value,
            String family,
            String productType,
            int usageCount
    ) {
        ProductClassificationOptionRecord record = option(value, value, usageCount);
        record.setFamily(family);
        record.setProductType(productType);
        return record;
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

    private static class InMemoryScopeRepository implements OperationConfigScopeRepository {
        private final List<OperationConfigStoreScope> stores = new ArrayList<>();

        void addStore(Long ownerUserId, String storeCode, String siteCode) {
            stores.add(new OperationConfigStoreScope(ownerUserId, 701L, "PRJ-X", "Xingyao", storeCode, siteCode));
        }

        @Override
        public List<OperationConfigBossOption> listBossOptions() {
            return List.of();
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByBossUserIds(List<Long> bossUserIds) {
            return stores;
        }

        @Override
        public List<OperationConfigStoreScope> listStoreSitesByStoreCodes(Set<String> storeCodes) {
            return stores.stream().filter(store -> storeCodes.contains(store.getStoreCode())).collect(Collectors.toList());
        }
    }

    private static class InMemoryProductDimensionRepository implements OperationConfigProductDimensionRepository {
        private final List<ProductClassificationOptionRecord> brands = new ArrayList<>();
        private final List<ProductClassificationOptionRecord> fulltypes = new ArrayList<>();
        private Long lastOwnerUserId;
        private String lastStoreCode;

        @Override
        public List<ProductClassificationOptionRecord> listBrandOptions(
                Long ownerUserId,
                String storeCode,
                String query,
                int limit
        ) {
            lastOwnerUserId = ownerUserId;
            lastStoreCode = storeCode;
            return brands;
        }

        @Override
        public List<ProductClassificationOptionRecord> listProductFulltypeOptions(
                Long ownerUserId,
                String storeCode,
                String query,
                int limit
        ) {
            lastOwnerUserId = ownerUserId;
            lastStoreCode = storeCode;
            return fulltypes;
        }
    }
}
