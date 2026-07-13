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
        private final String psku;
        private final String sku;
        private final String msku;
        private final Long batchId;
        private final String batchReferenceNo;
        private final String transportMode;
        private final String batchStatus;
        private final LocalDate etaDate;
        private final BigDecimal remainingQuantity;

        public InboundLineRow(
                Long lineId,
                String psku,
                String sku,
                String msku,
                Long batchId,
                String batchReferenceNo,
                String transportMode,
                String batchStatus,
                LocalDate etaDate,
                BigDecimal remainingQuantity
        ) {
            this.lineId = lineId;
            this.psku = psku;
            this.sku = sku;
            this.msku = msku;
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

        public String getPsku() {
            return psku;
        }

        public String getSku() {
            return sku;
        }

        public String getMsku() {
            return msku;
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

    final class ProductIdentityRow {
        private final String partnerSku;
        private final String psoPartnerSku;
        private final String psoPskuCode;
        private final String psoOfferCode;
        private final String childSku;
        private final String skuParent;
        private final String barcode;

        public ProductIdentityRow(
                String partnerSku,
                String psoPartnerSku,
                String psoPskuCode,
                String psoOfferCode,
                String childSku,
                String skuParent,
                String barcode
        ) {
            this.partnerSku = partnerSku;
            this.psoPartnerSku = psoPartnerSku;
            this.psoPskuCode = psoPskuCode;
            this.psoOfferCode = psoOfferCode;
            this.childSku = childSku;
            this.skuParent = skuParent;
            this.barcode = barcode;
        }

        public String getPartnerSku() {
            return partnerSku;
        }

        public String getPsoPartnerSku() {
            return psoPartnerSku;
        }

        public String getPsoPskuCode() {
            return psoPskuCode;
        }

        public String getPsoOfferCode() {
            return psoOfferCode;
        }

        public String getChildSku() {
            return childSku;
        }

        public String getSkuParent() {
            return skuParent;
        }

        public String getBarcode() {
            return barcode;
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
