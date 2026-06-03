package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationConfigTypedVersionContentSupportTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parsesDefaultCalendarCategoryFactorsIntoCalendarRules() throws JsonProcessingException {
        OperationConfigDefaultVersionCatalog catalog = new OperationConfigDefaultVersionCatalog();
        OperationConfigVersionDetailView detail = catalog.getDetail(
                OperationConfigDefaultVersionCatalog.DEFAULT_CALENDAR_VERSION_NO
        );
        OperationConfigTypedVersion version = new OperationConfigTypedVersion(
                88000L,
                detail.getVersionNo(),
                detail.getDisplayName(),
                detail.getConfigType(),
                detail.getStatus(),
                null,
                detail.getSourceLabel(),
                detail.getSummary(),
                detail.getItemCount(),
                detail.getScopeSummary(),
                objectMapper.writeValueAsString(detail.getItems()),
                0L,
                0L,
                LocalDateTime.of(2026, 5, 27, 0, 0),
                LocalDateTime.of(2026, 5, 27, 0, 0)
        );

        List<OperationCalendarRule> rules = OperationConfigTypedVersionContentSupport.calendarRules(
                version,
                1001L,
                "STR001",
                "AE",
                LocalDate.of(2026, 8, 20)
        );

        assertEquals(54, rules.size());
        assertTrue(rules.stream().anyMatch(rule ->
                "开学季模式".equals(rule.getRuleName())
                        && LocalDate.of(2026, 8, 17).equals(rule.getDateFrom())
                        && LocalDate.of(2026, 9, 7).equals(rule.getDateTo())
                        && "category".equals(rule.getTargetScopeType())
                        && "stationery-stationery".equals(rule.getTargetScopeValue())
                        && new BigDecimal("1.35").compareTo(rule.getFactorValue()) == 0
        ));
        assertTrue(rules.stream().anyMatch(rule ->
                "斋月 (Ramadan)".equals(rule.getRuleName())
                        && "all_products".equals(rule.getTargetScopeType())
                        && rule.getTargetScopeValue() == null
                        && new BigDecimal("0.85").compareTo(rule.getFactorValue()) == 0
        ));
    }

    @Test
    void lifecycleThresholdsParseFormulaClassifierParameters() throws JsonProcessingException {
        OperationConfigTypedVersion version = typedLifecycleVersion(List.of(
                item("成长期", "爆发惯性系数", "1.7"),
                item("成长期", "稳健系数", "1.08"),
                item("成长期", "阶梯增长倍数", "2.5"),
                item("成长期", "波动去极值比例", "0.2"),
                item("成长期", "波动增长动量阈值", "0.15"),
                item("衰退期", "衰退比例阈值", "0.75"),
                item("稳定期", "成熟期上升短期权重", "0.72"),
                item("稳定期", "成熟期下滑短期权重", "0.55")
        ));

        OperationLifecycleRuleThresholds thresholds = OperationConfigTypedVersionContentSupport.lifecycleThresholds(version);

        assertEquals(new BigDecimal("1.7000"), thresholds.getExplosiveInertiaFactor());
        assertEquals(new BigDecimal("1.0800"), thresholds.getSteadyTrendFactor());
        assertEquals(new BigDecimal("2.5000"), thresholds.getStepGrowthMultiplier());
        assertEquals(new BigDecimal("0.2000"), thresholds.getVolatileOutlierTrimRatio());
        assertEquals(new BigDecimal("0.1500"), thresholds.getVolatileMomentumThreshold());
        assertEquals(new BigDecimal("0.7500"), thresholds.getDeclineDecayRatioThreshold());
        assertEquals(new BigDecimal("0.7200"), thresholds.getStableRisingShortWeight());
        assertEquals(new BigDecimal("0.5500"), thresholds.getStableFallingShortWeight());
    }

    private OperationConfigTypedVersion typedLifecycleVersion(List<OperationConfigDefaultVersionItemView> items)
            throws JsonProcessingException {
        return new OperationConfigTypedVersion(
                88009L,
                "LIFECYCLE_CONFIG_88009",
                "生命周期配置",
                OperationConfigVersionType.PRODUCT_LIFECYCLE.name(),
                "CURRENT",
                OperationConfigDefaultVersionCatalog.DEFAULT_LIFECYCLE_VERSION_NO,
                "复制版本",
                items.size() + " 条 DEFAULT_V1 配置",
                items.size(),
                "全局当前",
                new ObjectMapper().writeValueAsString(items),
                0L,
                0L,
                LocalDateTime.of(2026, 5, 28, 0, 0),
                LocalDateTime.of(2026, 5, 28, 0, 0)
        );
    }

    private OperationConfigDefaultVersionItemView item(String groupName, String itemName, String defaultValue) {
        return new OperationConfigDefaultVersionItemView(
                groupName,
                itemName,
                "随时",
                "数值",
                defaultValue,
                null,
                null
        );
    }
}
