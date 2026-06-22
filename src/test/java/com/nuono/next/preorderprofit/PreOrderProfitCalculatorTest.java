package com.nuono.next.preorderprofit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PreOrderProfitCalculatorTest {

    private final PreOrderProfitCalculator calculator = new PreOrderProfitCalculator();

    @Test
    void saCalculationUsesFixedTaxExchangeDomesticLogisticsAndTaxedFees() {
        PreOrderProfitCalculationRequest request = readyRequest();
        request.setSiteCode("SA");
        request.setCategoryId("home-kitchen-sa");
        request.setLogisticsCarrierId("et-sa-air-standard");
        request.setSalePrice(new BigDecimal("49"));
        request.setPurchasePriceRmb(new BigDecimal("12.50"));
        request.setLengthCm(new BigDecimal("18"));
        request.setWidthCm(new BigDecimal("8"));
        request.setHeightCm(new BigDecimal("8"));
        request.setActualWeightKg(new BigDecimal("0.30"));

        PreOrderProfitCalculationView view = calculator.calculate(request);

        assertEquals("READY", view.getStatus());
        assertEquals("SAR", view.getCurrency());
        assertEquals(new BigDecimal("1.8833"), view.getExchangeRate());
        assertEquals(new BigDecimal("0.15"), view.getTaxRate());
        assertEquals(new BigDecimal("0.6000"), view.getDomesticLogisticsFeeRmb());
        assertEquals(new BigDecimal("0.32"), view.getDomesticLogisticsFee());
        assertEquals(new BigDecimal("0.192"), view.getVolumeWeightKg());
        assertEquals(new BigDecimal("0.300"), view.getBillingWeightKg());
        assertEquals(new BigDecimal("5.58"), view.getFirstLegLogisticsFee());
        assertEquals(new BigDecimal("7.89"), view.getCommissionFeeTaxIncluded());
        assertEquals(new BigDecimal("11.50"), view.getFulfillmentFeeTaxIncluded());
        assertEquals(new BigDecimal("31.92"), view.getTotalCost());
        assertEquals(new BigDecimal("17.08"), view.getEstimatedProfit());
        assertEquals(new BigDecimal("34.86"), view.getEstimatedMarginRatePct());
        assertEquals(new BigDecimal("28.64"), view.getBreakEvenSalePrice());
        assertEquals(new BigDecimal("44.58"), view.getTargetMarginSalePrice());
    }

    @Test
    void aeCalculationUsesFivePercentTaxAndAedExchange() {
        PreOrderProfitCalculationRequest request = readyRequest();
        request.setSiteCode("AE");
        request.setCategoryId("home-storage-ae");
        request.setLogisticsCarrierId("et-ae-air-standard");
        request.setSalePrice(new BigDecimal("55"));
        request.setPurchasePriceRmb(new BigDecimal("16"));
        request.setLengthCm(new BigDecimal("30"));
        request.setWidthCm(new BigDecimal("24"));
        request.setHeightCm(new BigDecimal("10"));
        request.setActualWeightKg(new BigDecimal("0.50"));

        PreOrderProfitCalculationView view = calculator.calculate(request);

        assertEquals("READY", view.getStatus());
        assertEquals("AED", view.getCurrency());
        assertEquals(new BigDecimal("1.9500"), view.getExchangeRate());
        assertEquals(new BigDecimal("0.05"), view.getTaxRate());
        assertEquals(new BigDecimal("1.200"), view.getVolumeWeightKg());
        assertEquals(new BigDecimal("1.200"), view.getBillingWeightKg());
        assertEquals(new BigDecimal("19.69"), view.getFirstLegLogisticsFee());
        assertEquals(new BigDecimal("7.51"), view.getCommissionFeeTaxIncluded());
        assertEquals(new BigDecimal("10.29"), view.getFulfillmentFeeTaxIncluded());
    }

    @Test
    void missingRequiredInputDoesNotReturnFakeReadyProfit() {
        PreOrderProfitCalculationRequest request = readyRequest();
        request.setPurchaseUrl(" ");
        request.setPurchasePriceRmb(null);

        PreOrderProfitCalculationView view = calculator.calculate(request);

        assertEquals("INCOMPLETE_INPUT", view.getStatus());
        assertTrue(view.getMissingFields().contains("PURCHASE_URL"));
        assertTrue(view.getMissingFields().contains("PURCHASE_PRICE_RMB"));
        assertNull(view.getEstimatedProfit());
    }

    @Test
    void missingCategoryOrLogisticsRuleDoesNotDefaultToZeroCost() {
        PreOrderProfitCalculationRequest request = readyRequest();
        request.setCategoryId("unknown-category");
        request.setLogisticsCarrierId("unknown-logistics");

        PreOrderProfitCalculationView view = calculator.calculate(request);

        assertEquals("MISSING_RULE", view.getStatus());
        assertTrue(view.getMissingRules().contains("CATEGORY_RULE"));
        assertTrue(view.getMissingRules().contains("LOGISTICS_RULE"));
        assertNull(view.getEstimatedProfit());
    }

    @Test
    void targetMarginDenominatorMustStayPositive() {
        PreOrderProfitCalculationRequest request = readyRequest();
        request.setTargetMarginRate(new BigDecimal("0.90"));

        PreOrderProfitCalculationView view = calculator.calculate(request);

        assertEquals("INVALID_FORMULA", view.getStatus());
        assertNull(view.getTargetMarginSalePrice());
        assertTrue(view.getFormulaIssues().contains("TARGET_MARGIN_DENOMINATOR"));
    }

    private PreOrderProfitCalculationRequest readyRequest() {
        PreOrderProfitCalculationRequest request = new PreOrderProfitCalculationRequest();
        request.setStoreCode("CANMAN");
        request.setSiteCode("SA");
        request.setPurchaseUrl("https://detail.1688.com/offer/123.html");
        request.setPurchasePriceRmb(new BigDecimal("12.50"));
        request.setLengthCm(new BigDecimal("18"));
        request.setWidthCm(new BigDecimal("8"));
        request.setHeightCm(new BigDecimal("8"));
        request.setActualWeightKg(new BigDecimal("0.30"));
        request.setCategoryId("home-kitchen-sa");
        request.setLogisticsCarrierId("et-sa-air-standard");
        request.setSalePrice(new BigDecimal("49"));
        request.setTargetMarginRate(new BigDecimal("0.30"));
        return request;
    }
}
