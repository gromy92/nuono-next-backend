package com.nuono.next.warehousedispatch;

public final class WarehouseDispatchRecords extends WarehousePackingRecords {

    private WarehouseDispatchRecords() {
    }

    public static class IdSequenceCommand extends WarehouseProcurementRecords.IdSequenceCommand {
        public IdSequenceCommand(String sequenceName, long initialValue) {
            super(sequenceName, initialValue);
        }
    }

    public static class PurchaseOrderAccessRecord extends WarehouseProcurementRecords.PurchaseOrderAccessRecord {}
    public static class PurchaseOrderItemRecord extends WarehouseProcurementRecords.PurchaseOrderItemRecord {}
    public static class PurchaseOrderItemSiteRecord extends WarehouseProcurementRecords.PurchaseOrderItemSiteRecord {}
    public static class FulfillmentBalanceRecord extends WarehouseProcurementRecords.FulfillmentBalanceRecord {}
    public static class PurchaseReceiptRow extends WarehouseProcurementRecords.PurchaseReceiptRow {}
    public static class FulfillmentConfirmationInsertRecord extends WarehouseProcurementRecords.FulfillmentConfirmationInsertRecord {}
    public static class FulfillmentConfirmationLineInsertRecord extends WarehouseProcurementRecords.FulfillmentConfirmationLineInsertRecord {}

    public static class BalanceQuantityDelta extends WarehouseProcurementRecords.BalanceQuantityDelta {
        public BalanceQuantityDelta() {
            super();
        }

        public BalanceQuantityDelta(Long balanceId, Integer confirmedDelta, Integer abnormalDelta, Long operatorUserId) {
            super(balanceId, confirmedDelta, abnormalDelta, operatorUserId);
        }
    }

    public static class DispatchPlanRecord extends WarehouseDispatchPlanRecords.DispatchPlanRecord {}
    public static class DispatchPlanLineRecord extends WarehouseDispatchPlanRecords.DispatchPlanLineRecord {}
    public static class DispatchPlanLineSourceRecord extends WarehouseDispatchPlanRecords.DispatchPlanLineSourceRecord {}
    public static class ShippingBatchRecord extends WarehouseShippingBatchRecords.ShippingBatchRecord {}
    public static class ShippingBatchSourceRecord extends WarehouseShippingBatchRecords.ShippingBatchSourceRecord {}
    public static class ShippingSuggestionOptionRecord extends WarehouseShippingOptionRecords.ShippingSuggestionOptionRecord {}
    public static class ShippingSuggestionLineRecord extends WarehouseShippingOptionRecords.ShippingSuggestionLineRecord {}
    public static class ForwarderRouteQuoteRecord extends WarehouseShippingOptionRecords.ForwarderRouteQuoteRecord {}
    public static class ShippingSuggestionLineSourceRecord extends WarehouseShippingOptionRecords.ShippingSuggestionLineSourceRecord {}
    public static class OutboundOrderRecord extends WarehouseOutboundRecords.OutboundOrderRecord {}
    public static class OutboundOrderLineRecord extends WarehouseOutboundRecords.OutboundOrderLineRecord {}
    public static class OutboundOrderLineSourceRecord extends WarehouseOutboundRecords.OutboundOrderLineSourceRecord {}
    public static class PackingListRecord extends WarehousePackingRecords.PackingListRecord {}
    public static class PackingBoxRecord extends WarehousePackingRecords.PackingBoxRecord {}
    public static class PackingBoxItemRecord extends WarehousePackingRecords.PackingBoxItemRecord {}
}
