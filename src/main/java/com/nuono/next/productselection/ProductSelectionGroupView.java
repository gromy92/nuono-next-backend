package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.List;

public class ProductSelectionGroupView {

    private String groupId;
    private String groupNo;
    private String groupName;
    private String siteCode;
    private String status;
    private Integer materialCount;
    private List<ProductSelectionGroupMaterialView> materials = new ArrayList<>();
    private ProductSelectionGroupProcurementView procurement;
    private List<ProductSelectionGroupCompetitorView> competitors = new ArrayList<>();

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupNo() {
        return groupNo;
    }

    public void setGroupNo(String groupNo) {
        this.groupNo = groupNo;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getMaterialCount() {
        return materialCount;
    }

    public void setMaterialCount(Integer materialCount) {
        this.materialCount = materialCount;
    }

    public List<ProductSelectionGroupMaterialView> getMaterials() {
        return materials;
    }

    public void setMaterials(List<ProductSelectionGroupMaterialView> materials) {
        this.materials = materials == null ? new ArrayList<>() : materials;
    }

    public ProductSelectionGroupProcurementView getProcurement() {
        return procurement;
    }

    public void setProcurement(ProductSelectionGroupProcurementView procurement) {
        this.procurement = procurement;
    }

    public List<ProductSelectionGroupCompetitorView> getCompetitors() {
        return competitors;
    }

    public void setCompetitors(List<ProductSelectionGroupCompetitorView> competitors) {
        this.competitors = competitors == null ? new ArrayList<>() : competitors;
    }
}
