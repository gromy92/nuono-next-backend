package com.nuono.next.replenishmentplan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nuono.next.operationsconfig.InMemoryOperationConfigTypedVersionRepository;
import com.nuono.next.operationsconfig.OperationConfigDefaultVersionCatalog;
import com.nuono.next.operationsconfig.OperationConfigTypedVersion;
import com.nuono.next.operationsconfig.OperationConfigVersionType;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReplenishmentPlanConfigResolverTest {

    @Test
    void noCurrentTypedVersionReturnsDefaults() {
        ReplenishmentPlanConfigResolver resolver = new ReplenishmentPlanConfigResolver(
                new InMemoryOperationConfigTypedVersionRepository()
        );

        ReplenishmentPlanConfig config = resolver.resolve(50001L, "STR245027-NAE", "SA");

        assertDefaultConfig(config);
    }

    @Test
    void exactCurrentTypedVersionParsesChangedNumericFieldsAndVersionNo() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90001L,
                "REPLENISHMENT_OLD_EXACT",
                "CURRENT",
                "50001/STR245027-NAE/SA",
                content(
                        "空运运输天数", "11",
                        "空运覆盖天数", "16",
                        "海运运输天数", "66",
                        "海运覆盖天数", "31",
                        "预测窗口天数", "101",
                        "库存来源", "FBN,SUPERMALL",
                        "在途必须有 ETA", "true",
                        "空运只应急", "true",
                        "建议数量取整", "ceil"
                ),
                LocalDateTime.of(2026, 7, 6, 9, 0)
        ));
        repository.insert(version(
                90002L,
                "REPLENISHMENT_EXACT",
                "CURRENT",
                "50001/STR245027-NAE/SA",
                content(
                        "空运运输天数", "10",
                        "空运覆盖天数", "20",
                        "海运运输天数", "65",
                        "海运覆盖天数", "35",
                        "预测窗口天数", "110",
                        "库存来源", "fbn, supermall",
                        "在途必须有 ETA", "true",
                        "空运只应急", "true",
                        "建议数量取整", "ceil"
                ),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));
        ReplenishmentPlanConfigResolver resolver = new ReplenishmentPlanConfigResolver(repository);

        ReplenishmentPlanConfig config = resolver.resolve(50001L, " str245027-nae ", " sa ");

        assertEquals("REPLENISHMENT_EXACT", config.getVersionNo());
        assertEquals(10, config.getAirLeadDays());
        assertEquals(20, config.getAirCoverDays());
        assertEquals(65, config.getSeaLeadDays());
        assertEquals(35, config.getSeaCoverDays());
        assertEquals(110, config.getForecastHorizonDays());
        assertEquals(List.of("FBN", "SUPERMALL"), config.getInventorySources());
        assertEquals(true, config.isRequireInboundEtaDate());
        assertEquals(true, config.isAirEmergencyOnly());
        assertEquals("ceil", config.getRoundingMode());
    }

    @Test
    void globalCurrentFallbackWorksWhenExactMissing() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90003L,
                "REPLENISHMENT_GLOBAL",
                "CURRENT",
                "全局当前",
                content(
                        "空运运输天数", "13",
                        "空运覆盖天数", "17",
                        "海运运输天数", "72",
                        "海运覆盖天数", "33",
                        "预测窗口天数", "105",
                        "库存来源", "FBN,SUPERMALL",
                        "在途必须有 ETA", "true",
                        "空运只应急", "true",
                        "建议数量取整", "ceil"
                ),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));
        ReplenishmentPlanConfigResolver resolver = new ReplenishmentPlanConfigResolver(repository);

        ReplenishmentPlanConfig config = resolver.resolve(50001L, "STR245027-NAE", "SA");

        assertEquals("REPLENISHMENT_GLOBAL", config.getVersionNo());
        assertEquals(13, config.getAirLeadDays());
        assertEquals(17, config.getAirCoverDays());
        assertEquals(72, config.getSeaLeadDays());
        assertEquals(33, config.getSeaCoverDays());
        assertEquals(105, config.getForecastHorizonDays());
    }

    @Test
    void persistedSystemDefaultReplenishmentConfigIsUsedWhenCurrentVersionsAreMissing() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90005L,
                OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO,
                "SYSTEM_DEFAULT",
                "全局默认",
                content(
                        "空运运输天数", "14",
                        "空运覆盖天数", "18",
                        "海运运输天数", "75",
                        "海运覆盖天数", "40",
                        "预测窗口天数", "120",
                        "库存来源", "FBN,SUPERMALL",
                        "在途必须有 ETA", "true",
                        "空运只应急", "false",
                        "建议数量取整", "ceil"
                ),
                LocalDateTime.of(2026, 7, 6, 11, 0)
        ));
        ReplenishmentPlanConfigResolver resolver = new ReplenishmentPlanConfigResolver(repository);

        ReplenishmentPlanConfig config = resolver.resolve(50001L, "STR245027-NAE", "SA");

        assertEquals(OperationConfigDefaultVersionCatalog.DEFAULT_REPLENISHMENT_PLAN_VERSION_NO, config.getVersionNo());
        assertEquals(14, config.getAirLeadDays());
        assertEquals(18, config.getAirCoverDays());
        assertEquals(75, config.getSeaLeadDays());
        assertEquals(40, config.getSeaCoverDays());
        assertEquals(120, config.getForecastHorizonDays());
        assertEquals(false, config.isAirEmergencyOnly());
    }

    @Test
    void invalidContentFallsBackToDefaults() {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90004L,
                "REPLENISHMENT_INVALID",
                "CURRENT",
                "50001/STR245027-NAE/SA",
                "{\"itemName\":\"空运运输天数\",\"defaultValue\":\"0\"}",
                LocalDateTime.of(2026, 7, 6, 10, 0)
        ));
        ReplenishmentPlanConfigResolver resolver = new ReplenishmentPlanConfigResolver(repository);

        ReplenishmentPlanConfig config = resolver.resolve(50001L, "STR245027-NAE", "SA");

        assertDefaultConfig(config);
    }

    @Test
    void invalidPresentValuesFallBackToHardcodedDefaultsWithDefaultVersionNo() {
        assertInvalidPresentValueFallsBackToDefaults("空运运输天数", "0");
        assertInvalidPresentValueFallsBackToDefaults("库存来源", "FBN,AMAZON");
        assertInvalidPresentValueFallsBackToDefaults("空运只应急", "yes");
        assertInvalidPresentValueFallsBackToDefaults("建议数量取整", "floor");
    }

    private static void assertInvalidPresentValueFallsBackToDefaults(String itemName, String invalidValue) {
        InMemoryOperationConfigTypedVersionRepository repository = new InMemoryOperationConfigTypedVersionRepository();
        repository.insert(version(
                90006L + Math.abs(itemName.hashCode() % 1000),
                "REPLENISHMENT_INVALID_" + itemName.hashCode(),
                "CURRENT",
                "50001/STR245027-NAE/SA",
                contentWithOverride(itemName, invalidValue),
                LocalDateTime.of(2026, 7, 6, 12, 0)
        ));
        ReplenishmentPlanConfigResolver resolver = new ReplenishmentPlanConfigResolver(repository);

        ReplenishmentPlanConfig config = resolver.resolve(50001L, "STR245027-NAE", "SA");

        assertDefaultConfig(config);
    }

    private static OperationConfigTypedVersion version(
            Long id,
            String versionNo,
            String status,
            String scopeSummary,
            String contentJson,
            LocalDateTime updatedAt
    ) {
        return new OperationConfigTypedVersion(
                id,
                versionNo,
                "补货计划参数",
                OperationConfigVersionType.REPLENISHMENT_PLAN.name(),
                status,
                null,
                "运营",
                "补货计划参数",
                9,
                scopeSummary,
                contentJson,
                50001L,
                50001L,
                updatedAt.minusDays(1),
                updatedAt
        );
    }

    private static String content(String... nameValues) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < nameValues.length; index += 2) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"itemName\":\"")
                    .append(nameValues[index])
                    .append("\",\"defaultValue\":\"")
                    .append(nameValues[index + 1])
                    .append("\"}");
        }
        return json.append(']').toString();
    }

    private static String contentWithOverride(String itemName, String value) {
        return content(
                "空运运输天数", valueFor(itemName, "空运运输天数", value, "12"),
                "空运覆盖天数", valueFor(itemName, "空运覆盖天数", value, "15"),
                "海运运输天数", valueFor(itemName, "海运运输天数", value, "70"),
                "海运覆盖天数", valueFor(itemName, "海运覆盖天数", value, "30"),
                "预测窗口天数", valueFor(itemName, "预测窗口天数", value, "100"),
                "库存来源", valueFor(itemName, "库存来源", value, "FBN,SUPERMALL"),
                "在途必须有 ETA", valueFor(itemName, "在途必须有 ETA", value, "true"),
                "空运只应急", valueFor(itemName, "空运只应急", value, "true"),
                "建议数量取整", valueFor(itemName, "建议数量取整", value, "ceil")
        );
    }

    private static String valueFor(String itemName, String candidateName, String overrideValue, String defaultValue) {
        return candidateName.equals(itemName) ? overrideValue : defaultValue;
    }

    private static void assertDefaultConfig(ReplenishmentPlanConfig config) {
        ReplenishmentPlanConfig defaults = ReplenishmentPlanConfig.defaultBasicV1();
        assertEquals(defaults.getVersionNo(), config.getVersionNo());
        assertEquals(defaults.getAirLeadDays(), config.getAirLeadDays());
        assertEquals(defaults.getAirCoverDays(), config.getAirCoverDays());
        assertEquals(defaults.getSeaLeadDays(), config.getSeaLeadDays());
        assertEquals(defaults.getSeaCoverDays(), config.getSeaCoverDays());
        assertEquals(defaults.getForecastHorizonDays(), config.getForecastHorizonDays());
        assertEquals(defaults.getInventorySources(), config.getInventorySources());
        assertEquals(defaults.isRequireInboundEtaDate(), config.isRequireInboundEtaDate());
        assertEquals(defaults.isAirEmergencyOnly(), config.isAirEmergencyOnly());
        assertEquals(defaults.getRoundingMode(), config.getRoundingMode());
    }
}
