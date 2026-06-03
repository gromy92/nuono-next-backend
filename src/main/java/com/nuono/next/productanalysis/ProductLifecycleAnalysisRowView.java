package com.nuono.next.productanalysis;

import java.time.LocalDate;
import java.util.List;

public class ProductLifecycleAnalysisRowView {

    private final String partnerSku;
    private final String sku;
    private final String productTitle;
    private final String imageUrl;
    private final String brand;
    private final String productFulltype;
    private final String lifecycleCode;
    private final String lifecycleLabel;
    private final String analysisState;
    private final String analysisStateLabel;
    private final LocalDate analysisDate;
    private final LocalDate listingDate;
    private final LocalDate currentStageStartDate;
    private final String listingDateSource;
    private final String ruleVersion;
    private final Integer currentStock;
    private final Integer recent30DaySales;
    private final LocalDate latestFactDate;
    private final String projectionState;
    private final String projectionMessage;
    private final List<String> projectionMissingRequirements;
    private final Integer currentStageElapsedDays;
    private final Integer currentStageRemainingDays;
    private final String nextLifecycleCode;
    private final String nextLifecycleLabel;
    private final LocalDate nextTransitionDate;
    private final List<ProductLifecycleTimelinePointView> futureTimeline;

    public ProductLifecycleAnalysisRowView(
            String partnerSku,
            String sku,
            String productTitle,
            String imageUrl,
            String brand,
            String productFulltype,
            String lifecycleCode,
            String lifecycleLabel,
            String analysisState,
            String analysisStateLabel,
            LocalDate analysisDate,
            LocalDate listingDate,
            String listingDateSource,
            String ruleVersion,
            Integer currentStock,
            Integer recent30DaySales,
            LocalDate latestFactDate
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                imageUrl,
                brand,
                productFulltype,
                lifecycleCode,
                lifecycleLabel,
                analysisState,
                analysisStateLabel,
                analysisDate,
                listingDate,
                null,
                listingDateSource,
                ruleVersion,
                currentStock,
                recent30DaySales,
                latestFactDate
        );
    }

    public ProductLifecycleAnalysisRowView(
            String partnerSku,
            String sku,
            String productTitle,
            String imageUrl,
            String brand,
            String productFulltype,
            String lifecycleCode,
            String lifecycleLabel,
            String analysisState,
            String analysisStateLabel,
            LocalDate analysisDate,
            LocalDate listingDate,
            LocalDate currentStageStartDate,
            String listingDateSource,
            String ruleVersion,
            Integer currentStock,
            Integer recent30DaySales,
            LocalDate latestFactDate
    ) {
        this(
                partnerSku,
                sku,
                productTitle,
                imageUrl,
                brand,
                productFulltype,
                lifecycleCode,
                lifecycleLabel,
                analysisState,
                analysisStateLabel,
                analysisDate,
                listingDate,
                currentStageStartDate,
                listingDateSource,
                ruleVersion,
                currentStock,
                recent30DaySales,
                latestFactDate,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private ProductLifecycleAnalysisRowView(
            String partnerSku,
            String sku,
            String productTitle,
            String imageUrl,
            String brand,
            String productFulltype,
            String lifecycleCode,
            String lifecycleLabel,
            String analysisState,
            String analysisStateLabel,
            LocalDate analysisDate,
            LocalDate listingDate,
            LocalDate currentStageStartDate,
            String listingDateSource,
            String ruleVersion,
            Integer currentStock,
            Integer recent30DaySales,
            LocalDate latestFactDate,
            String projectionState,
            String projectionMessage,
            List<String> projectionMissingRequirements,
            Integer currentStageElapsedDays,
            Integer currentStageRemainingDays,
            String nextLifecycleCode,
            String nextLifecycleLabel,
            LocalDate nextTransitionDate,
            List<ProductLifecycleTimelinePointView> futureTimeline
    ) {
        this.partnerSku = partnerSku;
        this.sku = sku;
        this.productTitle = productTitle;
        this.imageUrl = imageUrl;
        this.brand = brand;
        this.productFulltype = productFulltype;
        this.lifecycleCode = lifecycleCode;
        this.lifecycleLabel = lifecycleLabel;
        this.analysisState = analysisState;
        this.analysisStateLabel = analysisStateLabel;
        this.analysisDate = analysisDate;
        this.listingDate = listingDate;
        this.currentStageStartDate = currentStageStartDate;
        this.listingDateSource = listingDateSource;
        this.ruleVersion = ruleVersion;
        this.currentStock = currentStock;
        this.recent30DaySales = recent30DaySales;
        this.latestFactDate = latestFactDate;
        this.projectionState = projectionState;
        this.projectionMessage = projectionMessage;
        this.projectionMissingRequirements = projectionMissingRequirements == null
                ? List.of()
                : List.copyOf(projectionMissingRequirements);
        this.currentStageElapsedDays = currentStageElapsedDays;
        this.currentStageRemainingDays = currentStageRemainingDays;
        this.nextLifecycleCode = nextLifecycleCode;
        this.nextLifecycleLabel = nextLifecycleLabel;
        this.nextTransitionDate = nextTransitionDate;
        this.futureTimeline = futureTimeline == null ? List.of() : List.copyOf(futureTimeline);
    }

    public ProductLifecycleAnalysisRowView withProjection(ProductLifecycleTimelineProjection projection) {
        return new ProductLifecycleAnalysisRowView(
                partnerSku,
                sku,
                productTitle,
                imageUrl,
                brand,
                productFulltype,
                lifecycleCode,
                lifecycleLabel,
                analysisState,
                analysisStateLabel,
                analysisDate,
                listingDate,
                currentStageStartDate,
                listingDateSource,
                ruleVersion,
                currentStock,
                recent30DaySales,
                latestFactDate,
                projection == null ? null : projection.getQualityState(),
                projection == null ? null : projection.getQualityMessage(),
                projection == null ? List.of() : projection.getMissingRequirements(),
                projection == null ? null : projection.getCurrentStageElapsedDays(),
                projection == null ? null : projection.getCurrentStageRemainingDays(),
                projection == null ? null : projection.getNextLifecycleCode(),
                projection == null ? null : projection.getNextLifecycleLabel(),
                projection == null ? null : projection.getNextTransitionDate(),
                projection == null ? List.of() : projection.getFutureTimeline()
        );
    }

    public ProductLifecycleAnalysisRowView withMissingListingDateState() {
        return new ProductLifecycleAnalysisRowView(
                partnerSku,
                sku,
                productTitle,
                imageUrl,
                brand,
                productFulltype,
                "data_insufficient",
                "数据不足",
                "data_insufficient",
                "数据不足",
                analysisDate,
                null,
                null,
                "missing",
                ruleVersion,
                currentStock,
                recent30DaySales,
                latestFactDate
        );
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public String getSku() {
        return sku;
    }

    public String getProductTitle() {
        return productTitle;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getBrand() {
        return brand;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public String getLifecycleCode() {
        return lifecycleCode;
    }

    public String getLifecycleLabel() {
        return lifecycleLabel;
    }

    public String getAnalysisState() {
        return analysisState;
    }

    public String getAnalysisStateLabel() {
        return analysisStateLabel;
    }

    public LocalDate getAnalysisDate() {
        return analysisDate;
    }

    public LocalDate getListingDate() {
        return listingDate;
    }

    public LocalDate getCurrentStageStartDate() {
        return currentStageStartDate;
    }

    public String getListingDateSource() {
        return listingDateSource;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public Integer getRecent30DaySales() {
        return recent30DaySales;
    }

    public LocalDate getLatestFactDate() {
        return latestFactDate;
    }

    public String getProjectionState() {
        return projectionState;
    }

    public String getProjectionMessage() {
        return projectionMessage;
    }

    public List<String> getProjectionMissingRequirements() {
        return projectionMissingRequirements;
    }

    public Integer getCurrentStageElapsedDays() {
        return currentStageElapsedDays;
    }

    public Integer getCurrentStageRemainingDays() {
        return currentStageRemainingDays;
    }

    public String getNextLifecycleCode() {
        return nextLifecycleCode;
    }

    public String getNextLifecycleLabel() {
        return nextLifecycleLabel;
    }

    public LocalDate getNextTransitionDate() {
        return nextTransitionDate;
    }

    public List<ProductLifecycleTimelinePointView> getFutureTimeline() {
        return futureTimeline;
    }
}
