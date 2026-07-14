package com.nuono.next.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProductOperationStageTest {

    @Test
    void resolvesAllowedOperationStageCodes() {
        assertThat(ProductOperationStage.fromCode("TESTING")).isEqualTo(ProductOperationStage.TESTING);
        assertThat(ProductOperationStage.fromCode("stable")).isEqualTo(ProductOperationStage.STABLE);
        assertThat(ProductOperationStage.fromCode(" Watch ")).isEqualTo(ProductOperationStage.WATCH);
        assertThat(ProductOperationStage.fromCode("CLEARANCE")).isEqualTo(ProductOperationStage.CLEARANCE);
    }

    @Test
    void rejectsUnsupportedOperationStageCodes() {
        assertThatThrownBy(() -> ProductOperationStage.fromCode("GROWING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的运营阶段");
    }

    @Test
    void acceptsBlankCodeAsUnset() {
        assertThat(ProductOperationStage.fromNullableCode(null)).isNull();
        assertThat(ProductOperationStage.fromNullableCode(" ")).isNull();
    }
}
