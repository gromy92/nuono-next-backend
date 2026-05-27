package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.logisticsquote.LogisticsCargoCategoryFact;
import com.nuono.next.logisticsquote.LogisticsQuoteFactSourceLineage;
import com.nuono.next.logisticsquote.LogisticsQuoteFactStatus;
import com.nuono.next.logisticsquote.LogisticsRestrictionRuleFact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class LogisticsRestrictionEvaluatorTest {

    private final LogisticsRestrictionEvaluator evaluator = new LogisticsRestrictionEvaluator();

    @Test
    void ordinaryCargoPassesWithoutRisk() {
        LogisticsRestrictionEvaluator.EvaluationResult result = evaluator.evaluate(
                cargo("ordinary", "23", "13", "5", "100", 1),
                category("normal", "普货", false),
                List.of()
        );

        assertFalse(result.isHardRestricted());
        assertTrue(result.getRiskPrompts().isEmpty());
    }

    @Test
    void batterySensitiveCargoHardRestrictionExcludesServiceLine() {
        LogisticsRestrictionEvaluator.EvaluationResult result = evaluator.evaluate(
                cargo("battery,sensitive", "23", "13", "5", "100", 1),
                category("battery", "带电敏感货", false),
                List.of(restriction("battery", "带电/敏感货禁运", "hard", false))
        );

        assertTrue(result.isHardRestricted());
        assertEquals(1, result.getRiskPrompts().size());
        assertEquals("hard", result.getRiskPrompts().get(0).getSeverity());
        assertTrue(result.getRiskPrompts().get(0).getMessage().contains("带电/敏感货禁运"));
    }

    @Test
    void warningRestrictionStaysVisibleButDoesNotExclude() {
        LogisticsRestrictionEvaluator.EvaluationResult result = evaluator.evaluate(
                cargo("magnetic", "23", "13", "5", "100", 1),
                category("normal", "普货", false),
                List.of(restriction("magnetic", "磁性货需要提前确认包装", "warning", false))
        );

        assertFalse(result.isHardRestricted());
        assertEquals(1, result.getRiskPrompts().size());
        assertEquals("warning", result.getRiskPrompts().get(0).getSeverity());
    }

    @Test
    void manualConfirmCargoReturnsManualConfirmationRisk() {
        LogisticsRestrictionEvaluator.EvaluationResult result = evaluator.evaluate(
                cargo("ordinary", "23", "13", "5", "100", 1),
                category("manual", "需人工确认分类", true),
                List.of(restriction("normal", "该分类需货代人工确认", "info", true))
        );

        assertFalse(result.isHardRestricted());
        assertEquals(2, result.getRiskPrompts().size());
        assertTrue(result.getRiskPrompts().stream().anyMatch(prompt -> prompt.isManualConfirmRequired()));
    }

    @Test
    void oversizedCargoHardRestrictionExcludesServiceLine() {
        LogisticsRestrictionEvaluator.EvaluationResult result = evaluator.evaluate(
                cargo("ordinary", "60", "40", "30", "1000", 1),
                category("normal", "普货", false),
                List.of(new LogisticsRestrictionRuleFact(
                        "oversized",
                        "yite",
                        "line",
                        "oversized",
                        "超尺寸",
                        "单边超过 50cm 禁运",
                        "oversized",
                        "hard",
                        false,
                        LogisticsQuoteFactStatus.ACTIVE.value(),
                        lineage()
                ))
        );

        assertTrue(result.isHardRestricted());
        assertEquals("hard", result.getRiskPrompts().get(0).getSeverity());
        assertTrue(result.getRiskPrompts().get(0).getMessage().contains("单边超过 50cm 禁运"));
    }

    private static LogisticsRestrictionEvaluator.CargoFacts cargo(
            String cargoAttributes,
            String lengthCm,
            String widthCm,
            String heightCm,
            String unitWeightGrams,
            int quantity
    ) {
        return new LogisticsRestrictionEvaluator.CargoFacts(
                cargoAttributes,
                new BigDecimal(lengthCm),
                new BigDecimal(widthCm),
                new BigDecimal(heightCm),
                new BigDecimal(unitWeightGrams),
                quantity
        );
    }

    private static LogisticsCargoCategoryFact category(String code, String name, boolean manualConfirmRequired) {
        return new LogisticsCargoCategoryFact(
                "category|" + code,
                "yite",
                "line",
                code,
                name,
                name,
                null,
                null,
                null,
                null,
                null,
                manualConfirmRequired,
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage()
        );
    }

    private static LogisticsRestrictionRuleFact restriction(
            String restrictionType,
            String itemText,
            String severity,
            boolean manualConfirmRequired
    ) {
        return new LogisticsRestrictionRuleFact(
                "restriction|" + restrictionType,
                "yite",
                "line",
                restrictionType,
                itemText,
                itemText,
                restrictionType,
                severity,
                manualConfirmRequired,
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage()
        );
    }

    private static LogisticsQuoteFactSourceLineage lineage() {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                20076L,
                40054L,
                70054L,
                3004L,
                "quote.pdf",
                "page 1"
        );
    }
}
