package com.nuono.next.store;

public class StoreBindCommand {

    private Long ownerUserId;

    private String storeCode;

    private String noonUser;

    private String noonProjectUser;

    private String noonPassword;

    private String noonPartnerId;

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(Long ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getNoonUser() {
        return noonUser;
    }

    public void setNoonUser(String noonUser) {
        this.noonUser = noonUser;
    }

    public String getNoonProjectUser() {
        return noonProjectUser;
    }

    public void setNoonProjectUser(String noonProjectUser) {
        this.noonProjectUser = noonProjectUser;
    }

    public String getNoonPassword() {
        return noonPassword;
    }

    public void setNoonPassword(String noonPassword) {
        this.noonPassword = noonPassword;
    }

    public String getNoonPartnerId() {
        return noonPartnerId;
    }

    public void setNoonPartnerId(String noonPartnerId) {
        this.noonPartnerId = noonPartnerId;
    }
}
