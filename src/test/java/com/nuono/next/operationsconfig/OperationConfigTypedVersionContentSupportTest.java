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
}
