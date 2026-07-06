package com.nuono.next.intransit;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public enum InTransitNodeStatus {
    CREATED("created", "已登记"),
    HANDED_TO_FORWARDER("handed_to_forwarder", "已交货代"),
    DEPARTED_ORIGIN("departed_origin", "起运"),
    IN_TRANSIT("in_transit", "运输中"),
    ARRIVED_PORT("arrived_port", "到港"),
    CUSTOMS_CLEARANCE("customs_clearance", "清关中"),
    CUSTOMS_RELEASED("customs_released", "清关完成"),
    DELIVERING("delivering", "派送中"),
    WAREHOUSE_RECEIVED("warehouse_received", "已入仓"),
    EXCEPTION("exception", "异常"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String label;

    InTransitNodeStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean isTerminalForBatchProjection() {
        return this == WAREHOUSE_RECEIVED || this == CANCELLED;
    }

    public static boolean isTerminalForBatchProjection(String value) {
        return isTerminalForBatchProjection(value, null);
    }

    public static boolean isTerminalForBatchProjection(String value, String description) {
        return require(normalizeForPersistence(value, description)).isTerminalForBatchProjection();
    }

    public static String normalizeForPersistence(String value, String description) {
        if (isWarehouseArrivalMarker(value) || isWarehouseArrivalMarker(description)) {
            return WAREHOUSE_RECEIVED.code();
        }
        InTransitNodeStatus status = require(value);
        return status.code();
    }

    public static boolean isWarehouseArrivalMarker(String value) {
        return isFbnWarehouseAppointment(value) || isPickedUpOverseasWarehouseBeforeDelivery(value);
    }

    private static boolean isFbnWarehouseAppointment(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalized.contains("FBN预约送仓")
                || (normalized.contains("FBN") && normalized.contains("预约送仓"));
    }

    private static boolean isPickedUpOverseasWarehouseBeforeDelivery(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String normalized = value.trim().replaceAll("\\s+", "");
        return normalized.contains("提回海外仓")
                && normalized.contains("待拆柜")
                && normalized.contains("派送");
    }

    public static InTransitNodeStatus require(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("物流节点状态不支持自由文本。");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(status -> status.code.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("物流节点状态不支持自由文本。"));
    }

    public static List<String> codes() {
        return Arrays.stream(values()).map(InTransitNodeStatus::code).collect(Collectors.toList());
    }
}
