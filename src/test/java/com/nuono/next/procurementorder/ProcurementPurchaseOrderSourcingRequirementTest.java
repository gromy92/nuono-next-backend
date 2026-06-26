package com.nuono.next.procurementorder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProcurementPurchaseOrderSourcingRequirementTest {

    @Test
    void buildsAli1688StyleHintsFromManualRequirement() {
        ProcurementPurchaseOrderSourcingRequirement requirement =
                ProcurementPurchaseOrderSourcingRequirement.of("  2只装  ", "  12cm  ", "  红色  ");

        assertThat(requirement.getSpecText()).isEqualTo("2只装");
        assertThat(requirement.getSizeText()).isEqualTo("12cm");
        assertThat(requirement.getColorText()).isEqualTo("红色");
        assertThat(requirement.toSpecHints()).containsExactly("规格: 2只装", "尺寸: 12cm", "颜色: 红色");
    }

    @Test
    void omitsEmptyManualRequirementValues() {
        ProcurementPurchaseOrderSourcingRequirement requirement =
                ProcurementPurchaseOrderSourcingRequirement.of(" ", null, "  蓝色  ");

        assertThat(requirement.getSpecText()).isNull();
        assertThat(requirement.getSizeText()).isNull();
        assertThat(requirement.getColorText()).isEqualTo("蓝色");
        assertThat(requirement.toSpecHints()).containsExactly("颜色: 蓝色");
    }
}
