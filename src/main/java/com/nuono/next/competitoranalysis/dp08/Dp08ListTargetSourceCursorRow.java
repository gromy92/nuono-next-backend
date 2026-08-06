package com.nuono.next.competitoranalysis.dp08;

/** Native stable source tuple for one DP08B owner/store/site/product union. */
public class Dp08ListTargetSourceCursorRow {
    private Long ownerUserId;
    private String storeCode;
    private String siteCode;
    private String noonProductCode;

    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long value) { ownerUserId = value; }
    public String getStoreCode() { return storeCode; }
    public void setStoreCode(String value) { storeCode = value; }
    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String value) { siteCode = value; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String value) { noonProductCode = value; }
}
