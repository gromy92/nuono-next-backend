package com.nuono.next.filemanagement.parse;

public class FileParseUploadView {

    private Long fileId;
    private String uploadId;
    private Long targetPlanId;
    private Long standardVersionId;
    private String originalFileName;
    private String contentType;
    private String fileExtension;
    private Long sizeBytes;
    private String sha256Hash;
    private String downloadUrl;

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public Long getTargetPlanId() {
        return targetPlanId;
    }

    public void setTargetPlanId(Long targetPlanId) {
        this.targetPlanId = targetPlanId;
    }

    public Long getStandardVersionId() {
        return standardVersionId;
    }

    public void setStandardVersionId(Long standardVersionId) {
        this.standardVersionId = standardVersionId;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
