package com.nuono.next.replenishmentplan;

import com.nuono.next.product.ProductImageUrlSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReplenishmentPlanRepository {

    List<StockRow> listFbnSupermallStock(Long ownerUserId, String storeCode, String siteCode);

    List<InboundRow> listActiveInbound(Long ownerUserId, String storeCode, String siteCode);

    final class StockRow {
        private final String partnerSku;
        private final String sku;
        private final String imageUrl;
        private final LocalDate listingAt;
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
            this(partnerSku, sku, null, null, currentStockUnits, fbnStockUnits, supermallStockUnits);
        }

        public StockRow(
                String partnerSku,
                String sku,
                String imageUrl,
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits
        ) {
            this(partnerSku, sku, imageUrl, null, currentStockUnits, fbnStockUnits, supermallStockUnits);
        }

        public StockRow(
                String partnerSku,
                String sku,
                String imageUrl,
                LocalDate listingAt,
                BigDecimal currentStockUnits,
                BigDecimal fbnStockUnits,
                BigDecimal supermallStockUnits
        ) {
            this.partnerSku = partnerSku;
            this.sku = sku;
            this.imageUrl = ProductImageUrlSupport.normalize(imageUrl);
            this.listingAt = listingAt;
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

        public String getImageUrl() {
            return imageUrl;
        }

        public LocalDate getListingAt() {
            return listingAt;
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

    final class InboundLineRow {
        private final Long lineId;
        private final String partnerSku;
        private final String destinationCode;
        private final String resolvedSiteCode;
        private final String scopeStatus;
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final LocalDate etaDate;
        private final BigDecimal remainingQuantity;

        public InboundLineRow(
                Long lineId,
                String partnerSku,
                String destinationCode,
                String resolvedSiteCode,
                String scopeStatus,
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity
        ) {
            this.lineId = lineId;
            this.partnerSku = partnerSku;
            this.destinationCode = destinationCode;
            this.resolvedSiteCode = resolvedSiteCode;
            this.scopeStatus = scopeStatus;
            this.batchId = batchId;
            this.batchReferenceNo = batchReferenceNo;
            this.transportMode = transportMode;
            this.batchStatus = batchStatus;
            this.etaDate = etaDate;
            this.remainingQuantity = remainingQuantity;
        }

        public Long getLineId() {
            return lineId;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public String getDestinationCode() {
            return destinationCode;
        }

        public String getResolvedSiteCode() {
            return resolvedSiteCode;
        }

        public String getScopeStatus() {
            return scopeStatus;
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

    final class InboundRow {
        private final String partnerSku;
        private final String destinationCode;
        private final boolean scopeResolved;
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
            this(
                    partnerSku,
                    null,
                    true,
                    batchId,
                    batchReferenceNo,
                    transportMode,
                    batchStatus,
                    etaDate,
                    remainingQuantity
            );
        }

        public InboundRow(
                String partnerSku,
                String destinationCode,
                boolean scopeResolved,
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity
        ) {
            this.partnerSku = partnerSku;
            this.destinationCode = destinationCode;
            this.scopeResolved = scopeResolved;
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

        public String getDestinationCode() {
            return destinationCode;
        }

        public boolean isScopeResolved() {
            return scopeResolved;
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
