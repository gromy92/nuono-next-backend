package com.nuono.next.procurement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProcurementLogisticsRequirementView {

    private String mode;
    private boolean ready;
    private String message;
    private boolean recommendationReady;
    private List<String> recommendationBlockers = new ArrayList<>();
    private RequirementView requirement;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRecommendationReady() {
        return recommendationReady;
    }

    public void setRecommendationReady(boolean recommendationReady) {
        this.recommendationReady = recommendationReady;
    }

    public List<String> getRecommendationBlockers() {
        return recommendationBlockers;
    }

    public void setRecommendationBlockers(List<String> recommendationBlockers) {
        this.recommendationBlockers = recommendationBlockers;
    }

    public RequirementView getRequirement() {
        return requirement;
    }

    public void setRequirement(RequirementView requirement) {
        this.requirement = requirement;
    }

    public static class RequirementView {

        private Long id;
        private Long ownerUserId;
        private Long demandItemId;
        private String transportMode;
        private String destinationCountry;
        private String destinationNode;
        private String originNode;
        private BigDecimal packageLengthCm;
        private BigDecimal packageWidthCm;
        private BigDecimal packageHeightCm;
        private BigDecimal unitWeightGrams;
        private Integer quantity;
        private String cargoAttributes;
        private String status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(Long ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public Long getDemandItemId() {
            return demandItemId;
        }

        public void setDemandItemId(Long demandItemId) {
            this.demandItemId = demandItemId;
        }

        public String getTransportMode() {
            return transportMode;
        }

        public void setTransportMode(String transportMode) {
            this.transportMode = transportMode;
        }

        public String getDestinationCountry() {
            return destinationCountry;
        }

        public void setDestinationCountry(String destinationCountry) {
            this.destinationCountry = destinationCountry;
        }

        public String getDestinationNode() {
            return destinationNode;
        }

        public void setDestinationNode(String destinationNode) {
            this.destinationNode = destinationNode;
        }

        public String getOriginNode() {
            return originNode;
        }

        public void setOriginNode(String originNode) {
            this.originNode = originNode;
        }

        public BigDecimal getPackageLengthCm() {
            return packageLengthCm;
        }

        public void setPackageLengthCm(BigDecimal packageLengthCm) {
            this.packageLengthCm = packageLengthCm;
        }

        public BigDecimal getPackageWidthCm() {
            return packageWidthCm;
        }

        public void setPackageWidthCm(BigDecimal packageWidthCm) {
            this.packageWidthCm = packageWidthCm;
        }

        public BigDecimal getPackageHeightCm() {
            return packageHeightCm;
        }

        public void setPackageHeightCm(BigDecimal packageHeightCm) {
            this.packageHeightCm = packageHeightCm;
        }

        public BigDecimal getUnitWeightGrams() {
            return unitWeightGrams;
        }

        public void setUnitWeightGrams(BigDecimal unitWeightGrams) {
            this.unitWeightGrams = unitWeightGrams;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public String getCargoAttributes() {
            return cargoAttributes;
        }

        public void setCargoAttributes(String cargoAttributes) {
            this.cargoAttributes = cargoAttributes;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
