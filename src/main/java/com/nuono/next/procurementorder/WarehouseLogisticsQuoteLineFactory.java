package com.nuono.next.procurementorder;

import com.nuono.next.procurementorder.ProcurementPurchaseOrderRecords.PurchaseOrderLogisticsQuoteLineRecord;

final class WarehouseLogisticsQuoteLineFactory {

    private WarehouseLogisticsQuoteLineFactory() {
    }

    static PurchaseOrderLogisticsQuoteLineRecord copyOf(PurchaseOrderLogisticsQuoteLineRecord source) {
        PurchaseOrderLogisticsQuoteLineRecord target = new PurchaseOrderLogisticsQuoteLineRecord();
        target.ownerUserId = source.ownerUserId;
        target.logicalStoreId = source.logicalStoreId;
        target.sourceStoreCode = source.sourceStoreCode;
        target.sourceStoreName = source.sourceStoreName;
        target.shippingOrderId = source.shippingOrderId;
        target.shippingOrderNo = source.shippingOrderNo;
        target.shippingOrderSegmentId = source.shippingOrderSegmentId;
        target.shippingOrderSegmentNo = source.shippingOrderSegmentNo;
        target.shippingOrderLineId = source.shippingOrderLineId;
        target.purchaseOrderId = source.purchaseOrderId;
        target.purchaseOrderNo = source.purchaseOrderNo;
        target.purchaseOrderTitle = source.purchaseOrderTitle;
        target.purchaseOrderItemId = source.purchaseOrderItemId;
        target.purchaseOrderItemSiteId = source.purchaseOrderItemSiteId;
        target.productMasterId = source.productMasterId;
        target.productVariantId = source.productVariantId;
        target.skuParent = source.skuParent;
        target.partnerSku = source.partnerSku;
        target.barcode = source.barcode;
        target.titleCache = source.titleCache;
        target.titleEn = source.titleEn;
        target.imageUrlCache = source.imageUrlCache;
        target.brandName = source.brandName;
        target.siteCode = source.siteCode;
        target.pskuCode = source.pskuCode;
        target.yiteMaterial = source.yiteMaterial;
        target.plannedTransportMode = source.plannedTransportMode;
        target.quantity = source.quantity;
        target.fulfillmentType = source.fulfillmentType;
        target.isNewProduct = source.isNewProduct;
        target.quoteStatus = source.quoteStatus;
        target.shippingSubmitStatus = source.shippingSubmitStatus;
        target.eligibilityStatus = source.eligibilityStatus;
        target.currency = source.currency;
        target.unitPrice = source.unitPrice;
        target.billingUnit = source.billingUnit;
        target.estimatedAmount = source.estimatedAmount;
        target.remark = source.remark;
        target.productLengthCm = source.productLengthCm;
        target.productWidthCm = source.productWidthCm;
        target.productHeightCm = source.productHeightCm;
        target.productWeightG = source.productWeightG;
        target.cartonLengthCm = source.cartonLengthCm;
        target.cartonWidthCm = source.cartonWidthCm;
        target.cartonHeightCm = source.cartonHeightCm;
        target.cartonWeightKg = source.cartonWeightKg;
        target.cartonQuantity = source.cartonQuantity;
        return target;
    }
}
