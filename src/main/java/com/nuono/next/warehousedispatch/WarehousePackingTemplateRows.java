package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderLineView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.OutboundOrderView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxItemView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingBoxView;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class WarehousePackingTemplateRows {

    private WarehousePackingTemplateRows() {}

    static List<BoxRow> select(
            List<OutboundOrderView> orders,
            Map<String, List<PackingListView>> packingListsByOrder,
            WarehousePackingExportChannel channel
    ) {
        List<BoxRow> result = new ArrayList<>();
        for (OutboundOrderView order : orders) {
            Map<Long, OutboundOrderLineView> lineById = lineById(order);
            for (PackingListView packingList : packingListsByOrder.getOrDefault(order.id, List.of())) {
                for (PackingBoxView box : packingList.boxes) {
                    if (!channel.matches(firstLine(box, lineById))) continue;
                    List<ItemRow> items = new ArrayList<>();
                    for (PackingBoxItemView item : box.items) {
                        items.add(new ItemRow(item, lineById.get(item.outboundOrderLineId)));
                    }
                    result.add(new BoxRow(order, packingList, box, items));
                }
            }
        }
        return result;
    }

    private static Map<Long, OutboundOrderLineView> lineById(OutboundOrderView order) {
        Map<Long, OutboundOrderLineView> result = new LinkedHashMap<>();
        for (OutboundOrderLineView line : order.lines) {
            try {
                if (line.id != null) result.put(Long.valueOf(line.id), line);
            } catch (NumberFormatException ignored) {
                // Persisted outbound line ids are numeric; malformed legacy ids remain unmapped.
            }
        }
        return result;
    }

    private static OutboundOrderLineView firstLine(
            PackingBoxView box,
            Map<Long, OutboundOrderLineView> lineById
    ) {
        return box.items.isEmpty() ? null : lineById.get(box.items.get(0).outboundOrderLineId);
    }

    static final class BoxRow {
        final OutboundOrderView order;
        final PackingListView packingList;
        final PackingBoxView box;
        final List<ItemRow> items;

        BoxRow(
                OutboundOrderView order,
                PackingListView packingList,
                PackingBoxView box,
                List<ItemRow> items
        ) {
            this.order = order;
            this.packingList = packingList;
            this.box = box;
            this.items = items;
        }
    }

    static final class ItemRow {
        final PackingBoxItemView item;
        final OutboundOrderLineView line;

        ItemRow(PackingBoxItemView item, OutboundOrderLineView line) {
            this.item = item;
            this.line = line;
        }
    }
}
