package com.nuono.next.product;

public class ProductImageSuiteAssetMoveCommand {
    private Long targetSuiteId;
    private Integer targetIndex;

    public Long getTargetSuiteId() {
        return targetSuiteId;
    }

    public void setTargetSuiteId(Long targetSuiteId) {
        this.targetSuiteId = targetSuiteId;
    }

    public Integer getTargetIndex() {
        return targetIndex;
    }

    public void setTargetIndex(Integer targetIndex) {
        this.targetIndex = targetIndex;
    }
}
