package com.nuono.next.product;

import java.time.LocalDateTime;

public class ProductMasterDraftRecord {

    private Long id;
    private Long productMasterId;
    private Long baselineSnapshotId;
    private Integer versionNo;
    private String dirtySiteCodesJson;
    private String draftJson;
    private LocalDateTime savedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProductMasterId() {
        return productMasterId;
    }

    public void setProductMasterId(Long productMasterId) {
        this.productMasterId = productMasterId;
    }

    public Long getBaselineSnapshotId() {
        return baselineSnapshotId;
    }

    public void setBaselineSnapshotId(Long baselineSnapshotId) {
        this.baselineSnapshotId = baselineSnapshotId;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getDirtySiteCodesJson() {
        return dirtySiteCodesJson;
    }

    public void setDirtySiteCodesJson(String dirtySiteCodesJson) {
        this.dirtySiteCodesJson = dirtySiteCodesJson;
    }

    public String getDraftJson() {
        return draftJson;
    }

    public void setDraftJson(String draftJson) {
        this.draftJson = draftJson;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }
}
