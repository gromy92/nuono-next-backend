package com.nuono.next.auth;

import java.util.ArrayList;
import java.util.List;

public class AuthLoginResult {

    private Long userId;

    private String accountNo;

    private String realName;

    private Long roleId;

    private String roleName;

    private String companyName;

    private Integer status;

    private Integer level;

    private Integer storeCount;

    private Integer authorizedStoreCount;

    private String bindingStatus;

    private Long defaultOwnerUserId;

    private AuthUserStore currentStore;

    private List<AuthUserStore> userStores = new ArrayList<>();

    private List<AuthGrantedMenu> grantedMenus = new ArrayList<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
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

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
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

    public String getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(String bindingStatus) {
        this.bindingStatus = bindingStatus;
    }

    public Long getDefaultOwnerUserId() {
        return defaultOwnerUserId;
    }

    public void setDefaultOwnerUserId(Long defaultOwnerUserId) {
        this.defaultOwnerUserId = defaultOwnerUserId;
    }

    public AuthUserStore getCurrentStore() {
        return currentStore;
    }

    public void setCurrentStore(AuthUserStore currentStore) {
        this.currentStore = currentStore;
    }

    public List<AuthUserStore> getUserStores() {
        return userStores;
    }

    public void setUserStores(List<AuthUserStore> userStores) {
        this.userStores = userStores == null ? new ArrayList<>() : userStores;
    }

    public List<AuthGrantedMenu> getGrantedMenus() {
        return grantedMenus;
    }

    public void setGrantedMenus(List<AuthGrantedMenu> grantedMenus) {
        this.grantedMenus = grantedMenus == null ? new ArrayList<>() : grantedMenus;
    }
}
