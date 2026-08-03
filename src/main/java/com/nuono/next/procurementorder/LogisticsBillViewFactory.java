package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsExpectedBillComponentRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.LogisticsExpectedBillRecord;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.LogisticsBillComponentView;
import com.nuono.next.procurementorder.ProcurementPurchaseOrderViews.LogisticsBillView;
import java.util.List;

final class LogisticsBillViewFactory {

    private LogisticsBillViewFactory() {
    }

    static LogisticsBillView toView(
            LogisticsExpectedBillRecord bill,
            List<LogisticsExpectedBillComponentRecord> components
    ) {
        LogisticsBillView view = new LogisticsBillView();
        view.id = text(bill.id);
        view.expectedBillNo = bill.expectedBillNo;
        view.shippingOrderId = text(bill.shippingOrderId);
        view.shippingOrderNo = bill.shippingOrderNo;
        view.shippingOrderTitle = bill.shippingOrderTitle;
        view.shippingOrderSegmentId = text(bill.shippingOrderSegmentId);
        view.shippingOrderSegmentNo = bill.shippingOrderSegmentNo;
        view.forwarderCode = bill.forwarderCode;
        view.forwarderName = bill.forwarderName;
        view.routeCode = bill.routeCode;
        view.routeName = bill.routeName;
        view.serviceCode = bill.serviceCode;
        view.serviceName = bill.serviceName;
        view.transportMode = bill.transportMode;
        view.currency = bill.currency;
        view.expectedTotalAmount = bill.expectedTotalAmount;
        view.expectedTotalCny = bill.expectedTotalCny;
        view.actualTotalCny = bill.actualTotalCny;
        view.diffAmountCny = bill.diffAmountCny;
        view.componentCount = bill.componentCount == null ? 0 : bill.componentCount;
        view.billStatus = bill.billStatus;
        view.reconciliationStatus = bill.reconciliationStatus;
        view.createdAt = bill.createdAt;
        view.updatedAt = bill.updatedAt;
        safe(components).stream().map(LogisticsBillViewFactory::toComponent).forEach(view.components::add);
        return view;
    }

    private static LogisticsBillComponentView toComponent(LogisticsExpectedBillComponentRecord component) {
        LogisticsBillComponentView view = new LogisticsBillComponentView();
        view.id = text(component.id);
        view.shippingOrderSegmentId = text(component.shippingOrderSegmentId);
        view.shippingOrderLineId = text(component.shippingOrderLineId);
        view.quoteLineId = text(component.quoteLineId);
        view.barcode = component.barcode;
        view.pskuCode = component.pskuCode;
        view.siteCode = component.siteCode;
        view.feeType = component.feeType;
        view.quantity = component.quantity;
        view.chargeQuantity = component.chargeQuantity;
        view.chargeUnit = component.chargeUnit;
        view.unitPrice = component.unitPrice;
        view.currency = component.currency;
        view.expectedAmount = component.expectedAmount;
        view.expectedAmountCny = component.expectedAmountCny;
        return view;
    }

    private static String text(Long value) {
        return value == null ? null : String.valueOf(value);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
