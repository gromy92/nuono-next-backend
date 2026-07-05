package com.nuono.next.productselection;

public class ProductSelectionGroupMaterialView {

    private String materialId;
    private String groupId;
    private String sourceCollectionId;
    private String status;
    private ProductSelectionSourceCollectionView sourceCollection;

    public String getMaterialId() {
        return materialId;
    }

    public void setMaterialId(String materialId) {
        this.materialId = materialId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getSourceCollectionId() {
        return sourceCollectionId;
    }

    public void setSourceCollectionId(String sourceCollectionId) {
        this.sourceCollectionId = sourceCollectionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ProductSelectionSourceCollectionView getSourceCollection() {
        return sourceCollection;
    }

    public void setSourceCollection(ProductSelectionSourceCollectionView sourceCollection) {
        this.sourceCollection = sourceCollection;
    }
}
