package com.nuono.next.replenishmentplan;

import com.nuono.next.product.ProductImageUrlSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ReplenishmentProductStockRow {
    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final String imageUrl;
    private final LocalDate listingAt;
    private final Boolean isActive;
    private final String activeStateSource;
    private final LocalDateTime activeStateSyncedAt;
    private final BigDecimal currentStockUnits;
    private final BigDecimal fbnStockUnits;
    private final BigDecimal supermallStockUnits;

    public ReplenishmentProductStockRow(
            String partnerSku,
            String sku,
            BigDecimal currentStockUnits,
            BigDecimal fbnStockUnits,
            BigDecimal supermallStockUnits
    ) {
        this(partnerSku, sku, null, null, null, null, null, null,
                currentStockUnits, fbnStockUnits, supermallStockUnits);
    }

    public ReplenishmentProductStockRow(
            String partnerSku,
            String sku,
            String imageUrl,
            BigDecimal currentStockUnits,
            BigDecimal fbnStockUnits,
            BigDecimal supermallStockUnits
    ) {
        this(partnerSku, sku, null, imageUrl, null, null, null, null,
                currentStockUnits, fbnStockUnits, supermallStockUnits);
    }

    public ReplenishmentProductStockRow(
            String partnerSku,
            String sku,
            String imageUrl,
            LocalDate listingAt,
            BigDecimal currentStockUnits,
            BigDecimal fbnStockUnits,
            BigDecimal supermallStockUnits
    ) {
        this(partnerSku, sku, null, imageUrl, listingAt, null, null, null,
                currentStockUnits, fbnStockUnits, supermallStockUnits);
    }

    public ReplenishmentProductStockRow(
            String partnerSku,
            String sku,
            String productTitle,
            String imageUrl,
            LocalDate listingAt,
            Boolean isActive,
            String activeStateSource,
            LocalDateTime activeStateSyncedAt,
            BigDecimal currentStockUnits,
            BigDecimal fbnStockUnits,
            BigDecimal supermallStockUnits
    ) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.imageUrl = ProductImageUrlSupport.normalize(imageUrl);
        this.listingAt = listingAt;
        this.isActive = isActive;
        this.activeStateSource = activeStateSource;
        this.activeStateSyncedAt = activeStateSyncedAt;
        this.currentStockUnits = currentStockUnits;
        this.fbnStockUnits = fbnStockUnits;
        this.supermallStockUnits = supermallStockUnits;
    }

    public String getPartnerSku() { return partnerSku; }
    public String getSku() { return sku; }
    public String getProductTitle() { return productTitle; }
    public String getImageUrl() { return imageUrl; }
    public LocalDate getListingAt() { return listingAt; }
    public Boolean getIsActive() { return isActive; }
    public String getActiveStateSource() { return activeStateSource; }
    public LocalDateTime getActiveStateSyncedAt() { return activeStateSyncedAt; }
    public BigDecimal getCurrentStockUnits() { return currentStockUnits; }
    public BigDecimal getFbnStockUnits() { return fbnStockUnits; }
    public BigDecimal getSupermallStockUnits() { return supermallStockUnits; }
}
