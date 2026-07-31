package com.nuono.next.officialwarehouse;

public final class OfficialWarehouseBatchSummaryRecords {

    private OfficialWarehouseBatchSummaryRecords() {
    }

    public static class ShippingBatchRawLineRecord {
        public Long batchId;
        public Long goodsLineId;
        public String psku;
        public String sku;
        public String msku;
        public String title;
        public Integer quantity;
    }
}
