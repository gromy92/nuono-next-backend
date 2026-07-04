package com.nuono.next.store;

import java.time.LocalDateTime;

public class StoreSyncOwnerContext {

    private Long id;

    private String accountNo;

    private String realName;

    private String roleName;

    private String companyName;

    private String noonPartnerUser;

    private String noonPartnerProjectUser;

    private String noonPartnerPwd;

    private String noonPartnerMailAuthCode;

    private String noonPartnerCookie;

    private LocalDateTime cookieGenerateTime;

    private String noonPartnerId;

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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
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

    public String getNoonPartnerPwd() {
        return noonPartnerPwd;
    }

    public void setNoonPartnerPwd(String noonPartnerPwd) {
        this.noonPartnerPwd = noonPartnerPwd;
    }

    public String getNoonPartnerMailAuthCode() {
        return noonPartnerMailAuthCode;
    }

    public void setNoonPartnerMailAuthCode(String noonPartnerMailAuthCode) {
        this.noonPartnerMailAuthCode = noonPartnerMailAuthCode;
    }

    public String getNoonPartnerCookie() {
        return noonPartnerCookie;
    }

    public void setNoonPartnerCookie(String noonPartnerCookie) {
        this.noonPartnerCookie = noonPartnerCookie;
    }

    public LocalDateTime getCookieGenerateTime() {
        return cookieGenerateTime;
    }

    public void setCookieGenerateTime(LocalDateTime cookieGenerateTime) {
        this.cookieGenerateTime = cookieGenerateTime;
    }

    public String getNoonPartnerId() {
        return noonPartnerId;
    }

    public void setNoonPartnerId(String noonPartnerId) {
        this.noonPartnerId = noonPartnerId;
    }
}
