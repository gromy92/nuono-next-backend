package com.nuono.next.procurementorder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProcurementPurchaseOrderCommands {

    private ProcurementPurchaseOrderCommands() {
    }

    public static class CreateOrderCommand {
        public String storeCode;
        public String title;
        public String remark;
        public List<String> siteCodes = new ArrayList<>();
        public List<ItemCommand> items = new ArrayList<>();
    }

    public static class AddItemsCommand {
        public List<ItemCommand> items = new ArrayList<>();
    }

    public static class UpdateOrderCommand {
        public String title;
        public String remark;
    }

    public static class UpdateItemCommand extends ItemCommand {
    }

    public static class UpdateItemSourcingRequirementCommand {
        public String sourcingSpec;
        public String sourcingSize;
        public String sourcingColor;
    }

    public static class CreateShippingOrderCommand {
        public String title;
        public String remark;
        public List<String> purchaseOrderIds = new ArrayList<>();
    }

    public static class UpdateShippingOrderCommand {
        public String title;
        public String remark;
    }

    public static class UpdateShippingOrderLineYiteMaterialCommand {
        public String yiteMaterial;
    }

    public static class UpdateShippingOrderLineQuoteCommand {
        public String forwarderCode;
        public String routeCode;
        public BigDecimal unitPrice;
        public String currency;
        public String billingUnit;
        public String yiteMaterial;
        public String remark;
    }

    public static class UpdateShippingOrderLineQuotesCommand extends UpdateShippingOrderLineQuoteCommand {
        public List<String> lineIds = new ArrayList<>();
    }

    public static class ShippingOrderSegmentScopeCommand {
        public List<String> segmentIds = new ArrayList<>();
    }

    public static class ItemCommand {
        public String psku;
        public String site;
        public String transportMode;
        public Integer quantity;
        public String fulfillmentType;
        public String fulfillmentSourceName;
        public List<SiteQuantityCommand> siteQuantities = new ArrayList<>();
    }

    public static class SiteQuantityCommand {
        public String siteCode;
        public String transportMode;
        public Integer quantity;
    }
}
