package com.nuono.next.product;

public class ProductImageAssetUsageUpdateCommand {
    private ProductImageRole imageRole;
    private String processingNote;
    private ProductImageProcessingStatus processingStatus;

    public ProductImageRole getImageRole() { return imageRole; }
    public void setImageRole(ProductImageRole imageRole) { this.imageRole = imageRole; }
    public String getProcessingNote() { return processingNote; }
    public void setProcessingNote(String processingNote) { this.processingNote = processingNote; }
    public ProductImageProcessingStatus getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(ProductImageProcessingStatus processingStatus) { this.processingStatus = processingStatus; }
}
