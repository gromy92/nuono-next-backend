package com.nuono.next.productselection;

public class ProductSelectionGroupMaterialRow extends ProductSelectionSourceCollectionRow {

    private Long materialId;
    private Long groupId;
    private Long sourceCollectionId;
    private String materialStatus;

    public Long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(Long materialId) {
        this.materialId = materialId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public Long getSourceCollectionId() {
        return sourceCollectionId;
    }

    public void setSourceCollectionId(Long sourceCollectionId) {
        this.sourceCollectionId = sourceCollectionId;
    }

    public String getMaterialStatus() {
        return materialStatus;
    }

    public void setMaterialStatus(String materialStatus) {
        this.materialStatus = materialStatus;
    }

    public void copySourceCollectionFieldsFrom(ProductSelectionSourceCollectionRow source) {
        if (source == null) {
            return;
        }
        setId(source.getId());
        setOwnerUserId(source.getOwnerUserId());
        setLogicalStoreId(source.getLogicalStoreId());
        setSiteCode(source.getSiteCode());
        setCollectionNo(source.getCollectionNo());
        setSourceType(source.getSourceType());
        setCollectionSource(source.getCollectionSource());
        setSourcePlatform(source.getSourcePlatform());
        setSourceUrl(source.getSourceUrl());
        setPageUrl(source.getPageUrl());
        setSourceTitle(source.getSourceTitle());
        setSourceTitleCn(source.getSourceTitleCn());
        setSourceTitleAr(source.getSourceTitleAr());
        setSourceImageUrl(source.getSourceImageUrl());
        setImageUrlsJson(source.getImageUrlsJson());
        setPriceSummary(source.getPriceSummary());
        setMoqHint(source.getMoqHint());
        setShippingFrom(source.getShippingFrom());
        setBrandName(source.getBrandName());
        setUnitCount(source.getUnitCount());
        setColorName(source.getColorName());
        setSpecHintsJson(source.getSpecHintsJson());
        setSpecAttributeCount(source.getSpecAttributeCount());
        setSourceDescriptionEn(source.getSourceDescriptionEn());
        setSourceDescriptionAr(source.getSourceDescriptionAr());
        setSourceSellingPointsEnJson(source.getSourceSellingPointsEnJson());
        setSourceSellingPointsArJson(source.getSourceSellingPointsArJson());
        setSelectedText(source.getSelectedText());
        setSelectedTextAr(source.getSelectedTextAr());
        setNotes(source.getNotes());
        setStatus(source.getStatus());
        setFailureCode(source.getFailureCode());
        setFailureMessage(source.getFailureMessage());
        setCollectedAt(source.getCollectedAt());
        setCreatedBy(source.getCreatedBy());
        setUpdatedBy(source.getUpdatedBy());
        setCreatedByName(source.getCreatedByName());
        setStoreName(source.getStoreName());
        setStoreCode(source.getStoreCode());
    }
}
