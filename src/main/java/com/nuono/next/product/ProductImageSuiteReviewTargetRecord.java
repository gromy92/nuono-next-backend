package com.nuono.next.product;

public class ProductImageSuiteReviewTargetRecord {
    private Long id;
    private Long suiteId;
    private String targetScope;
    private Long assetId;
    private ProductImageSuiteAssetRole imageRole;
    private Integer roleOrdinal;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSuiteId() { return suiteId; }
    public void setSuiteId(Long suiteId) { this.suiteId = suiteId; }
    public String getTargetScope() { return targetScope; }
    public void setTargetScope(String targetScope) { this.targetScope = targetScope; }
    public Long getAssetId() { return assetId; }
    public void setAssetId(Long assetId) { this.assetId = assetId; }
    public ProductImageSuiteAssetRole getImageRole() { return imageRole; }
    public void setImageRole(ProductImageSuiteAssetRole imageRole) { this.imageRole = imageRole; }
    public Integer getRoleOrdinal() { return roleOrdinal; }
    public void setRoleOrdinal(Integer roleOrdinal) { this.roleOrdinal = roleOrdinal; }
}
