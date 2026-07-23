package com.nuono.next.warehousedispatch;

public final class WarehouseDispatchViews extends WarehousePackingViews {

    private WarehouseDispatchViews() {
    }

    public static class FulfillmentItemView extends WarehouseProcurementViews.FulfillmentItemView {}
    public static class ConfirmationView extends WarehouseProcurementViews.ConfirmationView {}
    public static class ConfirmationLineView extends WarehouseProcurementViews.ConfirmationLineView {}
    public static class ReadyItemView extends WarehouseProcurementViews.ReadyItemView {}
    public static class PurchaseReceiptOrderView extends WarehouseProcurementViews.PurchaseReceiptOrderView {}
    public static class PurchaseReceiptItemView extends WarehouseProcurementViews.PurchaseReceiptItemView {}
    public static class ReadySourceView extends WarehouseProcurementViews.ReadySourceView {}
    public static class DispatchPlanView extends WarehouseDispatchPlanViews.DispatchPlanView {}
    public static class DispatchPlanLineView extends WarehouseDispatchPlanViews.DispatchPlanLineView {}
    public static class DispatchPlanLineSourceView extends WarehouseDispatchPlanViews.DispatchPlanLineSourceView {}
    public static class LogisticsHandoffView extends WarehouseDispatchPlanViews.LogisticsHandoffView {}
    public static class ShippingSuggestionOptionView extends WarehouseShippingSuggestionViews.ShippingSuggestionOptionView {}
    public static class ShippingSuggestionLineView extends WarehouseShippingSuggestionViews.ShippingSuggestionLineView {}
    public static class ShippingSuggestionLineSourceView extends WarehouseShippingSuggestionViews.ShippingSuggestionLineSourceView {}
    public static class ShippingBatchView extends WarehouseShippingBatchViews.ShippingBatchView {}
    public static class ShippingBatchSourceView extends WarehouseShippingBatchViews.ShippingBatchSourceView {}
    public static class MobileShippingDecisionPreviewView extends WarehouseMobileShippingViews.MobileShippingDecisionPreviewView {}
    public static class MobileShippingDecisionConfirmView extends WarehouseMobileShippingViews.MobileShippingDecisionConfirmView {}
    public static class MobileShippingDecisionOptionView extends WarehouseMobileShippingViews.MobileShippingDecisionOptionView {}
    public static class MobileShippingDecisionLineView extends WarehouseMobileShippingViews.MobileShippingDecisionLineView {}
    public static class MobileShippingDecisionForwarderAllocationView extends WarehouseMobileShippingViews.MobileShippingDecisionForwarderAllocationView {}
    public static class PurchaseOrderLogisticsComparisonView extends WarehouseLogisticsComparisonViews.PurchaseOrderLogisticsComparisonView {}
    public static class PurchaseOrderLogisticsSegmentView extends WarehouseLogisticsComparisonViews.PurchaseOrderLogisticsSegmentView {}
    public static class OutboundOrderView extends WarehouseOutboundViews.OutboundOrderView {}
    public static class OutboundOrderLineView extends WarehouseOutboundViews.OutboundOrderLineView {}
    public static class OutboundOrderLineSourceView extends WarehouseOutboundViews.OutboundOrderLineSourceView {}
    public static class PackingListView extends WarehousePackingViews.PackingListView {}
    public static class PackingBoxView extends WarehousePackingViews.PackingBoxView {}
    public static class PackingBoxItemView extends WarehousePackingViews.PackingBoxItemView {}
}
