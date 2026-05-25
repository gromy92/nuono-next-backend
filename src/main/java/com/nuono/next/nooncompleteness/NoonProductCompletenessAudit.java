package com.nuono.next.nooncompleteness;

public class NoonProductCompletenessAudit {
    private long productMasterCount;
    private long siteOfferCount;
    private long detailBaselineCount;

    public NoonProductCompletenessAudit() {
    }

    public NoonProductCompletenessAudit(long productMasterCount, long siteOfferCount, long detailBaselineCount) {
        this.productMasterCount = Math.max(productMasterCount, 0);
        this.siteOfferCount = Math.max(siteOfferCount, 0);
        this.detailBaselineCount = Math.max(detailBaselineCount, 0);
    }

    public static NoonProductCompletenessAudit empty() {
        return new NoonProductCompletenessAudit(0, 0, 0);
    }

    public boolean hasProductListBaseline() {
        return siteOfferCount > 0;
    }

    public DetailCoverage detailCoverage() {
        if (productMasterCount <= 0 || detailBaselineCount <= 0) {
            return DetailCoverage.MISSING;
        }
        if (detailBaselineCount >= productMasterCount) {
            return DetailCoverage.COMPLETE;
        }
        return DetailCoverage.PARTIAL;
    }

    public long getProductMasterCount() {
        return productMasterCount;
    }

    public void setProductMasterCount(long productMasterCount) {
        this.productMasterCount = Math.max(productMasterCount, 0);
    }

    public long getSiteOfferCount() {
        return siteOfferCount;
    }

    public void setSiteOfferCount(long siteOfferCount) {
        this.siteOfferCount = Math.max(siteOfferCount, 0);
    }

    public long getDetailBaselineCount() {
        return detailBaselineCount;
    }

    public void setDetailBaselineCount(long detailBaselineCount) {
        this.detailBaselineCount = Math.max(detailBaselineCount, 0);
    }

    public enum DetailCoverage {
        COMPLETE,
        PARTIAL,
        MISSING
    }
}
