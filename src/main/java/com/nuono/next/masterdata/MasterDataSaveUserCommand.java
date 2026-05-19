package com.nuono.next.masterdata;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class MasterDataSaveUserCommand {

    private String accountNo;

    private String realName;

    private String phone;

    private String email;

    private String password;

    private String accountType;

    private String companyName;

    private Long roleId;

    private Long operatorUserId;

    private LocalDateTime expiredTime;

    private List<String> storeCodes = new ArrayList<>();

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public Long getOperatorUserId() {
        return operatorUserId;
    }

    public void setOperatorUserId(Long operatorUserId) {
        this.operatorUserId = operatorUserId;
    }

    public LocalDateTime getExpiredTime() {
        return expiredTime;
    }

    public void setExpiredTime(LocalDateTime expiredTime) {
        this.expiredTime = expiredTime;
    }

    public List<String> getStoreCodes() {
        return storeCodes;
    }

    public void setStoreCodes(List<String> storeCodes) {
        this.storeCodes = storeCodes == null ? new ArrayList<>() : storeCodes;
    }
}
