package com.nuono.next.profit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.profit.ProfitQuickSignalsRequest.Candidate;
import com.nuono.next.profit.ProfitQuickSignalsRequest.Context;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfitQuickSignalsServiceTest {

    private final ProfitCalculationEngine profitCalculationEngine = new ProfitCalculationEngine();
    private final ProfitQuickSignalsService profitQuickSignalsService = new ProfitQuickSignalsService(profitCalculationEngine);
    private final ProfitCalculationService profitCalculationService = new ProfitCalculationService(profitCalculationEngine);

    @Test
    void shouldReturnPartialQuickSignalsWhenDefaultsAreUsed() {
        ProfitQuickSignalsRequest request = new ProfitQuickSignalsRequest();
        Context context = new Context();
        context.setSite("SA");
        context.setSalePrice(new BigDecimal("49"));
        request.setContext(context);

        Candidate candidate = new Candidate();
        candidate.setCandidateKey("candidate-101");
        candidate.setCandidateId(101L);
        candidate.setTitle("Portable Electric Bakhoor Burner");
        candidate.setPurchasePrice(new BigDecimal("12.5"));
        candidate.setLengthCm(new BigDecimal("18"));
        candidate.setWidthCm(new BigDecimal("8"));
        candidate.setHeightCm(new BigDecimal("8"));
        candidate.setWeightGrams(new BigDecimal("280"));
        request.setCandidates(List.of(candidate));

        ProfitQuickSignalsView view = profitQuickSignalsService.calculate(request);

        assertTrue(view.isReady());
        assertEquals("SAR", view.getMarketCurrency());
        assertEquals(1, view.getSignals().size());
        ProfitQuickSignalsView.SignalView signal = view.getSignals().get(0);
        assertEquals(ProfitQuickSignalStatus.PARTIAL, signal.getStatus());
        assertEquals(2, signal.getQuickScenarios().size());
        assertEquals(new BigDecimal("25.44"), signal.getQuickScenarios().get(0).getProfitRmb());
        assertEquals(new BigDecimal("42.14"), signal.getQuickScenarios().get(1).getProfitRmb());
        assertTrue(signal.getUsedDefaults().contains("exchangeRate"));
        assertNotNull(signal.getDetailSeed());
        assertEquals(new BigDecimal("49"), signal.getDetailSeed().getSalePrice());
    }

    @Test
    void shouldReturnReadyQuickSignalsWhenNoDefaultsAreUsed() {
        ProfitQuickSignalsRequest request = new ProfitQuickSignalsRequest();
        Context context = new Context();
        context.setSite("SA");
        context.setSalePrice(new BigDecimal("49"));
        context.setVatRate(new BigDecimal("0.15"));
        context.setExchangeRate(new BigDecimal("1.8833"));
        context.setDomesticShippingFee(new BigDecimal("2.2"));
        context.setWarehouseDeliveryUnitPrice(new BigDecimal("2.5"));
        context.setAirFreightUnitPrice(new BigDecimal("65"));
        context.setOceanFreightUnitPrice(new BigDecimal("1300"));
        context.setAirFreightDimFactor(new BigDecimal("5000"));
        context.setFbnCommissionRate(new BigDecimal("0.15"));
        context.setFbpCommissionRate(new BigDecimal("0.15"));
        context.setFbnOutboundFee(new BigDecimal("8"));
        context.setFbpDirectShipFee(new BigDecimal("10"));
        context.setFulfillmentFee(new BigDecimal("7"));
        request.setContext(context);

        Candidate candidate = new Candidate();
        candidate.setCandidateKey("candidate-103");
        candidate.setCandidateId(103L);
        candidate.setTitle("Portable Electric Bakhoor Burner");
        candidate.setPurchasePrice(new BigDecimal("12.5"));
        candidate.setLengthCm(new BigDecimal("18"));
        candidate.setWidthCm(new BigDecimal("8"));
        candidate.setHeightCm(new BigDecimal("8"));
        candidate.setWeightGrams(new BigDecimal("280"));
        request.setCandidates(List.of(candidate));

        ProfitQuickSignalsView view = profitQuickSignalsService.calculate(request);

        assertEquals(1, view.getSignals().size());
        ProfitQuickSignalsView.SignalView signal = view.getSignals().get(0);
        assertEquals(ProfitQuickSignalStatus.READY, signal.getStatus());
        assertTrue(signal.getUsedDefaults().isEmpty());
        assertEquals(2, signal.getQuickScenarios().size());
    }

    @Test
    void shouldCarryDetailSeedIntoCalculateWithoutChangingQuickScenarioResults() {
        ProfitQuickSignalsRequest request = new ProfitQuickSignalsRequest();
        Context context = new Context();
        context.setSite("SA");
        context.setSalePrice(new BigDecimal("49"));
        context.setVatRate(new BigDecimal("0.15"));
        context.setExchangeRate(new BigDecimal("1.8833"));
        context.setDomesticShippingFee(new BigDecimal("2.2"));
        context.setWarehouseDeliveryUnitPrice(new BigDecimal("2.5"));
        context.setAirFreightUnitPrice(new BigDecimal("65"));
        context.setOceanFreightUnitPrice(new BigDecimal("1300"));
        context.setAirFreightDimFactor(new BigDecimal("5000"));
        context.setFbnCommissionRate(new BigDecimal("0.15"));
        context.setFbpCommissionRate(new BigDecimal("0.15"));
        context.setFbnOutboundFee(new BigDecimal("8"));
        context.setFbpDirectShipFee(new BigDecimal("10"));
        context.setFulfillmentFee(new BigDecimal("7"));
        request.setContext(context);

        Candidate candidate = new Candidate();
        candidate.setCandidateKey("candidate-104");
        candidate.setCandidateId(104L);
        candidate.setTitle("Carryover Check Candidate");
        candidate.setPurchasePrice(new BigDecimal("12.5"));
        candidate.setLengthCm(new BigDecimal("18"));
        candidate.setWidthCm(new BigDecimal("8"));
        candidate.setHeightCm(new BigDecimal("8"));
        candidate.setWeightGrams(new BigDecimal("280"));
        request.setCandidates(List.of(candidate));

        ProfitQuickSignalsView.SignalView signal = profitQuickSignalsService.calculate(request).getSignals().get(0);
        ProfitQuickSignalsView.DetailSeedView detailSeed = signal.getDetailSeed();
        assertNotNull(detailSeed);

        ProfitCalculationView detailView = profitCalculationService.calculate(toCommand(detailSeed));

        assertBigDecimalValue(detailSeed.getSalePrice(), detailView.getSalePrice());
        assertBigDecimalValue(detailSeed.getPurchasePrice(), detailView.getPurchasePrice());
        assertBigDecimalValue(detailSeed.getLengthCm(), detailView.getLengthCm());
        assertBigDecimalValue(detailSeed.getWidthCm(), detailView.getWidthCm());
        assertBigDecimalValue(detailSeed.getHeightCm(), detailView.getHeightCm());
        assertBigDecimalValue(detailSeed.getWeightGrams(), detailView.getWeightGrams());
        assertBigDecimalValue(findQuickScenario(signal, "FBN_AIR").getProfitRmb(), findDetailScenario(detailView, "FBN_AIR").getProfitRmb());
        assertBigDecimalValue(findQuickScenario(signal, "FBN_OCEAN").getProfitRmb(), findDetailScenario(detailView, "FBN_OCEAN").getProfitRmb());
    }

    @Test
    void shouldReturnBlockedWhenRequiredFieldsAreMissing() {
        ProfitQuickSignalsRequest request = new ProfitQuickSignalsRequest();
        Context context = new Context();
        context.setSite("SA");
        context.setSalePrice(new BigDecimal("49"));
        request.setContext(context);

        Candidate candidate = new Candidate();
        candidate.setCandidateKey("candidate-102");
        candidate.setCandidateId(102L);
        candidate.setTitle("Mini Mabkhara");
        candidate.setPurchasePrice(new BigDecimal("10"));
        candidate.setLengthCm(new BigDecimal("12"));
        candidate.setWidthCm(new BigDecimal("7"));
        candidate.setHeightCm(new BigDecimal("6"));
        request.setCandidates(List.of(candidate));

        ProfitQuickSignalsView view = profitQuickSignalsService.calculate(request);

        assertEquals(1, view.getSignals().size());
        ProfitQuickSignalsView.SignalView signal = view.getSignals().get(0);
        assertEquals(ProfitQuickSignalStatus.BLOCKED, signal.getStatus());
        assertTrue(signal.getMissingInputs().contains("weightGrams"));
        assertEquals(0, signal.getQuickScenarios().size());
        assertNotNull(signal.getDetailSeed());
    }

    private ProfitCalculationCommand toCommand(ProfitQuickSignalsView.DetailSeedView detailSeed) {
        ProfitCalculationCommand command = new ProfitCalculationCommand();
        command.setTitle(detailSeed.getTitle());
        command.setSite(detailSeed.getSite());
        command.setSalePrice(detailSeed.getSalePrice());
        command.setPurchasePrice(detailSeed.getPurchasePrice());
        command.setLengthCm(detailSeed.getLengthCm());
        command.setWidthCm(detailSeed.getWidthCm());
        command.setHeightCm(detailSeed.getHeightCm());
        command.setWeightGrams(detailSeed.getWeightGrams());
        command.setVatRate(detailSeed.getVatRate());
        command.setExchangeRate(detailSeed.getExchangeRate());
        command.setDomesticShippingFee(detailSeed.getDomesticShippingFee());
        command.setWarehouseDeliveryUnitPrice(detailSeed.getWarehouseDeliveryUnitPrice());
        command.setAirFreightUnitPrice(detailSeed.getAirFreightUnitPrice());
        command.setOceanFreightUnitPrice(detailSeed.getOceanFreightUnitPrice());
        command.setAirFreightDimFactor(detailSeed.getAirFreightDimFactor());
        command.setFbnCommissionRate(detailSeed.getFbnCommissionRate());
        command.setFbpCommissionRate(detailSeed.getFbpCommissionRate());
        command.setFbnOutboundFee(detailSeed.getFbnOutboundFee());
        command.setFbpDirectShipFee(detailSeed.getFbpDirectShipFee());
        command.setFulfillmentFee(detailSeed.getFulfillmentFee());
        return command;
    }

    private ProfitQuickSignalsView.QuickScenarioView findQuickScenario(
            ProfitQuickSignalsView.SignalView signal,
            String scenarioCode
    ) {
        return signal.getQuickScenarios().stream()
                .filter(item -> scenarioCode.equals(item.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private ProfitCalculationView.ScenarioView findDetailScenario(ProfitCalculationView detailView, String scenarioCode) {
        return detailView.getScenarios().stream()
                .filter(item -> scenarioCode.equals(item.getCode()))
                .findFirst()
                .orElseThrow();
    }

    private void assertBigDecimalValue(BigDecimal expected, BigDecimal actual) {
        assertNotNull(expected);
        assertNotNull(actual);
        assertTrue(expected.compareTo(actual) == 0, () -> "expected: <" + expected + "> but was: <" + actual + ">");
    }
}
