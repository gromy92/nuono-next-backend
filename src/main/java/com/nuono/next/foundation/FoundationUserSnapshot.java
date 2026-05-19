package com.nuono.next.foundation;

public class FoundationUserSnapshot {

    private Long id;

    private String accountNo;

    private String realName;

    private String phone;

    private String accountType;

    private String companyName;

    private Integer status;

    private String roleName;

    private Integer storeCount;

    private Integer authorizedStoreCount;

    private Integer managedStoreCount;

    private Integer managedCompanyCount;

    private Integer descendantUserCount;

    private String sites;

    private String bindingStatus;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNo() {
        return accountNo;
    }

    public void setAccountNo(String accountNo) {
        this.accountNo = accountNo;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Integer getStoreCount() {
        return storeCount;
    }

    public void setStoreCount(Integer storeCount) {
        this.storeCount = storeCount;
    }

    public Integer getAuthorizedStoreCount() {
        return authorizedStoreCount;
    }

    public void setAuthorizedStoreCount(Integer authorizedStoreCount) {
        this.authorizedStoreCount = authorizedStoreCount;
    }

    public Integer getManagedStoreCount() {
        return managedStoreCount;
    }

    public void setManagedStoreCount(Integer managedStoreCount) {
        this.managedStoreCount = managedStoreCount;
    }

    public Integer getManagedCompanyCount() {
        return managedCompanyCount;
    }

    public void setManagedCompanyCount(Integer managedCompanyCount) {
        this.managedCompanyCount = managedCompanyCount;
    }

    public Integer getDescendantUserCount() {
        return descendantUserCount;
    }

    public void setDescendantUserCount(Integer descendantUserCount) {
        this.descendantUserCount = descendantUserCount;
    }

    public String getSites() {
        return sites;
    }

    public void setSites(String sites) {
        this.sites = sites;
    }

    public String getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(String bindingStatus) {
        this.bindingStatus = bindingStatus;
    }
}
