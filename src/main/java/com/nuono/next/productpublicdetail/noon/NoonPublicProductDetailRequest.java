package com.nuono.next.productpublicdetail.noon;

public class NoonPublicProductDetailRequest {
    private String siteCode;
    private String locale;
    private String noonProductCode;

    public static Builder builder() {
        return new Builder();
    }

    public String getSiteCode() { return siteCode; }
    public void setSiteCode(String siteCode) { this.siteCode = siteCode; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getNoonProductCode() { return noonProductCode; }
    public void setNoonProductCode(String noonProductCode) { this.noonProductCode = noonProductCode; }

    public static final class Builder {
        private final NoonPublicProductDetailRequest request = new NoonPublicProductDetailRequest();

        public Builder siteCode(String siteCode) {
            request.siteCode = siteCode;
            return this;
        }

        public Builder locale(String locale) {
            request.locale = locale;
            return this;
        }

        public Builder noonProductCode(String noonProductCode) {
            request.noonProductCode = noonProductCode;
            return this;
        }

        public NoonPublicProductDetailRequest build() {
            return request;
        }
    }
}
