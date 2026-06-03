package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.sales.ProductLifecycleCalculationScope;
import com.nuono.next.sales.ProductLifecycleRuleSet;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationConfigProductLifecycleRuleProviderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exactCurrentScopeWinsOverGlobalCurrentAndUsesTypedLifecycleThresholds() throws JsonProcessingException {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.replaceWith(List.of(
                lifecycleVersion(
                        100L,
                        "LIFECYCLE_GLOBAL",
                        "全局生命周期",
                        "CURRENT",
                        "全局当前",
                        "全局复制",
                        LocalDateTime.of(2026, 5, 27, 9, 0),
                        "成长期最小销量环比增长率",
                        "0.6000"
                ),
                lifecycleVersion(
                        101L,
                        "LIFECYCLE_EXACT",
                        "精确生命周期",
                        "CURRENT",
                        "10002/STR245027-NAE/AE",
                        "店铺复制",
                        LocalDateTime.of(2026, 5, 27, 8, 0),
                        "成长期最小销量环比增长率",
                        "0.8000"
                )
        ));
        OperationConfigProductLifecycleRuleProvider provider =
                new OperationConfigProductLifecycleRuleProvider(repository);

        ProductLifecycleRuleSet ruleSet = provider.resolve(scope(" str245027-nae ", " ae "));

        assertEquals("LIFECYCLE_EXACT", ruleSet.getRuleVersion());
        assertEquals("LIFECYCLE_EXACT", ruleSet.getLifecycleVersionNo());
        assertEquals("精确生命周期", ruleSet.getLifecycleVersionName());
        assertEquals("店铺复制", ruleSet.getLifecycleVersionSourceLabel());
        assertFalse(ruleSet.isFallback());
        assertEquals(
                new BigDecimal("0.8000"),
                ruleSet.getThresholds().getGrowthMinSalesGrowthRate()
        );
    }

    @Test
    void fallsBackToDefaultV1WhenNoCurrentOrSystemVersionExists() {
        OperationConfigProductLifecycleRuleProvider provider =
                new OperationConfigProductLifecycleRuleProvider(new InMemoryOperationConfigTypedVersionRepository());

        ProductLifecycleRuleSet ruleSet = provider.resolve(scope("STR245027-NAE", "AE"));

        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO, ruleSet.getLifecycleVersionNo());
        assertTrue(ruleSet.isFallback());
    }

    @Test
    void globalCurrentWinsOverSystemDefault() throws JsonProcessingException {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.replaceWith(List.of(
                lifecycleVersion(
                        200L,
                        OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO,
                        "默认生命周期配置",
                        "SYSTEM_DEFAULT",
                        "全局默认",
                        "系统默认",
                        LocalDateTime.of(2026, 5, 25, 0, 0),
                        "成长期最小销量环比增长率",
                        "0.5000"
                ),
                lifecycleVersion(
                        201L,
                        "LIFECYCLE_GLOBAL_CURRENT",
                        "全局当前生命周期",
                        "CURRENT",
                        "全局当前",
                        "全局复制",
                        LocalDateTime.of(2026, 5, 27, 9, 0),
                        "成长期最小销量环比增长率",
                        "0.7000"
                )
        ));
        OperationConfigProductLifecycleRuleProvider provider =
                new OperationConfigProductLifecycleRuleProvider(repository);

        ProductLifecycleRuleSet ruleSet = provider.resolve(scope("STR245027-NAE", "AE"));

        assertEquals("LIFECYCLE_GLOBAL_CURRENT", ruleSet.getLifecycleVersionNo());
        assertFalse(ruleSet.isFallback());
        assertEquals(new BigDecimal("0.7000"), ruleSet.getThresholds().getGrowthMinSalesGrowthRate());
    }

    @Test
    void selectsNewestUpdatedAtThenLargestIdWithinSameVersionClass() throws JsonProcessingException {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        LocalDateTime sameUpdatedAt = LocalDateTime.of(2026, 5, 27, 9, 0);
        repository.replaceWith(List.of(
                lifecycleVersion(
                        300L,
                        "LIFECYCLE_GLOBAL_OLD",
                        "旧全局当前",
                        "CURRENT",
                        "全局当前",
                        "全局复制",
                        LocalDateTime.of(2026, 5, 27, 8, 0),
                        "成长期最小销量环比增长率",
                        "0.6000"
                ),
                lifecycleVersion(
                        301L,
                        "LIFECYCLE_GLOBAL_SAME_TIME_SMALL_ID",
                        "同更新时间小 ID",
                        "CURRENT",
                        "全局当前",
                        "全局复制",
                        sameUpdatedAt,
                        "成长期最小销量环比增长率",
                        "0.7000"
                ),
                lifecycleVersion(
                        302L,
                        "LIFECYCLE_GLOBAL_SAME_TIME_LARGE_ID",
                        "同更新时间大 ID",
                        "CURRENT",
                        "全局当前",
                        "全局复制",
                        sameUpdatedAt,
                        "成长期最小销量环比增长率",
                        "0.9000"
                )
        ));
        OperationConfigProductLifecycleRuleProvider provider =
                new OperationConfigProductLifecycleRuleProvider(repository);

        ProductLifecycleRuleSet ruleSet = provider.resolve(scope("STR245027-NAE", "AE"));

        assertEquals("LIFECYCLE_GLOBAL_SAME_TIME_LARGE_ID", ruleSet.getLifecycleVersionNo());
        assertEquals(new BigDecimal("0.9000"), ruleSet.getThresholds().getGrowthMinSalesGrowthRate());
    }

    private ProductLifecycleCalculationScope scope(String storeCode, String siteCode) {
        return new ProductLifecycleCalculationScope(
                10002L,
                storeCode,
                siteCode,
                LocalDate.of(2026, 5, 28),
                null,
                false
        );
    }

    private OperationConfigTypedVersion lifecycleVersion(
            Long id,
            String versionNo,
            String displayName,
            String status,
            String scopeSummary,
            String sourceLabel,
            LocalDateTime updatedAt,
            String itemName,
            String defaultValue
    ) throws JsonProcessingException {
        List<OperationConfigDefaultVersionItemView> items = List.of(new OperationConfigDefaultVersionItemView(
                "成长期",
                itemName,
                "随时",
                "数值",
                defaultValue,
                null,
                null
        ));
        return new OperationConfigTypedVersion(
                id,
                versionNo,
                displayName,
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                status,
                OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO,
                sourceLabel,
                items.size() + " 条生命周期配置",
                items.size(),
                scopeSummary,
                objectMapper.writeValueAsString(items),
                0L,
                0L,
                updatedAt,
                updatedAt
        );
    }
}
