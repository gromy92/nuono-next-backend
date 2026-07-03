package com.nuono.next.productlogisticscost;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class ProductLogisticsCostCommands {

    private ProductLogisticsCostCommands() {
    }

    public static class ManualCurrentQuoteCommand {
        public String storeCode;
        public String partnerSku;
        public String siteCode;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String feeType;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String chargeUnit;
        public BigDecimal unitCostCny;
        public String remark;
    }

    public static class ManualRateCardCommand {
        public String siteCode;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String feeType;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String chargeUnit;
        public BigDecimal unitCostCny;
        public String sourceType;
        public String sourceReference;
        public String remark;
    }

    public static class BatchCategoryAssignmentCommand {
        public String storeCode;
        public String siteCode;
        public String forwarderCode;
        public String forwarderName;
        public String transportMode;
        public String feeType;
        public String cargoCategoryCode;
        public String cargoCategoryName;
        public String remark;
        public List<CategoryAssignmentItem> items = new ArrayList<>();
    }

    public static class CategoryAssignmentItem {
        public String partnerSku;
    }

    public static class ProductMatchRow {
        public Long logicalStoreId;
        public Long productMasterId;
        public Long productVariantId;
        public String partnerSku;
        public String skuParent;
        public String barcode;
        public String siteCode;
    }
}
