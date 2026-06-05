package com.nuono.next.procurement.aliorder;

public class Ali1688HistoricalOrderCleanupView {

    public static class DeleteOrderRequest {
        private String storeCode;
        private String siteCode;
        private String reason;

        public String getStoreCode() {
            return storeCode;
        }

        public void setStoreCode(String storeCode) {
            this.storeCode = storeCode;
        }

        public String getSiteCode() {
            return siteCode;
        }

        public void setSiteCode(String siteCode) {
            this.siteCode = siteCode;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class DeleteOrderResult {
        private Long orderId;
        private String status;
        private String reason;

        public static DeleteOrderResult deleted(Long orderId, String reason) {
            DeleteOrderResult result = new DeleteOrderResult();
            result.setOrderId(orderId);
            result.setStatus("deleted");
            result.setReason(reason);
            return result;
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
