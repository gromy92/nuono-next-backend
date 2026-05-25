package com.nuono.next.profit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.outboundfee.OfficialOutboundFeeCalculationFailure;
import com.nuono.next.outboundfee.OfficialOutboundFeeCalculator;
import com.nuono.next.outboundfee.OfficialOutboundFeeFactRepository;
import com.nuono.next.outboundfee.OfficialOutboundFeeFactStatus;
import com.nuono.next.outboundfee.OfficialOutboundFeeFactType;
import com.nuono.next.outboundfee.OfficialOutboundFeeSourceLineage;
import com.nuono.next.outboundfee.OutboundFeeCalculationPolicyFact;
import com.nuono.next.outboundfee.OutboundFeeWeightSlabRuleFact;
import com.nuono.next.outboundfee.OutboundSizeClassificationRuleFact;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProfitCalculationServiceOfficialOutboundFeeTest {

    private final ProfitCalculationEngine profitCalculationEngine = new ProfitCalculationEngine();

    @Test
    void usesOfficialOutboundFeeFactsForFbnProfitAndReturnsEvidence() {
        InMemoryOfficialOutboundFeeFactRepository repository = new InMemoryOfficialOutboundFeeFactRepository();
        repository.sizeClassifications.add(sizeClassification("KSA|NOON|FBN|Small Envelope|2026-05-01", "Small Envelope"));
        repository.weightSlabs.add(weightSlab("KSA|NOON|FBN|Small Envelope|MIN:0|MAX:500|CUR:SAR|2026-05-01"));
        repository.policies.add(policy("KSA|NOON|FBN|2026-05-01"));
        ProfitCalculationService service = new ProfitCalculationService(
                profitCalculationEngine,
                new OfficialOutboundFeeCalculator(repository)
        );

        ProfitCalculationCommand command = command();
        command.setFbnOutboundFee(new BigDecimal("99"));

        ProfitCalculationView view = service.calculate(command);

        assertTrue(view.isReady());
        assertEquals("CALCULATED", view.getOfficialOutboundFee().getStatus());
        assertEquals(new BigDecimal("6.5"), view.getOfficialOutboundFee().getFeeAmount());
        assertEquals("SAR", view.getOfficialOutboundFee().getCurrency());
        assertEquals("Small Envelope", view.getOfficialOutboundFee().getMatchedClassificationName());
        assertEquals("KSA|NOON|FBN|Small Envelope|MIN:0|MAX:500|CUR:SAR|2026-05-01", view.getOfficialOutboundFee().getMatchedSlabNaturalKey());
        assertEquals(9001L, view.getOfficialOutboundFee().getSourceVersionId());
        assertEquals(new BigDecimal("300"), view.getOfficialOutboundFee().getEvidence().get("shippingWeightGrams"));
        ProfitCalculationView.ScenarioView fbnAir = findScenario(view, "FBN_AIR");
        assertEquals(new BigDecimal("6.50"), fbnAir.getPlatformFeeAmountMarket());
        assertNotEquals(new BigDecimal("99.00"), fbnAir.getPlatformFeeAmountMarket());
        assertNotEquals(new BigDecimal("8.00"), fbnAir.getPlatformFeeAmountMarket());
    }

    @Test
    void reportsTypedFailureAndDoesNotCalculateFbnScenariosWhenOfficialFactsAreMissing() {
        ProfitCalculationService service = new ProfitCalculationService(
                profitCalculationEngine,
                new OfficialOutboundFeeCalculator(new InMemoryOfficialOutboundFeeFactRepository())
        );

        ProfitCalculationView view = service.calculate(command());

        assertTrue(view.isReady());
        assertEquals("FAILED", view.getOfficialOutboundFee().getStatus());
        assertEquals(OfficialOutboundFeeCalculationFailure.POLICY_NOT_FOUND.name(), view.getOfficialOutboundFee().getFailureCode());
        assertFalse(hasScenario(view, "FBN_AIR"));
        assertFalse(hasScenario(view, "FBN_OCEAN"));
        assertTrue(hasScenario(view, "FBP_AIR"));
    }

    @Test
    void usesManualFbnOutboundFeeOnlyWhenExplicitlyRequestedAndLabelsItAsManual() {
        ProfitCalculationService service = new ProfitCalculationService(
                profitCalculationEngine,
                new OfficialOutboundFeeCalculator(new InMemoryOfficialOutboundFeeFactRepository())
        );
        ProfitCalculationCommand command = command();
        command.setFbnOutboundFee(new BigDecimal("9.75"));
        command.setManualFbnOutboundFeeOverride(true);

        ProfitCalculationView view = service.calculate(command);

        assertTrue(view.isReady());
        assertEquals("MANUAL_OVERRIDE", view.getOfficialOutboundFee().getStatus());
        assertEquals(new BigDecimal("9.75"), view.getOfficialOutboundFee().getFeeAmount());
        assertEquals("SAR", view.getOfficialOutboundFee().getCurrency());
        assertEquals(new BigDecimal("9.75"), findScenario(view, "FBN_AIR").getPlatformFeeAmountMarket());
    }

    @Test
    void reportsTypedFailureWhenProductDimensionsAreMissing() {
        ProfitCalculationService service = new ProfitCalculationService(
                profitCalculationEngine,
                new OfficialOutboundFeeCalculator(new InMemoryOfficialOutboundFeeFactRepository())
        );
        ProfitCalculationCommand command = command();
        command.setLengthCm(null);

        ProfitCalculationView view = service.calculate(command);

        assertFalse(view.isReady());
        assertEquals("FAILED", view.getOfficialOutboundFee().getStatus());
        assertEquals(OfficialOutboundFeeCalculationFailure.MISSING_DIMENSIONS.name(), view.getOfficialOutboundFee().getFailureCode());
        assertTrue(view.getScenarios().isEmpty());
    }

    private ProfitCalculationCommand command() {
        ProfitCalculationCommand command = new ProfitCalculationCommand();
        command.setTitle("Portable Electric Bakhoor Burner");
        command.setSite("SA");
        command.setSalePrice(new BigDecimal("49"));
        command.setPurchasePrice(new BigDecimal("12.5"));
        command.setLengthCm(new BigDecimal("18"));
        command.setWidthCm(new BigDecimal("8"));
        command.setHeightCm(new BigDecimal("8"));
        command.setWeightGrams(new BigDecimal("280"));
        command.setVatRate(new BigDecimal("0.15"));
        command.setExchangeRate(new BigDecimal("1.8833"));
        command.setDomesticShippingFee(new BigDecimal("2.2"));
        command.setWarehouseDeliveryUnitPrice(new BigDecimal("2.5"));
        command.setAirFreightUnitPrice(new BigDecimal("65"));
        command.setOceanFreightUnitPrice(new BigDecimal("1300"));
        command.setAirFreightDimFactor(new BigDecimal("5000"));
        command.setFbnCommissionRate(new BigDecimal("0.15"));
        command.setFbpCommissionRate(new BigDecimal("0.15"));
        command.setFbpDirectShipFee(new BigDecimal("10"));
        command.setFulfillmentFee(new BigDecimal("7"));
        return command;
    }

    private ProfitCalculationView.ScenarioView findScenario(ProfitCalculationView view, String code) {
        return view.getScenarios().stream()
                .filter(scenario -> code.equals(scenario.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasScenario(ProfitCalculationView view, String code) {
        return view.getScenarios().stream().anyMatch(scenario -> code.equals(scenario.getCode()));
    }

    private OutboundSizeClassificationRuleFact sizeClassification(String naturalKey, String classificationName) {
        return new OutboundSizeClassificationRuleFact(
                naturalKey,
                "KSA",
                "NOON",
                "FBN",
                classificationName,
                new BigDecimal("20"),
                new BigDecimal("15"),
                new BigDecimal("10"),
                new BigDecimal("500"),
                new BigDecimal("20"),
                1,
                "cm",
                "g",
                "2026-05-01",
                OfficialOutboundFeeFactStatus.ACTIVE.value(),
                lineage(1001L)
        );
    }

    private OutboundFeeWeightSlabRuleFact weightSlab(String naturalKey) {
        return new OutboundFeeWeightSlabRuleFact(
                naturalKey,
                "KSA",
                "NOON",
                "FBN",
                "Small Envelope",
                new BigDecimal("0"),
                false,
                new BigDecimal("500"),
                true,
                new BigDecimal("6.5"),
                new BigDecimal("8.5"),
                new BigDecimal("50"),
                "SAR",
                new BigDecimal("500"),
                new BigDecimal("2"),
                "SAR",
                "2026-05-01",
                OfficialOutboundFeeFactStatus.ACTIVE.value(),
                lineage(1002L)
        );
    }

    private OutboundFeeCalculationPolicyFact policy(String naturalKey) {
        return new OutboundFeeCalculationPolicyFact(
                naturalKey,
                "KSA",
                "NOON",
                "FBN",
                "Noon FBN official outbound policy",
                "product_weight_plus_packaging_weight",
                "sort_dimensions_desc",
                "min_exclusive_max_inclusive",
                "no_rounding",
                new BigDecimal("50"),
                "SAR",
                "cm",
                "g",
                "2026-05-01",
                OfficialOutboundFeeFactStatus.ACTIVE.value(),
                lineage(1003L)
        );
    }

    private OfficialOutboundFeeSourceLineage lineage(Long sourceVersionItemId) {
        return new OfficialOutboundFeeSourceLineage(
                "file_management",
                7001L,
                8001L,
                9001L,
                sourceVersionItemId,
                "fulfilled-by-noon-fbn-fees-in-ksa.pdf",
                "page 1"
        );
    }

    private static class InMemoryOfficialOutboundFeeFactRepository implements OfficialOutboundFeeFactRepository {

        private final List<OutboundSizeClassificationRuleFact> sizeClassifications = new ArrayList<>();
        private final List<OutboundFeeWeightSlabRuleFact> weightSlabs = new ArrayList<>();
        private final List<OutboundFeeCalculationPolicyFact> policies = new ArrayList<>();

        @Override
        public Optional<OutboundSizeClassificationRuleFact> findSizeClassificationBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public List<OutboundSizeClassificationRuleFact> findActiveSizeClassifications(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            return sizeClassifications;
        }

        @Override
        public void insertSizeClassification(OutboundSizeClassificationRuleFact fact) {
            sizeClassifications.add(fact);
        }

        @Override
        public Optional<OutboundFeeWeightSlabRuleFact> findWeightSlabBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public List<OutboundFeeWeightSlabRuleFact> findActiveWeightSlabs(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            return weightSlabs;
        }

        @Override
        public void insertWeightSlab(OutboundFeeWeightSlabRuleFact fact) {
            weightSlabs.add(fact);
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findCalculationPolicyBySourceVersionItemId(Long sourceVersionItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<OutboundFeeCalculationPolicyFact> findActiveCalculationPolicy(
                String country,
                String platform,
                String fulfillmentType,
                LocalDate calculationDate
        ) {
            return policies.stream().findFirst();
        }

        @Override
        public void insertCalculationPolicy(OutboundFeeCalculationPolicyFact fact) {
            policies.add(fact);
        }

        @Override
        public boolean hasActiveFactWithNaturalKey(OfficialOutboundFeeFactType factType, String naturalKey) {
            return false;
        }

        @Override
        public void supersedeActiveFacts(OfficialOutboundFeeFactType factType, String naturalKey) {
        }
    }
}
