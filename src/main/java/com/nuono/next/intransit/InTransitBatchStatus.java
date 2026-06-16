package com.nuono.next.intransit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public enum InTransitBatchStatus {
    DRAFT("draft", "草稿"),
    PENDING_SHIPMENT("pending_shipment", "待发货"),
    SHIPPED("shipped", "已发货"),
    IN_TRANSIT("in_transit", "运输中"),
    CUSTOMS_CLEARANCE("customs_clearance", "清关中"),
    DELIVERING("delivering", "派送中"),
    WAREHOUSE_RECEIVED("warehouse_received", "已入仓"),
    EXCEPTION("exception", "异常"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String label;

    InTransitBatchStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static InTransitBatchStatus require(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("在途批次状态不支持自由文本。");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("在途批次状态不支持自由文本。"));
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(InTransitBatchStatus::code).collect(Collectors.toList());
    }
}
