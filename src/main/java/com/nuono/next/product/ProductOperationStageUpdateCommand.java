package com.nuono.next.product;

public class ProductOperationStageUpdateCommand extends ProductMasterFetchCommand {

    private String operationStageCode;

    private Long operatorUserId;

    public String getOperationStageCode() {
        return operationStageCode;
    }

    public void setOperationStageCode(String operationStageCode) {
        this.operationStageCode = operationStageCode;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
