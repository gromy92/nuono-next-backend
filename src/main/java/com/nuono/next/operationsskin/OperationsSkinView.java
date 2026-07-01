package com.nuono.next.operationsskin;

import java.util.ArrayList;
import java.util.List;

public class OperationsSkinView {
    private Long id;
    private String storeCode;
    private String skinName;
    private String status;
    private String coverImageUrl;
    private String styleDescription;
    private String remark;
    private Integer sortOrder;
    private String updatedAt;
    private Integer assetCount;
    private Integer heroComponentCount;
    private Integer heroComponentRequiredCount;
    private List<OperationsSkinAssetView> assets = new ArrayList<>();
    private List<OperationsSkinComponentView> components = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSkinName() {
        return skinName;
    }

    public void setSkinName(String skinName) {
        this.skinName = skinName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getStyleDescription() {
        return styleDescription;
    }

    public void setStyleDescription(String styleDescription) {
        this.styleDescription = styleDescription;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getAssetCount() {
        return assetCount;
    }

    public void setAssetCount(Integer assetCount) {
        this.assetCount = assetCount;
    }

    public Integer getHeroComponentCount() {
        return heroComponentCount;
    }

    public void setHeroComponentCount(Integer heroComponentCount) {
        this.heroComponentCount = heroComponentCount;
    }

    public Integer getHeroComponentRequiredCount() {
        return heroComponentRequiredCount;
    }

    public void setHeroComponentRequiredCount(Integer heroComponentRequiredCount) {
        this.heroComponentRequiredCount = heroComponentRequiredCount;
    }

    public List<OperationsSkinAssetView> getAssets() {
        return assets;
    }

    public void setAssets(List<OperationsSkinAssetView> assets) {
        this.assets = assets == null ? new ArrayList<>() : assets;
    }

    public List<OperationsSkinComponentView> getComponents() {
        return components;
    }

    public void setComponents(List<OperationsSkinComponentView> components) {
        this.components = components == null ? new ArrayList<>() : components;
    }
}
