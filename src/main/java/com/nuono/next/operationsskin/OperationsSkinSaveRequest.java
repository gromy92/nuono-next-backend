package com.nuono.next.operationsskin;

import java.util.ArrayList;
import java.util.List;

public class OperationsSkinSaveRequest {
    private String storeCode;
    private String skinName;
    private String status;
    private String coverImageUrl;
    private String styleDescription;
    private String remark;
    private Integer sortOrder;
    private List<OperationsSkinAssetView> assets = new ArrayList<>();

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

    public List<OperationsSkinAssetView> getAssets() {
        return assets;
    }

    public void setAssets(List<OperationsSkinAssetView> assets) {
        this.assets = assets == null ? new ArrayList<>() : assets;
    }
}
