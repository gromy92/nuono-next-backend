package com.nuono.next.officialwarehouse;

public final class OfficialWarehouseStatisticsCommands {

    private OfficialWarehouseStatisticsCommands() {
    }

    public static class StockCorrectionCommand {
        public String storeCode;
        public String siteCode;
        public String targetRefType;
        public String targetRefId;
        public String productVariantId;
        public String productSiteOfferId;
        public String fromStockBucket;
        public String toStockBucket;
        public Integer quantity;
        public String warehouseCode;
        public String reasonCode;
        public String reasonText;
    }

    public static class InventorySyncCommand {
        public String storeCode;
        public String siteCode;
        public Integer maxPages;
    }

    public static class FbnExportCreateCommand {
        public String storeCode;
        public String siteCode;
        public String exportCategoryCode;
        public String fromDate;
        public String toDate;
    }

    public static class FbnReceivedImportCommand {
        public String storeCode;
        public String siteCode;
        public Boolean logStatus;
    }

    public static class ScheduledDeliveryAccuracyImportCommand {
        public String storeCode;
        public String siteCode;
        public Boolean logStatus;
    }

    public static class ScheduledDeliveryAccuracyRematchCommand {
        public String storeCode;
        public String siteCode;
    }

    public static class ScheduledDeliveryAccuracyMissingAsnSyncCommand {
        public String storeCode;
        public String siteCode;
        public Boolean dryRun;
        public Integer limit;
        public Boolean rematchAfterSync;
    }
}
