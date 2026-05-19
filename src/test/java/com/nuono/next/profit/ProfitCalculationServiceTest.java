package com.nuono.next.profit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProfitCalculationServiceTest {

    private final ProfitCalculationEngine profitCalculationEngine = new ProfitCalculationEngine();
    private final ProfitCalculationService profitCalculationService = new ProfitCalculationService(profitCalculationEngine);

    @Test
    void shouldCalculateThreeProfitScenarios() {
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
        command.setFbnOutboundFee(new BigDecimal("8"));
        command.setFbpDirectShipFee(new BigDecimal("10"));
        command.setFulfillmentFee(new BigDecimal("7"));

        ProfitCalculationView view = profitCalculationService.calculate(command);

        assertTrue(view.isReady());
        assertEquals(new BigDecimal("0.0012"), view.getCubeVolumeCbm());
        assertEquals(new BigDecimal("230.40"), view.getDimensionalWeightGrams());
        assertEquals(new BigDecimal("0.70"), view.getWarehouseDeliveryFeeRmb());
        assertEquals(new BigDecimal("18.20"), view.getAirFirstLegFeeRmb());
        assertEquals(new BigDecimal("1.50"), view.getOceanFirstLegFeeRmb());
        assertEquals(3, view.getScenarios().size());
        assertEquals(new BigDecimal("25.44"), view.getScenarios().get(0).getProfitRmb());
        assertEquals(new BigDecimal("42.14"), view.getScenarios().get(1).getProfitRmb());
        assertEquals(new BigDecimal("14.11"), view.getScenarios().get(2).getProfitRmb());
    }
}
