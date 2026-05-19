package com.nuono.next.productcompletion;

public class ProductInfoCompletionCommand {

    private Long operatorUserId;
    private String sourceUrl;
    private String title;
    private String supplierName;
    private String detailText;
    private String attributeSnapshotText;
    private String packageSnapshotText;
    private String shippingSnapshotText;
    private String rawHtml;
    private String imageOcrText;
    private Boolean useAi;

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
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

    public String getDetailText() {
        return detailText;
    }

    public void setDetailText(String detailText) {
        this.detailText = detailText;
    }

    public String getAttributeSnapshotText() {
        return attributeSnapshotText;
    }

    public void setAttributeSnapshotText(String attributeSnapshotText) {
        this.attributeSnapshotText = attributeSnapshotText;
    }

    public String getPackageSnapshotText() {
        return packageSnapshotText;
    }

    public void setPackageSnapshotText(String packageSnapshotText) {
        this.packageSnapshotText = packageSnapshotText;
    }

    public String getShippingSnapshotText() {
        return shippingSnapshotText;
    }

    public void setShippingSnapshotText(String shippingSnapshotText) {
        this.shippingSnapshotText = shippingSnapshotText;
    }

    public String getRawHtml() {
        return rawHtml;
    }

    public void setRawHtml(String rawHtml) {
        this.rawHtml = rawHtml;
    }

    public String getImageOcrText() {
        return imageOcrText;
    }

    public void setImageOcrText(String imageOcrText) {
        this.imageOcrText = imageOcrText;
    }

    public Boolean getUseAi() {
        return useAi;
    }

    public void setUseAi(Boolean useAi) {
        this.useAi = useAi;
    }
}
