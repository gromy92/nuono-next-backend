package com.nuono.next.replenishmentplan;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReplenishmentPlanRepository {

    List<StockRow> listFbnSupermallStock(Long ownerUserId, String storeCode, String siteCode);

    List<InboundRow> listActiveInbound(Long ownerUserId, String storeCode, String siteCode);

    final class StockRow {
        private final String partnerSku;
        private final String sku;
        private final BigDecimal currentStockUnits;
        private final BigDecimal fbnStockUnits;
        private final BigDecimal supermallStockUnits;

        public StockRow(
                String partnerSku,
                String sku,
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits
        ) {
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.currentStockUnits = currentStockUnits;
            this.fbnStockUnits = fbnStockUnits;
            this.supermallStockUnits = supermallStockUnits;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public String getSku() {
            return sku;
        }

        public BigDecimal getCurrentStockUnits() {
            return currentStockUnits;
        }

        public BigDecimal getFbnStockUnits() {
            return fbnStockUnits;
        }

        public BigDecimal getSupermallStockUnits() {
            return supermallStockUnits;
        }
    }

    final class InboundRow {
        private final String partnerSku;
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final LocalDate etaDate;
        private final BigDecimal remainingQuantity;

        public InboundRow(
                String partnerSku,
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity
        ) {
            this.partnerSku = partnerSku;
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.etaDate = etaDate;
            this.remainingQuantity = remainingQuantity;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public Long getBatchId() {
            return batchId;
        }

        public String getBatchReferenceNo() {
            return batchReferenceNo;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public String getBatchStatus() {
            return batchStatus;
        }

        public LocalDate getEtaDate() {
            return etaDate;
        }

        public BigDecimal getRemainingQuantity() {
            return remainingQuantity;
        }
    }
}
