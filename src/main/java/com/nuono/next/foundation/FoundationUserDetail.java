package com.nuono.next.foundation;

import java.util.ArrayList;
import java.util.List;

public class FoundationUserDetail {

    private Long id;

    private String accountNo;

    private String realName;

    private String phone;

    private String email;

    private String companyName;

    private Integer status;

    private String accountType;

    private Long roleId;

    private String roleName;

    private Integer roleLevel;

    private Boolean systemRole;

    private Integer listLimit;

    private Integer collectLimit;

    private Integer whApLimit;

    private Integer chatgptTranslateLimit;

    private Integer storeCount;

    private Integer authorizedStoreCount;

    private Integer directCompanyCount;

    private String directCompanies;

    private Integer managedStoreCount;

    private Integer managedAuthorizedStoreCount;

    private Integer managedCompanyCount;

    private String managedCompanies;

    private Integer descendantUserCount;

    private Integer descendantStoreCount;

    private Integer descendantCompanyCount;

    private Integer roleMenuCount;

    private Integer userMenuCount;

    private String sites;

    private String bindingStatus;

    private String noonPartnerUser;

    private String noonPartnerProjectUser;

    private String noonPartnerId;

    private String noonPartnerUserCode;

    private String noonPartnerMailAuthCode;

    private String cookieGenerateTime;

    private String effectiveTime;

    private String expiredTime;

    private List<FoundationUserStoreLink> storeLinks = new ArrayList<>();

    private List<FoundationUserStoreLink> descendantStoreLinks = new ArrayList<>();

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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
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

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
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

    public Integer getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(Integer roleLevel) {
        this.roleLevel = roleLevel;
    }

    public Boolean getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(Boolean systemRole) {
        this.systemRole = systemRole;
    }

    public Integer getListLimit() {
        return listLimit;
    }

    public void setListLimit(Integer listLimit) {
        this.listLimit = listLimit;
    }

    public Integer getCollectLimit() {
        return collectLimit;
    }

    public void setCollectLimit(Integer collectLimit) {
        this.collectLimit = collectLimit;
    }

    public Integer getWhApLimit() {
        return whApLimit;
    }

    public void setWhApLimit(Integer whApLimit) {
        this.whApLimit = whApLimit;
    }

    public Integer getChatgptTranslateLimit() {
        return chatgptTranslateLimit;
    }

    public void setChatgptTranslateLimit(Integer chatgptTranslateLimit) {
        this.chatgptTranslateLimit = chatgptTranslateLimit;
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

    public Integer getDirectCompanyCount() {
        return directCompanyCount;
    }

    public void setDirectCompanyCount(Integer directCompanyCount) {
        this.directCompanyCount = directCompanyCount;
    }

    public String getDirectCompanies() {
        return directCompanies;
    }

    public void setDirectCompanies(String directCompanies) {
        this.directCompanies = directCompanies;
    }

    public Integer getManagedStoreCount() {
        return managedStoreCount;
    }

    public void setManagedStoreCount(Integer managedStoreCount) {
        this.managedStoreCount = managedStoreCount;
    }

    public Integer getManagedAuthorizedStoreCount() {
        return managedAuthorizedStoreCount;
    }

    public void setManagedAuthorizedStoreCount(Integer managedAuthorizedStoreCount) {
        this.managedAuthorizedStoreCount = managedAuthorizedStoreCount;
    }

    public Integer getManagedCompanyCount() {
        return managedCompanyCount;
    }

    public void setManagedCompanyCount(Integer managedCompanyCount) {
        this.managedCompanyCount = managedCompanyCount;
    }

    public String getManagedCompanies() {
        return managedCompanies;
    }

    public void setManagedCompanies(String managedCompanies) {
        this.managedCompanies = managedCompanies;
    }

    public Integer getDescendantUserCount() {
        return descendantUserCount;
    }

    public void setDescendantUserCount(Integer descendantUserCount) {
        this.descendantUserCount = descendantUserCount;
    }

    public Integer getDescendantStoreCount() {
        return descendantStoreCount;
    }

    public void setDescendantStoreCount(Integer descendantStoreCount) {
        this.descendantStoreCount = descendantStoreCount;
    }

    public Integer getDescendantCompanyCount() {
        return descendantCompanyCount;
    }

    public void setDescendantCompanyCount(Integer descendantCompanyCount) {
        this.descendantCompanyCount = descendantCompanyCount;
    }

    public Integer getRoleMenuCount() {
        return roleMenuCount;
    }

    public void setRoleMenuCount(Integer roleMenuCount) {
        this.roleMenuCount = roleMenuCount;
    }

    public Integer getUserMenuCount() {
        return userMenuCount;
    }

    public void setUserMenuCount(Integer userMenuCount) {
        this.userMenuCount = userMenuCount;
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

    public String getNoonPartnerUser() {
        return noonPartnerUser;
    }

    public void setNoonPartnerUser(String noonPartnerUser) {
        this.noonPartnerUser = noonPartnerUser;
    }

    public String getNoonPartnerProjectUser() {
        return noonPartnerProjectUser;
    }

    public void setNoonPartnerProjectUser(String noonPartnerProjectUser) {
        this.noonPartnerProjectUser = noonPartnerProjectUser;
    }

    public String getNoonPartnerId() {
        return noonPartnerId;
    }

    public void setNoonPartnerId(String noonPartnerId) {
        this.noonPartnerId = noonPartnerId;
    }

    public String getNoonPartnerUserCode() {
        return noonPartnerUserCode;
    }

    public void setNoonPartnerUserCode(String noonPartnerUserCode) {
        this.noonPartnerUserCode = noonPartnerUserCode;
    }

    public String getNoonPartnerMailAuthCode() {
        return noonPartnerMailAuthCode;
    }

    public void setNoonPartnerMailAuthCode(String noonPartnerMailAuthCode) {
        this.noonPartnerMailAuthCode = noonPartnerMailAuthCode;
    }

    public String getCookieGenerateTime() {
        return cookieGenerateTime;
    }

    public void setCookieGenerateTime(String cookieGenerateTime) {
        this.cookieGenerateTime = cookieGenerateTime;
    }

    public String getEffectiveTime() {
        return effectiveTime;
    }

    public void setEffectiveTime(String effectiveTime) {
        this.effectiveTime = effectiveTime;
    }

    public String getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(String expiredTime) {
        this.expiredTime = expiredTime;
    }

    public List<FoundationUserStoreLink> getStoreLinks() {
        return storeLinks;
    }

    public void setStoreLinks(List<FoundationUserStoreLink> storeLinks) {
        this.storeLinks = storeLinks;
    }

    public List<FoundationUserStoreLink> getDescendantStoreLinks() {
        return descendantStoreLinks;
    }

    public void setDescendantStoreLinks(List<FoundationUserStoreLink> descendantStoreLinks) {
        this.descendantStoreLinks = descendantStoreLinks;
    }
}
