package com.nuono.next.mobile;

import java.util.ArrayList;
import java.util.List;

public class MobileAuthResponse {

    private Boolean needBindPhone;

    private String bindToken;

    private Integer expiresIn;

    private String token;

    private String refreshToken;

    private MobileUserView user;

    private List<MobileStoreView> stores = new ArrayList<>();

    private String defaultStoreCode;

    public Boolean getNeedBindPhone() {
        return needBindPhone;
    }

    public void setNeedBindPhone(Boolean needBindPhone) {
        this.needBindPhone = needBindPhone;
    }

    public String getBindToken() {
        return bindToken;
    }

    public void setBindToken(String bindToken) {
        this.bindToken = bindToken;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public MobileUserView getUser() {
        return user;
    }

    public void setUser(MobileUserView user) {
        this.user = user;
    }

    public List<MobileStoreView> getStores() {
        return stores;
    }

    public void setStores(List<MobileStoreView> stores) {
        this.stores = stores;
    }

    public String getDefaultStoreCode() {
        return defaultStoreCode;
    }

    public void setDefaultStoreCode(String defaultStoreCode) {
        this.defaultStoreCode = defaultStoreCode;
    }
}
