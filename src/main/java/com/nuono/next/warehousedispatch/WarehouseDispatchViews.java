package com.nuono.next.warehousedispatch;

import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.DispatchPlanRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class WarehouseDispatchViews {

    private WarehouseDispatchViews() {
    }

    public static class FulfillmentItemView {
        public String purchaseOrderId;
        public String purchaseOrderItemId;
        public String fulfillmentType;
        public String sourceName;
    }

    public static class ConfirmationView {
        public String id;
        public String confirmationNo;
        public String confirmationType;
        public String status;
        public Integer expectedQuantity;
        public Integer confirmedQuantity;
        public Integer abnormalQuantity;
        public List<ConfirmationLineView> lines = new ArrayList<>();
    }

    public static class ConfirmationLineView {
        public String purchaseOrderItemId;
        public String partnerSku;
        public Integer expectedQuantity;
        public Integer confirmedQuantity;
        public Integer abnormalQuantity;
    }

    public static class ReadyItemView {
        public String productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public Boolean isNewProduct;
        public Boolean manualConfirmRequired;
        public Boolean logisticsQuoteBlocking;
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public String fulfillmentType;
        public String specStatus;
        public Integer availableQuantity;
        public List<ReadySourceView> sources = new ArrayList<>();
    }

    public static class PurchaseReceiptOrderView {
        public String id;
        public String orderNo;
        public String title;
        public String storeName;
        public String storeCode;
        public String createdAt;
        public List<PurchaseReceiptItemView> items = new ArrayList<>();
    }

    public static class PurchaseReceiptItemView {
        public String id;
        public String orderId;
        public String orderNo;
        public String purchaseOrderTitle;
        public String storeName;
        public String psku;
        public String title;
        public String imageUrl;
        public String siteCode;
        public String transportMode;
        public Integer expectedQty;
        public Integer receivedQty;
        public Integer plannedQty;
        public String specStatus;
        public String fulfillmentType;
        public String fulfillmentSourceName;
        public String exceptionText;
    }

    public static class ReadySourceView {
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String plannedTransportMode;
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public Boolean logisticsQuoteBlocking;
        public Integer availableQuantity;
    }

    public static class DispatchPlanView {
        public String id;
        public Long ownerUserId;
        public String planNo;
        public String status;
        public Integer itemCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public String handoffRequestNo;
        public Integer handoffGenerationNo;
        public String handoffErrorMessage;
        public String createdAt;
        public String updatedAt;
        public List<DispatchPlanLineView> lines = new ArrayList<>();

        public DispatchPlanRecord toRecord() {
            DispatchPlanRecord record = new DispatchPlanRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.ownerUserId = ownerUserId;
            record.planNo = planNo;
            record.status = status;
            record.itemCount = itemCount;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.handoffRequestNo = handoffRequestNo;
            record.handoffGenerationNo = handoffGenerationNo;
            record.handoffErrorMessage = handoffErrorMessage;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class DispatchPlanLineView {
        public String id;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String specStatus;
        public Integer quantity;
        public List<DispatchPlanLineSourceView> sources = new ArrayList<>();

        public DispatchPlanLineRecord toRecord() {
            DispatchPlanLineRecord record = new DispatchPlanLineRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.specStatus = specStatus;
            record.quantity = quantity;
            record.sourceCount = sources == null ? 0 : sources.size();
            return record;
        }
    }

    public static class DispatchPlanLineSourceView {
        public String id;
        public Long dispatchPlanId;
        public Long dispatchPlanLineId;
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String plannedTransportMode;
        public String fulfillmentType;
        public Integer quantity;

        public DispatchPlanLineSourceRecord toRecord() {
            DispatchPlanLineSourceRecord record = new DispatchPlanLineSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.dispatchPlanId = dispatchPlanId;
            record.dispatchPlanLineId = dispatchPlanLineId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.sourceStoreCode = sourceStoreCode;
            record.sourceStoreName = sourceStoreName;
            record.purchaseOrderId = purchaseOrderId;
            record.purchaseOrderNo = purchaseOrderNo;
            record.purchaseOrderItemId = purchaseOrderItemId;
            record.purchaseOrderItemSiteId = purchaseOrderItemSiteId;
            record.plannedTransportMode = plannedTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.quantity = quantity;
            return record;
        }
    }

    public static class LogisticsHandoffView {
        public String dispatchPlanId;
        public String dispatchPlanNo;
        public String status;
        public Integer handoffGenerationNo;
        public String handoffRequestNo;
        public List<DispatchPlanLineView> lines = new ArrayList<>();
    }

    public static class ShippingBatchView {
        public String id;
        public Long ownerUserId;
        public String batchNo;
        public String status;
        public String selectedOptionId;
        public Integer sourceCount;
        public Integer skuCount;
        public Integer totalQuantity;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<ShippingBatchSourceView> sources = new ArrayList<>();
        public List<ShippingSuggestionOptionView> options = new ArrayList<>();

        public ShippingBatchRecord toRecord() {
            ShippingBatchRecord record = new ShippingBatchRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.ownerUserId = ownerUserId;
            record.batchNo = batchNo;
            record.status = status;
            record.selectedOptionId = selectedOptionId == null ? null : Long.valueOf(selectedOptionId);
            record.sourceCount = sourceCount;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.remark = remark;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class ShippingBatchSourceView {
        public String id;
        public Long batchId;
        public Long fulfillmentBalanceId;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String plannedTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public String productLengthCm;
        public String productWidthCm;
        public String productHeightCm;
        public String productWeightG;
        public String logisticsProfileStatus;
        public Boolean sensitiveFlag;
        public List<String> sensitiveReasons = new ArrayList<>();
        public String logisticsQuoteStatus;
        public String logisticsShippingSubmitStatus;
        public Boolean logisticsQuoteBlocking;
        public Integer reservedQuantity;

        public ShippingBatchSourceRecord toRecord() {
            ShippingBatchSourceRecord record = new ShippingBatchSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.batchId = batchId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.sourceStoreCode = sourceStoreCode;
            record.sourceStoreName = sourceStoreName;
            record.purchaseOrderId = purchaseOrderId;
            record.purchaseOrderNo = purchaseOrderNo;
            record.purchaseOrderTitle = purchaseOrderTitle;
            record.purchaseOrderItemId = purchaseOrderItemId;
            record.purchaseOrderItemSiteId = purchaseOrderItemSiteId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.plannedTransportMode = plannedTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.sourcePartyName = sourcePartyName;
            record.specStatus = specStatus;
            record.reservedQuantity = reservedQuantity;
            return record;
        }
    }

    public static class ShippingSuggestionOptionView {
        public String id;
        public Long batchId;
        public String optionType;
        public String optionName;
        public String status;
        public Boolean selectedFlag;
        public Integer score;
        public Integer skuCount;
        public Integer totalQuantity;
        public Integer airQuantity;
        public Integer seaQuantity;
        public Integer specMissingCount;
        public Integer warningCount;
        public String forwarderPlanType;
        public Boolean autoRecommended;
        public List<String> targetForwarderCodes = new ArrayList<>();
        public List<String> targetForwarderNames = new ArrayList<>();
        public List<String> routeCodes = new ArrayList<>();
        public String evaluationStatus;
        public List<String> blockedReasons = new ArrayList<>();
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedTotalAmount;
        public BigDecimal avgUnitAmount;
        public String currency;
        public List<ShippingSuggestionLineView> lines = new ArrayList<>();

        public ShippingSuggestionOptionRecord toRecord() {
            ShippingSuggestionOptionRecord record = new ShippingSuggestionOptionRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.batchId = batchId;
            record.optionType = optionType;
            record.optionName = optionName;
            record.status = status;
            record.selectedFlag = selectedFlag;
            record.score = score;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.airQuantity = airQuantity;
            record.seaQuantity = seaQuantity;
            record.specMissingCount = specMissingCount;
            record.warningCount = warningCount;
            return record;
        }
    }

    public static class ShippingSuggestionLineView {
        public String id;
        public Long optionId;
        public Long batchId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public String targetForwarderCode;
        public String targetForwarderName;
        public String routeCode;
        public String routeName;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String quoteCargoCategoryCode;
        public String quoteCargoCategoryName;
        public Boolean cargoCategoryReviewRequired;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public BigDecimal chargeableWeightKg;
        public BigDecimal estimatedAmount;
        public String currency;
        public Integer quantity;
        public List<ShippingSuggestionLineSourceView> sources = new ArrayList<>();

        public ShippingSuggestionLineRecord toRecord() {
            ShippingSuggestionLineRecord record = new ShippingSuggestionLineRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.optionId = optionId;
            record.batchId = batchId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.sourcePartyName = sourcePartyName;
            record.specStatus = specStatus;
            record.quantity = quantity;
            record.sourceCount = sources == null ? 0 : sources.size();
            return record;
        }
    }

    public static class ShippingSuggestionLineSourceView {
        public String id;
        public Long optionId;
        public Long lineId;
        public Long batchId;
        public Long batchSourceId;
        public Long fulfillmentBalanceId;
        public String plannedTransportMode;
        public Integer quantity;

        public ShippingSuggestionLineSourceRecord toRecord() {
            ShippingSuggestionLineSourceRecord record = new ShippingSuggestionLineSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.optionId = optionId;
            record.lineId = lineId;
            record.batchId = batchId;
            record.batchSourceId = batchSourceId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.plannedTransportMode = plannedTransportMode;
            record.quantity = quantity;
            return record;
        }
    }

    public static class PurchaseOrderLogisticsComparisonView {
        public String purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public String sourceStoreCode;
        public String sourceStoreName;
        public Integer skuCount;
        public Integer totalQuantity;
        public String quantityBasis;
        public String quantityBasisLabel;
        public String fulfillmentReadinessNote;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public String recommendedOptionId;
        public String recommendedOptionName;
        public BigDecimal recommendedEstimatedAmount;
        public String currency;
        public List<String> defects = new ArrayList<>();
        public List<String> missingPlanSuggestions = new ArrayList<>();
        public List<PurchaseOrderLogisticsSegmentView> segments = new ArrayList<>();
    }

    public static class PurchaseOrderLogisticsSegmentView {
        public String segmentKey;
        public String siteCode;
        public String plannedTransportMode;
        public Integer skuCount;
        public Integer totalQuantity;
        public String quantityBasis;
        public String quantityBasisLabel;
        public BigDecimal actualWeightKg;
        public BigDecimal volumeCbm;
        public String recommendedOptionId;
        public String recommendedOptionName;
        public BigDecimal recommendedEstimatedAmount;
        public String currency;
        public List<String> defects = new ArrayList<>();
        public List<String> missingPlanSuggestions = new ArrayList<>();
        public List<ShippingSuggestionOptionView> options = new ArrayList<>();
    }

    public static class OutboundOrderView {
        public String id;
        public Long batchId;
        public Long optionId;
        public Long ownerUserId;
        public String outboundNo;
        public String status;
        public String originType;
        public String originName;
        public Integer skuCount;
        public Integer totalQuantity;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<OutboundOrderLineView> lines = new ArrayList<>();

        public OutboundOrderRecord toRecord() {
            OutboundOrderRecord record = new OutboundOrderRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.batchId = batchId;
            record.optionId = optionId;
            record.ownerUserId = ownerUserId;
            record.outboundNo = outboundNo;
            record.status = status;
            record.originType = originType;
            record.originName = originName;
            record.skuCount = skuCount;
            record.totalQuantity = totalQuantity;
            record.remark = remark;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class OutboundOrderLineView {
        public String id;
        public Long outboundOrderId;
        public Long batchId;
        public Long optionLineId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String productTitle;
        public String productImageUrl;
        public String siteCode;
        public String actualTransportMode;
        public String fulfillmentType;
        public String sourcePartyName;
        public String specStatus;
        public Integer quantity;
        public Integer packedQuantity;
        public List<OutboundOrderLineSourceView> sources = new ArrayList<>();

        public OutboundOrderLineRecord toRecord() {
            OutboundOrderLineRecord record = new OutboundOrderLineRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.outboundOrderId = outboundOrderId;
            record.batchId = batchId;
            record.optionLineId = optionLineId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.skuParent = skuParent;
            record.titleCache = productTitle;
            record.imageUrlCache = productImageUrl;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.fulfillmentType = fulfillmentType;
            record.sourcePartyName = sourcePartyName;
            record.specStatus = specStatus;
            record.quantity = quantity;
            record.packedQuantity = packedQuantity;
            return record;
        }
    }

    public static class OutboundOrderLineSourceView {
        public String id;
        public Long outboundOrderId;
        public Long outboundOrderLineId;
        public Long batchSourceId;
        public Long fulfillmentBalanceId;
        public Long purchaseOrderId;
        public String purchaseOrderNo;
        public String purchaseOrderTitle;
        public Long purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public String plannedTransportMode;
        public Integer quantity;

        public OutboundOrderLineSourceRecord toRecord() {
            OutboundOrderLineSourceRecord record = new OutboundOrderLineSourceRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.outboundOrderId = outboundOrderId;
            record.outboundOrderLineId = outboundOrderLineId;
            record.batchSourceId = batchSourceId;
            record.fulfillmentBalanceId = fulfillmentBalanceId;
            record.purchaseOrderId = purchaseOrderId;
            record.purchaseOrderNo = purchaseOrderNo;
            record.purchaseOrderTitle = purchaseOrderTitle;
            record.purchaseOrderItemId = purchaseOrderItemId;
            record.purchaseOrderItemSiteId = purchaseOrderItemSiteId;
            record.plannedTransportMode = plannedTransportMode;
            record.quantity = quantity;
            return record;
        }
    }

    public static class PackingListView {
        public String id;
        public Long outboundOrderId;
        public Long ownerUserId;
        public String packingNo;
        public String status;
        public Integer boxCount;
        public Integer packedQuantity;
        public String grossWeightKg;
        public String volumeCbm;
        public String remark;
        public String createdAt;
        public String updatedAt;
        public List<PackingBoxView> boxes = new ArrayList<>();

        public PackingListRecord toRecord() {
            PackingListRecord record = new PackingListRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.outboundOrderId = outboundOrderId;
            record.ownerUserId = ownerUserId;
            record.packingNo = packingNo;
            record.status = status;
            record.boxCount = boxCount;
            record.packedQuantity = packedQuantity;
            record.remark = remark;
            record.createdAt = createdAt;
            record.updatedAt = updatedAt;
            return record;
        }
    }

    public static class PackingBoxView {
        public String id;
        public Long packingListId;
        public Long outboundOrderId;
        public String boxNo;
        public String lengthCm;
        public String widthCm;
        public String heightCm;
        public String grossWeightKg;
        public Integer quantity;
        public List<PackingBoxItemView> items = new ArrayList<>();

        public PackingBoxRecord toRecord() {
            PackingBoxRecord record = new PackingBoxRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.packingListId = packingListId;
            record.outboundOrderId = outboundOrderId;
            record.boxNo = boxNo;
            record.quantity = quantity;
            return record;
        }
    }

    public static class PackingBoxItemView {
        public String id;
        public Long packingListId;
        public Long packingBoxId;
        public Long outboundOrderId;
        public Long outboundOrderLineId;
        public Long productVariantId;
        public String partnerSku;
        public String siteCode;
        public String actualTransportMode;
        public Integer quantity;

        public PackingBoxItemRecord toRecord() {
            PackingBoxItemRecord record = new PackingBoxItemRecord();
            record.id = id == null ? null : Long.valueOf(id);
            record.packingListId = packingListId;
            record.packingBoxId = packingBoxId;
            record.outboundOrderId = outboundOrderId;
            record.outboundOrderLineId = outboundOrderLineId;
            record.productVariantId = productVariantId;
            record.partnerSku = partnerSku;
            record.siteCode = siteCode;
            record.actualTransportMode = actualTransportMode;
            record.quantity = quantity;
            return record;
        }
    }
}
