package com.nuono.next.product;

public class ProductGroupProjectionRecord {

    private Long productGroupId;
    private Long productMasterId;
    private String skuGroup;
    private String groupRef;
    private String groupRefCanonical;
    private String groupName;
    private String brand;
    private String productFulltype;
    private String axesJson;
    private String conditionsJson;
    private Integer memberCount;

    public Long getProductGroupId() {
        return productGroupId;
    }

    public void setProductGroupId(Long productGroupId) {
        this.productGroupId = productGroupId;
    }

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
    }

    public String getSkuGroup() {
        return skuGroup;
    }

    public void setSkuGroup(String skuGroup) {
        this.skuGroup = skuGroup;
    }

    public String getGroupRef() {
        return groupRef;
    }

    public void setGroupRef(String groupRef) {
        this.groupRef = groupRef;
    }

    public String getGroupRefCanonical() {
        return groupRefCanonical;
    }

    public void setGroupRefCanonical(String groupRefCanonical) {
        this.groupRefCanonical = groupRefCanonical;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public String getProductFulltype() {
        return productFulltype;
    }

    public void setProductFulltype(String productFulltype) {
        this.productFulltype = productFulltype;
    }

    public String getAxesJson() {
        return axesJson;
    }

    public void setAxesJson(String axesJson) {
        this.axesJson = axesJson;
    }

    public String getConditionsJson() {
        return conditionsJson;
    }

    public void setConditionsJson(String conditionsJson) {
        this.conditionsJson = conditionsJson;
    }

    public Integer getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(Integer memberCount) {
        this.memberCount = memberCount;
    }
}
