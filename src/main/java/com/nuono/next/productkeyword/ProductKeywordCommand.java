package com.nuono.next.productkeyword;

import java.util.ArrayList;
import java.util.List;

public class ProductKeywordCommand {
    private String storeCode;
    private String siteCode;
    private String partnerSku;
    private String keyword;
    private String locale;
    private List<String> intentTags = new ArrayList<>();

    public String getStoreCode() {
        return storeCode;
    }

    public void setStoreCode(String storeCode) {
        this.storeCode = storeCode;
    }

    public String getSiteCode() {
        return siteCode;
    }

    public void setSiteCode(String siteCode) {
        this.siteCode = siteCode;
    }

    public String getPartnerSku() {
        return partnerSku;
    }

    public void setPartnerSku(String partnerSku) {
        this.partnerSku = partnerSku;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public List<String> getIntentTags() {
        return intentTags;
    }

    public void setIntentTags(List<String> intentTags) {
        this.intentTags = intentTags == null ? new ArrayList<>() : new ArrayList<>(intentTags);
    }
}
