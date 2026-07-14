package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SalesForecastRiskLabelViewTest {

    @Test
    void overstockRiskExplanationDoesNotExposeLifecycleTerms() {
        SalesForecastRiskLabelView label = SalesForecastRiskLabelView.fromCode("overstock_risk");

        assertEquals("积压风险", label.getLabel());
        assertFalse(label.getExplanation().contains("衰退"));
        assertFalse(label.getExplanation().contains("长尾"));
        assertFalse(label.getExplanation().contains("生命周期"));
    }
}
