package com.nuono.next.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nuono.next.logisticsquote.LogisticsPriceRuleFact;
import com.nuono.next.logisticsquote.LogisticsQuoteFactSourceLineage;
import com.nuono.next.logisticsquote.LogisticsQuoteFactStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class LogisticsShipmentCostEstimatorTest {

    private final LogisticsShipmentCostEstimator estimator = new LogisticsShipmentCostEstimator();

    @Test
    void airUsesGreaterOfActualAndVolumetricWeight() {
        LogisticsShipmentCostEstimator.CostEstimate estimate = estimator.estimate(
                shipment("23", "13", "5", "100", 1, "air"),
                price("64", "kg", "unit_price", null, null, null, "6000", null, null, "NORMAL")
        );

        assertTrue(estimate.isComputable());
        assertEquals(new BigDecimal("0.10"), estimate.getActualWeightKg());
        assertEquals(new BigDecimal("0.249"), estimate.getVolumetricWeightKg());
        assertEquals(new BigDecimal("0.25"), estimate.getChargeableUnit());
        assertEquals(new BigDecimal("16.00"), estimate.getEstimatedCost());
        assertTrue(estimate.getBreakdownText().contains("体积重 0.249 kg"));
    }

    @Test
    void bulkyAirItemUsesVolumetricWeight() {
        LogisticsShipmentCostEstimator.CostEstimate estimate = estimator.estimate(
                shipment("60", "40", "40", "500", 2, "air"),
                price("50", "kg", "unit_price", null, null, null, "6000", null, null, "NORMAL")
        );

        assertTrue(estimate.isComputable());
        assertEquals(new BigDecimal("1.00"), estimate.getActualWeightKg());
        assertEquals(new BigDecimal("32.000"), estimate.getVolumetricWeightKg());
        assertEquals(new BigDecimal("32.00"), estimate.getChargeableUnit());
        assertEquals(new BigDecimal("1600.00"), estimate.getEstimatedCost());
    }

    @Test
    void seaUsesCbmWhenBillingUnitIsCbm() {
        LogisticsShipmentCostEstimator.CostEstimate estimate = estimator.estimate(
                shipment("50", "40", "30", "2000", 10, "sea"),
                price("120", "cbm", "unit_price", null, null, null, null, null, null, "NORMAL")
        );

        assertTrue(estimate.isComputable());
        assertEquals(new BigDecimal("0.600"), estimate.getVolumeCbm());
        assertEquals(new BigDecimal("0.60"), estimate.getChargeableUnit());
        assertEquals(new BigDecimal("72.00"), estimate.getEstimatedCost());
    }

    @Test
    void minimumBillableUnitAndMinimumChargeAreApplied() {
        LogisticsShipmentCostEstimator.CostEstimate estimate = estimator.estimate(
                shipment("20", "20", "10", "300", 1, "sea"),
                price("120", "cbm", "unit_price", "0.5", "cbm", "100", null, null, null, "NORMAL")
        );

        assertTrue(estimate.isComputable());
        assertEquals(new BigDecimal("0.004"), estimate.getVolumeCbm());
        assertEquals(new BigDecimal("0.50"), estimate.getChargeableUnit());
        assertEquals(new BigDecimal("100.00"), estimate.getEstimatedCost());
        assertTrue(estimate.getBreakdownText().contains("最低计费 0.5 cbm"));
        assertTrue(estimate.getBreakdownText().contains("最低收费 100"));
    }

    @Test
    void roundingRuleRoundsChargeableUnitUp() {
        LogisticsShipmentCostEstimator.CostEstimate estimate = estimator.estimate(
                shipment("23", "13", "5", "100", 1, "air"),
                price("64", "kg", "unit_price", null, null, null, "6000", null, "CEIL_0_5", "NORMAL")
        );

        assertTrue(estimate.isComputable());
        assertEquals(new BigDecimal("0.50"), estimate.getChargeableUnit());
        assertEquals(new BigDecimal("32.00"), estimate.getEstimatedCost());
    }

    @Test
    void askQuoteAndStartingPriceAreNotComputable() {
        LogisticsShipmentCostEstimator.CostEstimate askQuote = estimator.estimate(
                shipment("23", "13", "5", "100", 1, "air"),
                price(null, "kg", "unit_price", null, null, null, "6000", null, null, "ASK_QUOTE")
        );
        LogisticsShipmentCostEstimator.CostEstimate startingPrice = estimator.estimate(
                shipment("23", "13", "5", "100", 1, "air"),
                price("64", "kg", "unit_price", null, null, null, "6000", null, null, "STARTING_PRICE")
        );

        assertFalse(askQuote.isComputable());
        assertTrue(askQuote.getReason().contains("需要人工询价"));
        assertFalse(startingPrice.isComputable());
        assertTrue(startingPrice.getReason().contains("起步价"));
    }

    private static LogisticsShipmentCostEstimator.ShipmentFacts shipment(
            String lengthCm,
            String widthCm,
            String heightCm,
            String weightGrams,
            int quantity,
            String transportMode
    ) {
        return new LogisticsShipmentCostEstimator.ShipmentFacts(
                new BigDecimal(lengthCm),
                new BigDecimal(widthCm),
                new BigDecimal(heightCm),
                new BigDecimal(weightGrams),
                quantity,
                transportMode
        );
    }

    private static LogisticsPriceRuleFact price(
            String unitPrice,
            String billingUnit,
            String pricingModel,
            String minimumBillableUnit,
            String minimumBillableUnitType,
            String minimumCharge,
            String volumeDivisor,
            String seaWeightRatio,
            String roundingRule,
            String priceStatus
    ) {
        return new LogisticsPriceRuleFact(
                "price",
                "yite",
                "line",
                "category",
                unitPrice == null ? null : new BigDecimal(unitPrice),
                "SAR",
                billingUnit,
                pricingModel,
                minimumBillableUnit == null ? null : new BigDecimal(minimumBillableUnit),
                minimumBillableUnitType,
                minimumCharge == null ? null : new BigDecimal(minimumCharge),
                volumeDivisor == null ? null : new BigDecimal(volumeDivisor),
                seaWeightRatio,
                roundingRule,
                priceStatus,
                "2026-05-01",
                LogisticsQuoteFactStatus.ACTIVE.value(),
                new LogisticsQuoteFactSourceLineage("file_management", null, null, null, 1L, "quote.pdf", "page 1")
        );
    }
}
