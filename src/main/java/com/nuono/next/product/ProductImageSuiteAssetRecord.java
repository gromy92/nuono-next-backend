package com.nuono.next.product;

public class ProductImageSuiteAssetRecord {
    private Long id;
    private Long suiteId;
    private ProductImageSuiteAssetRole imageRole;
    private Integer roleOrdinal;
    private String imageUrl;
    private String contentType;
    private Long sizeBytes;
    private String sha256;
    private Integer sortOrder;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSuiteId() {
        return suiteId;
    }

    public void setSuiteId(Long suiteId) {
        this.suiteId = suiteId;
    }

    public ProductImageSuiteAssetRole getImageRole() {
        return imageRole;
    }

    public void setImageRole(ProductImageSuiteAssetRole imageRole) {
        this.imageRole = imageRole;
    }

    public Integer getRoleOrdinal() { return roleOrdinal; }
    public void setRoleOrdinal(Integer roleOrdinal) { this.roleOrdinal = roleOrdinal; }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
