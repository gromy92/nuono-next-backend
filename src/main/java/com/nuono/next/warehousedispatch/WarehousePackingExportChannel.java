package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class WarehousePackingExportChannel {

    final String forwarderCode;
    final String forwarderName;
    final String routeCode;
    final String routeName;
    final Totals totals;

    private WarehousePackingExportChannel(
            String forwarderCode,
            String forwarderName,
            String routeCode,
            String routeName,
            Totals totals
    ) {
        this.forwarderCode = forwarderCode;
        this.forwarderName = forwarderName;
        this.routeCode = routeCode;
        this.routeName = routeName;
        this.totals = totals;
    }

    static WarehousePackingExportChannel resolve(
            String forwarderCode,
            String routeCode,
            List<OutboundOrderView> orders,
            Map<String, List<PackingListView>> packingListsByOrder
    ) {
        String requiredForwarder = required(forwarderCode, "请选择货代。");
        String requiredRoute = required(routeCode, "请选择渠道。");
        Totals totals = new Totals();
        String forwarderName = null;
        String routeName = null;
        for (OutboundOrderView order : orders) {
            Map<Long, OutboundOrderLineView> lineById = lineById(order);
            for (PackingListView packingList : packingListsByOrder.getOrDefault(order.id, List.of())) {
                for (PackingBoxView box : packingList.boxes) {
                    OutboundOrderLineView line = firstLine(box, lineById);
                    if (!matches(line, requiredForwarder, requiredRoute)) continue;
                    if (forwarderName == null) forwarderName = line.targetForwarderName;
                    if (routeName == null) routeName = line.routeName;
                    totals.add(box);
                }
            }
        }
        if (totals.boxCount == 0) {
            throw new IllegalArgumentException("当前发货单没有匹配所选货代渠道的装箱数据。");
        }
        return new WarehousePackingExportChannel(
                requiredForwarder,
                value(forwarderName, requiredForwarder),
                requiredRoute,
                value(routeName, requiredRoute),
                totals
        );
    }

    boolean matches(OutboundOrderLineView line) {
        return matches(line, forwarderCode, routeCode);
    }

    String label() {
        return forwarderName + " / " + routeName;
    }

    private static boolean matches(OutboundOrderLineView line, String forwarderCode, String routeCode) {
        return line != null
                && same(line.targetForwarderCode, forwarderCode)
                && same(line.routeCode, routeCode);
    }

    private static OutboundOrderLineView firstLine(PackingBoxView box, Map<Long, OutboundOrderLineView> lineById) {
        return box.items.isEmpty() ? null : lineById.get(box.items.get(0).outboundOrderLineId);
    }

    private static Map<Long, OutboundOrderLineView> lineById(OutboundOrderView order) {
        Map<Long, OutboundOrderLineView> result = new LinkedHashMap<>();
        for (OutboundOrderLineView line : order.lines) {
            if (line.id != null) result.put(Long.valueOf(line.id), line);
        }
        return result;
    }

    private static String required(String value, String message) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private static String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static boolean same(String left, String right) {
        return left != null && right != null && left.trim().equalsIgnoreCase(right.trim());
    }

    static final class Totals {
        int boxCount;
        int quantity;
        BigDecimal grossWeightKg = BigDecimal.ZERO;
        BigDecimal volumeCbm = BigDecimal.ZERO;
        final Set<String> partnerSkus = new HashSet<>();

        void add(PackingBoxView box) {
            boxCount += 1;
            quantity += box.quantity == null ? 0 : box.quantity;
            grossWeightKg = grossWeightKg.add(decimal(box.grossWeightKg));
            BigDecimal volume = decimal(box.lengthCm).multiply(decimal(box.widthCm)).multiply(decimal(box.heightCm));
            volumeCbm = volumeCbm.add(volume.divide(new BigDecimal("1000000"), 6, RoundingMode.HALF_UP));
            box.items.stream().map(item -> item.partnerSku).filter(value -> value != null && !value.isBlank())
                    .forEach(partnerSkus::add);
        }

        int skuCount() {
            return partnerSkus.size();
        }

        private static BigDecimal decimal(String value) {
            try {
                return value == null || value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value.trim());
            } catch (NumberFormatException exception) {
                return BigDecimal.ZERO;
            }
        }
    }
}
