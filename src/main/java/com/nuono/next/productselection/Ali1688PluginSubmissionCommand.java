package com.nuono.next.productselection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public class Ali1688PluginSubmissionCommand {

    private String idempotencyKey;
    private String sourcePageUrl;
    private Long ownerUserId;
    private Long logicalStoreId;
    private Long operatorUserId;
    private Long roleId;
    private Integer level;
    private String storeCode;
    private List<Candidate> candidates = new ArrayList<>();
    private Map<String, Object> rawSnapshot;

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourcePageUrl() {
        return sourcePageUrl;
    }

    public void setSourcePageUrl(String sourcePageUrl) {
        this.sourcePageUrl = sourcePageUrl;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public List<Candidate> getCandidates() {
        if (candidates == null) {
            candidates = new ArrayList<>();
        }
        return candidates;
    }

    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates == null ? new ArrayList<>() : candidates;
    }

    public Map<String, Object> getRawSnapshot() {
        return rawSnapshot;
    }

    public void setRawSnapshot(Map<String, Object> rawSnapshot) {
        this.rawSnapshot = rawSnapshot;
    }

    public boolean hasIdentityFields() {
        return ownerUserId != null
                || logicalStoreId != null
                || operatorUserId != null
                || roleId != null
                || level != null
                || StringUtils.hasText(storeCode);
    }

    public static class Candidate {
        private String offerId;
        private String candidateUrl;
        private String title;
        private String supplierName;
        private String priceText;
        private BigDecimal priceMin;
        private BigDecimal priceMax;
        private String moqText;
        private Integer moqValue;
        private String locationText;
        private String mainImageUrl;
        private List<String> imageUrls = new ArrayList<>();
        private Map<String, Object> badges;
        private Map<String, Object> skuSnapshot;
        private Map<String, Object> supplierSnapshot;
        private Map<String, Object> logisticsSnapshot;

        public String getOfferId() {
            return offerId;
        }

        public void setOfferId(String offerId) {
            this.offerId = offerId;
        }

        public String getCandidateUrl() {
            return candidateUrl;
        }

        public void setCandidateUrl(String candidateUrl) {
            this.candidateUrl = candidateUrl;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getSupplierName() {
            return supplierName;
        }

        public void setSupplierName(String supplierName) {
            this.supplierName = supplierName;
        }

        public String getPriceText() {
            return priceText;
        }

        public void setPriceText(String priceText) {
            this.priceText = priceText;
        }

        public BigDecimal getPriceMin() {
            return priceMin;
        }

        public void setPriceMin(BigDecimal priceMin) {
            this.priceMin = priceMin;
        }

        public BigDecimal getPriceMax() {
            return priceMax;
        }

        public void setPriceMax(BigDecimal priceMax) {
            this.priceMax = priceMax;
        }

        public String getMoqText() {
            return moqText;
        }

        public void setMoqText(String moqText) {
            this.moqText = moqText;
        }

        public Integer getMoqValue() {
            return moqValue;
        }

        public void setMoqValue(Integer moqValue) {
            this.moqValue = moqValue;
        }

        public String getLocationText() {
            return locationText;
        }

        public void setLocationText(String locationText) {
            this.locationText = locationText;
        }

        public String getMainImageUrl() {
            return mainImageUrl;
        }

        public void setMainImageUrl(String mainImageUrl) {
            this.mainImageUrl = mainImageUrl;
        }

        public List<String> getImageUrls() {
            if (imageUrls == null) {
                imageUrls = new ArrayList<>();
            }
            return imageUrls;
        }

        public void setImageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls == null ? new ArrayList<>() : imageUrls;
        }

        public Map<String, Object> getBadges() {
            return badges;
        }

        public void setBadges(Map<String, Object> badges) {
            this.badges = badges;
        }

        public Map<String, Object> getSkuSnapshot() {
            return skuSnapshot;
        }

        public void setSkuSnapshot(Map<String, Object> skuSnapshot) {
            this.skuSnapshot = skuSnapshot;
        }

        public Map<String, Object> getSupplierSnapshot() {
            return supplierSnapshot;
        }

        public void setSupplierSnapshot(Map<String, Object> supplierSnapshot) {
            this.supplierSnapshot = supplierSnapshot;
        }

        public Map<String, Object> getLogisticsSnapshot() {
            return logisticsSnapshot;
        }

        public void setLogisticsSnapshot(Map<String, Object> logisticsSnapshot) {
            this.logisticsSnapshot = logisticsSnapshot;
        }
    }
}
