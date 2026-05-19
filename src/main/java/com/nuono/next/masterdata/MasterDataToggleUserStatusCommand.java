package com.nuono.next.masterdata;

public class MasterDataToggleUserStatusCommand {

    private Integer status;

    private Long operatorUserId;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }
}
