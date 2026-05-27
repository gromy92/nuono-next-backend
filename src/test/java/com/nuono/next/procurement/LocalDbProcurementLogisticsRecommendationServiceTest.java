package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.infrastructure.mapper.ProcurementLogisticsRequirementMapper;
import com.nuono.next.logisticsquote.LogisticsCargoCategoryFact;
import com.nuono.next.logisticsquote.LogisticsPriceRuleFact;
import com.nuono.next.logisticsquote.LogisticsQuoteComparisonQuery;
import com.nuono.next.logisticsquote.LogisticsQuoteFactRepository;
import com.nuono.next.logisticsquote.LogisticsQuoteFactSourceLineage;
import com.nuono.next.logisticsquote.LogisticsQuoteFactStatus;
import com.nuono.next.logisticsquote.LogisticsRestrictionRuleFact;
import com.nuono.next.logisticsquote.LogisticsServiceLineFact;
import com.nuono.next.logisticsquote.LogisticsServiceLineQuery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalDbProcurementLogisticsRecommendationServiceTest {

    @Test
    void recommendsAirForwarderFromActiveFacts() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "air",
                "义特空运小包",
                5,
                8
        ));
        fixture.repository.addServiceLine(serviceLine(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et",
                "ET物流",
                "air",
                "ET空运快线",
                4,
                7
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addCategory(category(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addPrice(price(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|kg",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "64",
                "kg"
        ));
        fixture.repository.addPrice(price(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|kg",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "61",
                "kg"
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("air", view.getTransportMode());
        assertEquals("KSA", view.getDestinationCountry());
        assertEquals("KSA/Riyadh", view.getDestinationNode());
        assertEquals("et", view.getForwarderCode());
        assertEquals("ET物流", view.getForwarderName());
        assertEquals("et|KSA|air|warehouse_to_fbn|KSA/Riyadh", view.getServiceLineKey());
        assertEquals("ET空运快线", view.getServiceLineName());
        assertEquals("et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal", view.getMatchedCargoCategoryKey());
        assertEquals("普货", view.getMatchedCargoCategoryName());
        assertEquals(new BigDecimal("61"), view.getEstimatedBaseCost());
        assertEquals("SAR", view.getCurrency());
        assertEquals("4-7 天", view.getLeadTimeText());
        assertTrue(view.getWinnerReason().contains("ET物流"));
        assertTrue(view.getWinnerReason().contains("61"));
        assertEquals("quote.pdf", view.getSourceEvidence().get(0).getSourceFileName());
        assertEquals("page 1", view.getSourceEvidence().get(0).getSourceLocator());
        assertEquals(2, view.getComparedOptions().size());
    }

    @Test
    void recommendsSeaForwarderFromActiveFacts() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("sea");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "sea",
                "义特海运双清",
                25,
                35
        ));
        fixture.repository.addServiceLine(serviceLine(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "et",
                "ET物流",
                "sea",
                "ET海运慢船",
                28,
                40
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addCategory(category(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "et",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addPrice(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "120",
                "cbm"
        ));
        fixture.repository.addPrice(price(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "et",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "135",
                "cbm"
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("sea", view.getTransportMode());
        assertEquals("yite", view.getForwarderCode());
        assertEquals("义特物流", view.getForwarderName());
        assertEquals("义特海运双清", view.getServiceLineName());
        assertEquals(new BigDecimal("120"), view.getEstimatedBaseCost());
        assertEquals("25-35 天", view.getLeadTimeText());
    }

    @Test
    void returnsNoAvailableQuoteWhenNoActiveLineMatchesTransportMode() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "sea",
                "义特海运双清",
                25,
                35
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("no_available_quote", view.getStatus());
        assertEquals("air", view.getTransportMode());
        assertNull(view.getForwarderCode());
        assertTrue(view.getMessage().contains("没有匹配空运"));
    }

    @Test
    void doesNotCompareOtherTransportModes() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "sea",
                "义特海运双清",
                25,
                35
        ));
        fixture.repository.addServiceLine(serviceLine(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et",
                "ET物流",
                "air",
                "ET空运快线",
                4,
                7
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addCategory(category(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addPrice(price(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "1",
                "cbm"
        ));
        fixture.repository.addPrice(price(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|kg",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "70",
                "kg"
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("air", view.getTransportMode());
        assertEquals("et", view.getForwarderCode());
        assertEquals(new BigDecimal("70"), view.getEstimatedBaseCost());
        assertEquals(1, view.getComparedOptions().size());
        assertEquals("et", view.getComparedOptions().get(0).getForwarderCode());
    }

    @Test
    void estimatesShipmentCostBeforeSelectingWinner() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("sea");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "sea",
                "义特海运双清",
                25,
                35
        ));
        fixture.repository.addServiceLine(serviceLine(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "et",
                "ET物流",
                "sea",
                "ET海运慢船",
                28,
                40
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addCategory(category(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "et",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addPrice(priceWithRules(
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "yite",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "100",
                "cbm",
                null,
                null,
                "1000",
                null,
                null,
                null,
                "NORMAL"
        ));
        fixture.repository.addPrice(price(
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal|cbm",
                "et",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|sea|warehouse_to_fbn|KSA/Riyadh|normal",
                "200",
                "cbm"
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("et", view.getForwarderCode());
        assertEquals(new BigDecimal("90.00"), view.getEstimatedCost());
        assertEquals(new BigDecimal("0.45"), view.getChargeableUnit());
        assertEquals("cbm", view.getBillingUnit());
        assertTrue(view.getCalculationBreakdown().contains("体积 0.449 cbm"));
        assertEquals("yite", view.getComparedOptions().get(1).getForwarderCode());
        assertEquals(new BigDecimal("1000.00"), view.getComparedOptions().get(1).getEstimatedCost());
        assertTrue(view.getComparedOptions().get(1).getReason().contains("最低收费 1000"));
    }

    @Test
    void keepsNonComputablePricesOutOfFinalRecommendation() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "air",
                "义特空运小包",
                5,
                8
        ));
        fixture.repository.addServiceLine(serviceLine(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et",
                "ET物流",
                "air",
                "ET空运快线",
                4,
                7
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addCategory(category(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addPrice(priceWithRules(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|kg",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                null,
                "kg",
                null,
                null,
                null,
                "6000",
                null,
                null,
                "ASK_QUOTE"
        ));
        fixture.repository.addPrice(price(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|kg",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "70",
                "kg"
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("et", view.getForwarderCode());
        assertEquals(2, view.getComparedOptions().size());
        assertEquals("yite", view.getComparedOptions().get(1).getForwarderCode());
        assertNull(view.getComparedOptions().get(1).getEstimatedCost());
        assertTrue(view.getComparedOptions().get(1).getReason().contains("人工询价"));
    }

    @Test
    void hardRestrictionExcludesServiceLineAndRecommendsNextSafeOption() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air", "battery,sensitive");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "air",
                "义特空运小包",
                5,
                8
        ));
        fixture.repository.addServiceLine(serviceLine(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et",
                "ET物流",
                "air",
                "ET空运快线",
                4,
                7
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "battery",
                "带电敏感货"
        ));
        fixture.repository.addCategory(category(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "battery",
                "带电敏感货"
        ));
        fixture.repository.addPrice(price(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery|kg",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "40",
                "kg"
        ));
        fixture.repository.addPrice(price(
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery|kg",
                "et",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "et|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "70",
                "kg"
        ));
        fixture.repository.addRestriction(restriction(
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "battery",
                "带电/敏感货禁运",
                "hard",
                false
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("et", view.getForwarderCode());
        assertEquals(2, view.getComparedOptions().size());
        assertEquals("yite", view.getComparedOptions().get(1).getForwarderCode());
        assertTrue(view.getComparedOptions().get(1).getReason().contains("带电/敏感货禁运"));
    }

    @Test
    void allRestrictedReturnsNoSafeAutomaticRecommendation() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air", "battery,sensitive");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "air",
                "义特空运小包",
                5,
                8
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "battery",
                "带电敏感货"
        ));
        fixture.repository.addPrice(price(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery|kg",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|battery",
                "40",
                "kg"
        ));
        fixture.repository.addRestriction(restriction(
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "battery",
                "带电/敏感货禁运",
                "hard",
                false
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("no_safe_automatic_recommendation", view.getStatus());
        assertNull(view.getForwarderCode());
        assertTrue(view.getMessage().contains("没有安全"));
        assertEquals(1, view.getComparedOptions().size());
        assertTrue(view.getComparedOptions().get(0).getReason().contains("带电/敏感货禁运"));
    }

    @Test
    void warningRestrictionStaysVisibleOnRecommendedResult() {
        TestFixture fixture = new TestFixture();
        fixture.saveRequirement("air", "magnetic");
        fixture.repository.addServiceLine(serviceLine(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite",
                "义特物流",
                "air",
                "义特空运小包",
                5,
                8
        ));
        fixture.repository.addCategory(category(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "normal",
                "普货"
        ));
        fixture.repository.addPrice(price(
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal|kg",
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh|normal",
                "70",
                "kg"
        ));
        fixture.repository.addRestriction(restriction(
                "yite",
                "yite|KSA|air|warehouse_to_fbn|KSA/Riyadh",
                "magnetic",
                "磁性货需要提前确认包装",
                "warning",
                false
        ));

        ProcurementLogisticsRecommendationView view = fixture.service.recommend(10002L, 41101L);

        assertEquals("recommended", view.getStatus());
        assertEquals("yite", view.getForwarderCode());
        assertEquals(1, view.getRiskPrompts().size());
        assertEquals("warning", view.getRiskPrompts().get(0).getSeverity());
        assertTrue(view.getRiskPrompts().get(0).getMessage().contains("磁性货需要提前确认包装"));
    }

    private static LogisticsServiceLineFact serviceLine(
            String naturalKey,
            String forwarderCode,
            String forwarderName,
            String transportMode,
            String channelName,
            Integer daysMin,
            Integer daysMax
    ) {
        return new LogisticsServiceLineFact(
                naturalKey,
                forwarderCode,
                forwarderName,
                "KSA",
                null,
                "KSA/Riyadh",
                transportMode,
                "warehouse_to_fbn",
                channelName,
                "佛山仓",
                "KSA/Riyadh",
                "weekly",
                daysMin,
                daysMax,
                "2026-05-01",
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage(3001L)
        );
    }

    private static LogisticsCargoCategoryFact category(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String categoryCode,
            String categoryName
    ) {
        return new LogisticsCargoCategoryFact(
                naturalKey,
                forwarderCode,
                serviceLineKey,
                categoryCode,
                categoryName,
                categoryName,
                null,
                null,
                null,
                null,
                null,
                false,
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage(3002L)
        );
    }

    private static LogisticsPriceRuleFact price(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String cargoCategoryKey,
            String unitPrice,
            String billingUnit
    ) {
        return priceWithRules(
                naturalKey,
                forwarderCode,
                serviceLineKey,
                cargoCategoryKey,
                unitPrice,
                billingUnit,
                null,
                null,
                null,
                null,
                null,
                null,
                "NORMAL"
        );
    }

    private static LogisticsPriceRuleFact priceWithRules(
            String naturalKey,
            String forwarderCode,
            String serviceLineKey,
            String cargoCategoryKey,
            String unitPrice,
            String billingUnit,
            String minimumBillableUnit,
            String minimumBillableUnitType,
            String minimumCharge,
            String volumeDivisor,
            String seaWeightRatio,
            String roundingRule,
            String priceStatus
    ) {
        return new LogisticsPriceRuleFact(
                naturalKey,
                forwarderCode,
                serviceLineKey,
                cargoCategoryKey,
                unitPrice == null ? null : new BigDecimal(unitPrice),
                "SAR",
                billingUnit,
                "unit_price",
                minimumBillableUnit == null ? null : new BigDecimal(minimumBillableUnit),
                minimumBillableUnitType,
                minimumCharge == null ? null : new BigDecimal(minimumCharge),
                volumeDivisor == null ? null : new BigDecimal(volumeDivisor),
                seaWeightRatio,
                roundingRule,
                priceStatus,
                "2026-05-01",
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage(3003L)
        );
    }

    private static LogisticsQuoteFactSourceLineage lineage(Long sourceVersionItemId) {
        return new LogisticsQuoteFactSourceLineage(
                "file_management",
                20076L,
                40054L,
                70054L,
                sourceVersionItemId,
                "quote.pdf",
                "page 1"
        );
    }

    private static LogisticsRestrictionRuleFact restriction(
            String forwarderCode,
            String serviceLineKey,
            String restrictionType,
            String itemText,
            String severity,
            boolean manualConfirmRequired
    ) {
        return new LogisticsRestrictionRuleFact(
                forwarderCode + "|" + serviceLineKey + "|" + restrictionType,
                forwarderCode,
                serviceLineKey,
                restrictionType,
                itemText,
                itemText,
                restrictionType,
                severity,
                manualConfirmRequired,
                LogisticsQuoteFactStatus.ACTIVE.value(),
                lineage(3004L)
        );
    }

    private static class TestFixture {
        private final CapturingProcurementLogisticsRequirementMapper mapper =
                new CapturingProcurementLogisticsRequirementMapper();
        private final LocalDbProcurementLogisticsRequirementService requirementService =
                new LocalDbProcurementLogisticsRequirementService(mapper);
        private final InMemoryFactRepository repository = new InMemoryFactRepository();
        private final LocalDbProcurementLogisticsRecommendationService service =
                new LocalDbProcurementLogisticsRecommendationService(requirementService, repository);

        TestFixture() {
            mapper.ownedDemandItems.put(41101L, 10002L);
        }

        void saveRequirement(String transportMode) {
            saveRequirement(transportMode, "ordinary");
        }

        void saveRequirement(String transportMode, String cargoAttributes) {
            ProcurementLogisticsRequirementCommand command = new ProcurementLogisticsRequirementCommand();
            command.setOwnerUserId(10002L);
            command.setDemandItemId(41101L);
            command.setTransportMode(transportMode);
            command.setDestinationCountry("KSA");
            command.setDestinationNode("KSA/Riyadh");
            command.setOriginNode("佛山仓");
            command.setPackageLengthCm(new BigDecimal("23"));
            command.setPackageWidthCm(new BigDecimal("13"));
            command.setPackageHeightCm(new BigDecimal("5"));
            command.setUnitWeightGrams(new BigDecimal("100"));
            command.setQuantity(300);
            command.setCargoAttributes(cargoAttributes);
            requirementService.saveRequirement(command);
        }
    }

    private static class CapturingProcurementLogisticsRequirementMapper implements ProcurementLogisticsRequirementMapper {

        private long nextId = 96000L;
        private final Map<Long, Long> ownedDemandItems = new LinkedHashMap<>();
        private final Map<Long, ProcurementLogisticsRequirementRow> requirements = new LinkedHashMap<>();

        @Override
        public Long nextRequirementId() {
            nextId += 1;
            return nextId;
        }

        @Override
        public int countOwnedDemandItem(Long ownerUserId, Long demandItemId) {
            return ownerUserId != null && ownerUserId.equals(ownedDemandItems.get(demandItemId)) ? 1 : 0;
        }

        @Override
        public ProcurementLogisticsRequirementRow selectRequirement(Long ownerUserId, Long demandItemId) {
            ProcurementLogisticsRequirementRow row = requirements.get(demandItemId);
            if (row == null || !ownerUserId.equals(row.getOwnerUserId())) {
                return null;
            }
            return row;
        }

        @Override
        public int upsertRequirement(ProcurementLogisticsRequirementRow row, Long operatorUserId) {
            requirements.put(row.getDemandItemId(), row);
            return 1;
        }
    }

    private static class InMemoryFactRepository implements LogisticsQuoteFactRepository {

        private final List<LogisticsServiceLineFact> serviceLines = new ArrayList<>();
        private final List<LogisticsCargoCategoryFact> categories = new ArrayList<>();
        private final List<LogisticsPriceRuleFact> prices = new ArrayList<>();
        private final List<LogisticsRestrictionRuleFact> restrictions = new ArrayList<>();

        void addServiceLine(LogisticsServiceLineFact fact) {
            serviceLines.add(fact);
        }

        void addCategory(LogisticsCargoCategoryFact fact) {
            categories.add(fact);
        }

        void addPrice(LogisticsPriceRuleFact fact) {
            prices.add(fact);
        }

        void addRestriction(LogisticsRestrictionRuleFact fact) {
            restrictions.add(fact);
        }

        @Override
        public java.util.Optional<LogisticsServiceLineFact> findServiceLineBySourceVersionItemId(Long sourceVersionItemId) {
            return java.util.Optional.empty();
        }

        @Override
        public void insertServiceLine(LogisticsServiceLineFact fact) {
            addServiceLine(fact);
        }

        @Override
        public List<LogisticsServiceLineFact> findActiveServiceLines(LogisticsServiceLineQuery query) {
            List<LogisticsServiceLineFact> rows = new ArrayList<>();
            for (LogisticsServiceLineFact serviceLine : serviceLines) {
                if (LogisticsQuoteFactStatus.ACTIVE.value().equals(serviceLine.getStatus())
                        && query.matches(serviceLine)) {
                    rows.add(serviceLine);
                }
            }
            return rows;
        }

        @Override
        public List<LogisticsCargoCategoryFact> findActiveCargoCategories(String forwarderCode, String serviceLineKey) {
            List<LogisticsCargoCategoryFact> rows = new ArrayList<>();
            for (LogisticsCargoCategoryFact category : categories) {
                if (LogisticsQuoteFactStatus.ACTIVE.value().equals(category.getStatus())
                        && forwarderCode.equals(category.getForwarderCode())
                        && serviceLineKey.equals(category.getServiceLineKey())) {
                    rows.add(category);
                }
            }
            return rows;
        }

        @Override
        public List<LogisticsPriceRuleFact> findComparablePriceRules(LogisticsQuoteComparisonQuery query) {
            List<LogisticsPriceRuleFact> rows = new ArrayList<>();
            for (LogisticsPriceRuleFact price : prices) {
                if (!price.isComparable() || !matches(query.getBillingUnit(), price.getBillingUnit())) {
                    continue;
                }
                LogisticsServiceLineFact serviceLine = serviceLines.stream()
                        .filter(line -> line.getNaturalKey().equals(price.getServiceLineKey()))
                        .filter(line -> line.getForwarderCode().equals(price.getForwarderCode()))
                        .filter(line -> LogisticsQuoteFactStatus.ACTIVE.value().equals(line.getStatus()))
                        .findFirst()
                        .orElse(null);
                LogisticsCargoCategoryFact category = categories.stream()
                        .filter(value -> value.getNaturalKey().equals(price.getCargoCategoryKey()))
                        .filter(value -> value.getForwarderCode().equals(price.getForwarderCode()))
                        .filter(value -> value.getServiceLineKey().equals(price.getServiceLineKey()))
                        .filter(LogisticsCargoCategoryFact::isComparable)
                        .findFirst()
                        .orElse(null);
                if (serviceLine != null && category != null && query.matches(serviceLine, category)) {
                    rows.add(price);
                }
            }
            return rows;
        }

        @Override
        public List<LogisticsPriceRuleFact> findPriceRulesByServiceLineKey(String serviceLineKey) {
            List<LogisticsPriceRuleFact> rows = new ArrayList<>();
            for (LogisticsPriceRuleFact price : prices) {
                if (serviceLineKey.equals(price.getServiceLineKey())
                        && LogisticsQuoteFactStatus.ACTIVE.value().equals(price.getStatus())) {
                    rows.add(price);
                }
            }
            return rows;
        }

        @Override
        public List<LogisticsRestrictionRuleFact> findRestrictionRulesByServiceLineKey(String serviceLineKey) {
            List<LogisticsRestrictionRuleFact> rows = new ArrayList<>();
            for (LogisticsRestrictionRuleFact restriction : restrictions) {
                if (serviceLineKey.equals(restriction.getServiceLineKey())
                        && LogisticsQuoteFactStatus.ACTIVE.value().equals(restriction.getStatus())) {
                    rows.add(restriction);
                }
            }
            return rows;
        }

        private boolean matches(String expected, String actual) {
            return expected == null || expected.isBlank() || expected.equals(actual);
        }
    }
}
