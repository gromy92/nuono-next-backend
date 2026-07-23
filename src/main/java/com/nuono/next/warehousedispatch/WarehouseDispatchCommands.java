package com.nuono.next.warehousedispatch;

import java.util.ArrayList;
import java.util.List;

public final class WarehouseDispatchCommands {

    private WarehouseDispatchCommands() {
    }

    public static class UpdateFulfillmentCommand {
        public String fulfillmentType;
        public String sourceName;
    }

    public static class ConfirmationCommand {
        public String clientRequestId;
        public String purchaseOrderId;
        public String confirmationType;
        public String sourcePartyName;
        public String remark;
        public List<ConfirmationLineCommand> lines = new ArrayList<>();
    }

    public static class ConfirmationLineCommand {
        public String purchaseOrderItemId;
        public Long purchaseOrderItemSiteId;
        public Long fulfillmentBalanceId;
        public Integer confirmedQuantity;
        public Integer abnormalQuantity;
        public Integer normalReceivedQuantity;
        public Integer replenishmentQuantity;
        public String replenishmentReason;
        public Integer returnQuantity;
        public Integer damageQuantity;
        public Integer overReceivedQuantity;
        public String keeperSnapshotJson;
        public String exceptionReason;
    }

    public static class CreateDispatchPlanCommand {
        public String clientRequestId;
        public String remark;
        public List<DispatchPlanSourceCommand> sources = new ArrayList<>();
    }

    public static class DispatchPlanSourceCommand {
        public Long fulfillmentBalanceId;
        public Integer quantity;
        public String targetSiteCode;
        public String actualTransportMode;
    }

    public static class UpdateDispatchTargetCommand {
        public String targetSiteCode;
        public String targetTransportMode;
    }

    public static class CreateShippingBatchCommand {
        public String remark;
        public List<ShippingBatchSourceCommand> sources = new ArrayList<>();
    }

    public static class CreateShippingBatchFromDispatchPlanCommand {
        public List<String> selectedForwarderCodes = new ArrayList<>();
    }

    public static class ShippingBatchSourceCommand {
        public Long fulfillmentBalanceId;
        public Integer quantity;
    }

    public static class MobileShippingDecisionPreviewCommand {
        public String siteCode;
        public String transportMode;
        public Boolean sensitiveConfirmed;
        public String generationMode;
        public List<String> targetForwarderCodes = new ArrayList<>();
        public List<String> targetOptionKeys = new ArrayList<>();
        public List<ShippingBatchSourceCommand> sources = new ArrayList<>();
    }

    public static class MobileShippingDecisionConfirmCommand extends MobileShippingDecisionPreviewCommand {
        public String acceptedOptionKey;
        public String remark;
    }

    public static class CreateShippingTargetOptionCommand {
        public String optionName;
        public String airForwarderCode;
        public String seaForwarderCode;
    }

    public static class IssueShippingBatchCommand {
        public String optionId;
    }

    public static class CreatePackingListCommand {
        public String remark;
    }

    public static class ReplacePackingBoxesCommand {
        public String remark;
        public List<PackingBoxCommand> boxes = new ArrayList<>();
    }

    public static class PackingBoxCommand {
        public String boxNo;
        public String status;
        public String lengthCm;
        public String widthCm;
        public String heightCm;
        public String grossWeightKg;
        public List<PackingBoxItemCommand> items = new ArrayList<>();
    }

    public static class PackingBoxItemCommand {
        public Long outboundOrderLineId;
        public Integer quantity;
    }

    public static class ConfirmPackingListsCommand {
        public List<String> packingListIds = new ArrayList<>();
    }

    public static class HandoffFailureCommand {
        public String handoffRequestNo;
        public String errorMessage;
    }
}
