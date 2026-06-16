package com.nuono.next.procurementorder;

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

    public static class ItemCommand {
        public String psku;
        public String site;
        public String transportMode;
        public Integer quantity;
        public List<SiteQuantityCommand> siteQuantities = new ArrayList<>();
    }

    public static class SiteQuantityCommand {
        public String siteCode;
        public String transportMode;
        public Integer quantity;
    }
}
