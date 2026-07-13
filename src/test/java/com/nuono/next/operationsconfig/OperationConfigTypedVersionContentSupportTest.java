package com.nuono.next.operationsconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    void resolvesEffectiveTypedCalendarVersionByExactScopeThenGlobalThenDefault() {
        InMemoryTypedVersionRepository repository = new InMemoryTypedVersionRepository();
        repository.insert(version(
                88001L,
                "DEFAULT_CALENDAR_CONFIG",
                "默认日历配置",
                "SYSTEM_DEFAULT",
                "全局默认",
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        repository.insert(version(
                88002L,
                "CALENDAR_GLOBAL",
                "全局当前日历",
                "CURRENT",
                "全局当前",
                LocalDateTime.of(2026, 5, 1, 0, 0)
        ));
        repository.insert(version(
                88003L,
                "CALENDAR_EXACT",
                "canman SA 日历",
                "CURRENT",
                "10002/STR108065-NSA/SA",
                LocalDateTime.of(2026, 5, 2, 0, 0)
        ));

        Optional<OperationConfigTypedVersion> exact = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                repository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                10002L,
                "str108065-nsa",
                "sa"
        );
        Optional<OperationConfigTypedVersion> global = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                repository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                10002L,
                "OTHER",
                "SA"
        );
        InMemoryTypedVersionRepository defaultRepository = new InMemoryTypedVersionRepository();
        defaultRepository.insert(version(
                88004L,
                "DEFAULT_CALENDAR_CONFIG",
                "默认日历配置",
                "SYSTEM_DEFAULT",
                "全局默认",
                LocalDateTime.of(2026, 1, 1, 0, 0)
        ));
        Optional<OperationConfigTypedVersion> fallback = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                defaultRepository,
                OperationConfigVersionType.BUSINESS_CALENDAR,
                10002L,
                "OTHER",
                "SA"
        );

        assertTrue(exact.isPresent());
        assertTrue(global.isPresent());
        assertTrue(fallback.isPresent());
        assertEquals("CALENDAR_EXACT", exact.get().getVersionNo());
        assertEquals("canman SA 日历", exact.get().getDisplayName());
        assertEquals("typed_version", exact.get().getSourceLabel());
        assertEquals("CALENDAR_GLOBAL", global.get().getVersionNo());
        assertEquals("DEFAULT_CALENDAR_CONFIG", fallback.get().getVersionNo());
    }

    @Test
    void calendarFactorResolverUsesFamilyRuleBeforeSiteFallbackAndAveragesWindow() {
        InMemoryTypedVersionRepository repository = new InMemoryTypedVersionRepository();
        repository.insert(new OperationConfigTypedVersion(
                88020L,
                "CALENDAR_FACTOR_CURRENT",
                "站点类目日历因子",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                null,
                "运营配置",
                "站点类目因子",
                2,
                "10002/STR108065-NSA/SA",
                "["
                        + "{\"groupName\":\"黑五\",\"itemName\":\"SA paper 黑五\",\"cadence\":\"年度\",\"valueType\":\"日期范围\",\"defaultValue\":\"2026-11-01 ~ 2026-11-02 / 1.50\",\"resultShape\":\"site:SA|family:paper\",\"note\":\"高置信\"},"
                        + "{\"groupName\":\"黑五\",\"itemName\":\"SA 全品黑五\",\"cadence\":\"年度\",\"valueType\":\"日期范围\",\"defaultValue\":\"2026-11-01 ~ 2026-11-02 / 1.20\",\"resultShape\":\"site:SA\",\"note\":\"低置信兜底\"}"
                        + "]",
                0L,
                0L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        ));
        OperationBusinessCalendarFactorResolver resolver = new OperationBusinessCalendarFactorResolver(repository);

        OperationBusinessCalendarFactorResolver.ProductScope paper = new OperationBusinessCalendarFactorResolver.ProductScope(
                "SA",
                "PAPERSAY",
                "copy_multipurpose_paper",
                "paper"
        );
        OperationBusinessCalendarFactorResolver.ProductScope otherFamily = new OperationBusinessCalendarFactorResolver.ProductScope(
                "SA",
                "PAPERSAY",
                "postal_scales",
                "electronics"
        );

        assertEquals(
                "1.3333",
                resolver.averageFactor(
                        10002L,
                        "STR108065-NSA",
                        "SA",
                        LocalDate.of(2026, 10, 31),
                        3,
                        paper
                ).setScale(4, RoundingMode.HALF_UP).toPlainString()
        );
        assertEquals(
                "1.1333",
                resolver.averageFactor(
                        10002L,
                        "STR108065-NSA",
                        "SA",
                        LocalDate.of(2026, 10, 31),
                        3,
                        otherFamily
                ).setScale(4, RoundingMode.HALF_UP).toPlainString()
        );
    }

    @Test
    void calendarFactorResolverExplainsMatchedRulesForProductScope() {
        InMemoryTypedVersionRepository repository = new InMemoryTypedVersionRepository();
        repository.insert(new OperationConfigTypedVersion(
                88021L,
                "CALENDAR_FACTOR_CURRENT",
                "站点类目日历因子",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                null,
                "运营配置",
                "站点类目因子",
                2,
                "10002/STR108065-NSA/SA",
                "["
                        + "{\"groupName\":\"黑五\",\"itemName\":\"SA paper 黑五\",\"cadence\":\"年度\",\"valueType\":\"日期范围\",\"defaultValue\":\"2026-11-01 ~ 2026-11-02 / 1.50\",\"resultShape\":\"site:SA|family:paper\",\"note\":\"高置信\"},"
                        + "{\"groupName\":\"黑五\",\"itemName\":\"SA 全品黑五\",\"cadence\":\"年度\",\"valueType\":\"日期范围\",\"defaultValue\":\"2026-11-01 ~ 2026-11-02 / 1.20\",\"resultShape\":\"site:SA\",\"note\":\"低置信兜底\"}"
                        + "]",
                0L,
                0L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        ));
        OperationBusinessCalendarFactorResolver resolver = new OperationBusinessCalendarFactorResolver(repository);

        OperationBusinessCalendarFactorResolver.CalendarFactorExplanation explanation = resolver.explainFactors(
                10002L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 10, 31),
                90,
                new OperationBusinessCalendarFactorResolver.ProductScope(
                        "SA",
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper"
                )
        );

        assertEquals("1.0111", explanation.averageFactor(90).setScale(4, RoundingMode.HALF_UP).toPlainString());
        assertEquals(1, explanation.getImpacts().size());
        OperationBusinessCalendarFactorResolver.CalendarFactorImpact impact = explanation.getImpacts().get(0);
        assertEquals("SA paper 黑五", impact.getRuleName());
        assertEquals("site", impact.getTargetScopeType());
        assertEquals("SA|family:paper", impact.getTargetScopeValue());
        assertEquals("站点+大类目", impact.getMatchedScopeLabel());
        assertEquals("1.5000", impact.getFactorValue().setScale(4, RoundingMode.HALF_UP).toPlainString());
        assertEquals(2, impact.getAffectedDays30());
        assertEquals(2, impact.getAffectedDays60());
        assertEquals(2, impact.getAffectedDays90());
    }

    @Test
    void resolverCountsCalendarFactorImpactsAcrossOneHundredTwentyForecastDays() {
        InMemoryTypedVersionRepository repository = new InMemoryTypedVersionRepository();
        repository.insert(new OperationConfigTypedVersion(
                88023L,
                "CALENDAR_FACTOR_120_DAY_HORIZON",
                "120天日历因子",
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                null,
                "运营配置",
                "120天因子",
                2,
                "10002/STR108065-NSA/SA",
                "["
                        + "{\"groupName\":\"Ramadan\",\"itemName\":\"SA paper Ramadan\",\"cadence\":\"年度\",\"valueType\":\"日期范围\",\"defaultValue\":\"2027-02-05 ~ 2027-02-07 / 1.30\",\"resultShape\":\"site:SA|family:paper\",\"note\":\"高置信\"}"
                        + "]",
                0L,
                0L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        ));
        OperationBusinessCalendarFactorResolver resolver = new OperationBusinessCalendarFactorResolver(repository);

        OperationBusinessCalendarFactorResolver.CalendarFactorExplanation explanation = resolver.explainFactors(
                10002L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 10, 31),
                120,
                new OperationBusinessCalendarFactorResolver.ProductScope(
                        "SA",
                        "PAPERSAY",
                        "copy_multipurpose_paper",
                        "paper"
                )
        );

        assertEquals(120, explanation.getDailyFactors().size());
        assertEquals(1, explanation.getImpacts().size());
        OperationBusinessCalendarFactorResolver.CalendarFactorImpact impact = explanation.getImpacts().get(0);
        assertEquals(0, impact.getAffectedDays30());
        assertEquals(0, impact.getAffectedDays60());
        assertEquals(0, impact.getAffectedDays90());
        assertEquals(3, impact.getAffectedDays120());
    }

    @Test
    void calendarRulesParseIntegerFactorAfterDateRangeDelimiter() {
        OperationConfigTypedVersion version = versionWithContent(
                88022L,
                "CALENDAR_INTEGER_FACTOR",
                "[{\"groupName\":\"黑五\",\"itemName\":\"SA paper 黑五\",\"cadence\":\"年度\",\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-11-20 ~ 2026-11-30 / 2\",\"resultShape\":\"site:SA|family:paper\",\"note\":\"高置信\"}]"
        );

        List<OperationCalendarRule> rules = OperationConfigTypedVersionContentSupport.calendarRules(
                version,
                10002L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 11, 1)
        );

        assertEquals(1, rules.size());
        assertEquals("2", rules.get(0).getFactorValue().toPlainString());
        assertEquals(LocalDate.of(2026, 11, 20), rules.get(0).getDateFrom());
        assertEquals(LocalDate.of(2026, 11, 30), rules.get(0).getDateTo());
    }

    @Test
    void calendarFactorResolverUsesLargestImpactWhenRulesOverlap() {
        InMemoryTypedVersionRepository repository = new InMemoryTypedVersionRepository();
        repository.insert(versionWithContent(
                88023L,
                "CALENDAR_TIE_FACTOR",
                "["
                        + "{\"groupName\":\"薪酬日\",\"itemName\":\"薪酬日\",\"cadence\":\"月度\",\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-05-26 ~ 2026-05-30 / 1.10\",\"resultShape\":\"site:SA\",\"note\":\"稳定测算\"},"
                        + "{\"groupName\":\"节日\",\"itemName\":\"古尔邦节\",\"cadence\":\"年度\",\"valueType\":\"日期范围/系数\",\"defaultValue\":\"2026-05-26 ~ 2026-05-29 / 0.70\",\"resultShape\":\"site:SA\",\"note\":\"历史测算\"}"
                        + "]"
        ));
        OperationBusinessCalendarFactorResolver resolver = new OperationBusinessCalendarFactorResolver(repository);

        OperationBusinessCalendarFactorResolver.CalendarFactorExplanation explanation = resolver.explainFactors(
                10002L,
                "STR108065-NSA",
                "SA",
                LocalDate.of(2026, 5, 25),
                1,
                new OperationBusinessCalendarFactorResolver.ProductScope("SA", "PAPERSAY", "copy_multipurpose_paper", "paper")
        );

        assertEquals("0.7000", explanation.averageFactor(1).setScale(4, RoundingMode.HALF_UP).toPlainString());
        assertEquals("古尔邦节", explanation.getImpacts().get(0).getRuleName());
    }

    @Test
    void resolveEffectiveVersionUsesReplenishmentSystemDefaultVersionNo() {
        InMemoryTypedVersionRepository repository = new InMemoryTypedVersionRepository();
        repository.insert(new OperationConfigTypedVersion(
                99001L,
                OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                "SYSTEM_DEFAULT",
                null,
                "系统默认",
                "1 条配置",
                1,
                "全局默认",
                "[]",
                0L,
                0L,
                LocalDateTime.of(2026, 7, 6, 10, 0),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));

        Optional<OperationConfigTypedVersion> resolved = OperationConfigTypedVersionContentSupport.resolveEffectiveVersion(
                repository,
                OperationConfigVersionType.REPLENISHMENT_PLAN,
                307L,
                "STR108065-NAE",
                "SA"
        );

        assertTrue(resolved.isPresent());
        assertEquals(
                OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                resolved.get().getVersionNo()
        );
    }

    private OperationConfigTypedVersion version(
            Long id,
            String versionNo,
            String displayName,
            String status,
            String scopeSummary,
            LocalDateTime updatedAt
    ) {
        return new OperationConfigTypedVersion(
                id,
                versionNo,
                displayName,
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                status,
                null,
                "typed_version",
                "summary",
                0,
                scopeSummary,
                "[]",
                0L,
                0L,
                updatedAt,
                updatedAt
        );
    }

    private OperationConfigTypedVersion versionWithContent(Long id, String versionNo, String contentJson) {
        return new OperationConfigTypedVersion(
                id,
                versionNo,
                versionNo,
                OperationConfigVersionType.BUSINESS_CALENDAR.name(),
                "CURRENT",
                null,
                "typed_version",
                "summary",
                1,
                "10002/STR108065-NSA/SA",
                contentJson,
                0L,
                0L,
                LocalDateTime.of(2026, 6, 1, 0, 0),
                LocalDateTime.of(2026, 6, 1, 0, 0)
        );
    }

    private static class InMemoryTypedVersionRepository implements OperationConfigTypedVersionRepository {
        private final List<OperationConfigTypedVersion> versions = new ArrayList<>();

        @Override
        public Long nextVersionId() {
            return 88000L + versions.size();
        }

        @Override
        public void insert(OperationConfigTypedVersion version) {
            versions.add(version);
        }

        @Override
        public List<OperationConfigTypedVersion> listVersions() {
            return versions;
        }

        @Override
        public Optional<OperationConfigTypedVersion> findByVersionNo(String versionNo) {
            return versions.stream()
                    .filter(version -> version.getVersionNo().equals(versionNo))
                    .findFirst();
        }

        @Override
        public void update(OperationConfigTypedVersion version) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByVersionNo(String versionNo) {
            throw new UnsupportedOperationException();
        }
    }
}
