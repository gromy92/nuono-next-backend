package com.nuono.next.masterdata;

import java.util.ArrayList;
import java.util.List;

public class MasterDataOrgNodeView {

    private Long id;

    private String accountNo;

    private String realName;

    private String roleName;

    private Integer roleLevel;

    private String companyName;

    private String storeSummary;

    private List<MasterDataOrgNodeView> children = new ArrayList<>();

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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getStoreSummary() {
        return storeSummary;
    }

    public void setStoreSummary(String storeSummary) {
        this.storeSummary = storeSummary;
    }

    public List<MasterDataOrgNodeView> getChildren() {
        return children;
    }

    public void setChildren(List<MasterDataOrgNodeView> children) {
        this.children = children == null ? new ArrayList<>() : children;
    }
}
