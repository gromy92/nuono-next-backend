package com.nuono.next.product;

import java.util.Locale;
import org.springframework.util.StringUtils;

public enum ProductOperationStage {
    TESTING("测试新品"),
    STABLE("稳定销售"),
    WATCH("观察调整"),
    CLEARANCE("清仓");

    private final String label;

    ProductOperationStage(String label) {
        this.label = label;
    }

    public String getCode() {
        return name();
    }

    public String getLabel() {
        return label;
    }

    public static ProductOperationStage fromNullableCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return fromCode(code);
    }

    public static ProductOperationStage fromCode(String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        for (ProductOperationStage stage : values()) {
            if (stage.name().equals(normalized)) {
                return stage;
            }
        }
        throw new IllegalArgumentException("不支持的运营阶段：" + code);
    }
}
