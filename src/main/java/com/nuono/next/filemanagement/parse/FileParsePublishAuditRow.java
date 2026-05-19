package com.nuono.next.filemanagement.parse;

public class FileParsePublishAuditRow {

    private Long versionId;
    private String payloadHash;

    public Long getVersionId() {
        return versionId;
    }

    public void setVersionId(Long versionId) {
        this.versionId = versionId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }
}
