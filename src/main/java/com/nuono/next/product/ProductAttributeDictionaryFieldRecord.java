package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductAttributeDictionaryFieldRecord {

    private Long id;
    private String projectCode;
    private String storeCode;
    private String productFulltype;
    private String attributeCode;
    private String labelEn;
    private String labelAr;
    private String labelZh;
    private String groupName;
    private String inputKind;
    private Boolean required;
    private Boolean grouping;
    private Boolean visibleSeller;
    private String dictionarySource;
    private Integer sortOrder;
    private LocalDateTime fetchedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public void setProductFulltype(String productFulltype) {
        this.productFulltype = productFulltype;
    }

    public String getAttributeCode() {
        return attributeCode;
    }

    public void setAttributeCode(String attributeCode) {
        this.attributeCode = attributeCode;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public void setLabelEn(String labelEn) {
        this.labelEn = labelEn;
    }

    public String getLabelAr() {
        return labelAr;
    }

    public void setLabelAr(String labelAr) {
        this.labelAr = labelAr;
    }

    public String getLabelZh() {
        return labelZh;
    }

    public void setLabelZh(String labelZh) {
        this.labelZh = labelZh;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getInputKind() {
        return inputKind;
    }

    public void setInputKind(String inputKind) {
        this.inputKind = inputKind;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public Boolean getGrouping() {
        return grouping;
    }

    public void setGrouping(Boolean grouping) {
        this.grouping = grouping;
    }

    public Boolean getVisibleSeller() {
        return visibleSeller;
    }

    public void setVisibleSeller(Boolean visibleSeller) {
        this.visibleSeller = visibleSeller;
    }

    public String getDictionarySource() {
        return dictionarySource;
    }

    public void setDictionarySource(String dictionarySource) {
        this.dictionarySource = dictionarySource;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}
