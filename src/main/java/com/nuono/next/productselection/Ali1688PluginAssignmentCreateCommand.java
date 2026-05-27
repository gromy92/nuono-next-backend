package com.nuono.next.productselection;

public class Ali1688PluginAssignmentCreateCommand {

    private Long ownerUserId;
    private Long logicalStoreId;
    private Long operatorUserId;
    private Long roleId;
    private Integer level;
    private String storeCode;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public Long getLogicalStoreId() {
        return logicalStoreId;
    }

    public void setLogicalStoreId(Long logicalStoreId) {
        this.logicalStoreId = logicalStoreId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public boolean hasIdentityFields() {
        return ownerUserId != null
                || logicalStoreId != null
                || operatorUserId != null
                || roleId != null
                || level != null
                || (storeCode != null && !storeCode.trim().isEmpty());
    }
}
