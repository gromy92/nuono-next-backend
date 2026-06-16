package com.nuono.next.productpublicdetail;

public class ProductPublicDetailSyncSummary {
    private int selected;
    private int succeeded;
    private int partial;
    private int notFound;
    private int failed;
    private int skipped;
    private long elapsedMillis;
    private String adapterVersion;

    public int getSelected() { return selected; }
    public void setSelected(int selected) { this.selected = selected; }
    public int getSucceeded() { return succeeded; }
    public void setSucceeded(int succeeded) { this.succeeded = succeeded; }
    public int getPartial() { return partial; }
    public void setPartial(int partial) { this.partial = partial; }
    public int getNotFound() { return notFound; }
    public void setNotFound(int notFound) { this.notFound = notFound; }
    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }
    public int getSkipped() { return skipped; }
    public void setSkipped(int skipped) { this.skipped = skipped; }
    public long getElapsedMillis() { return elapsedMillis; }
    public void setElapsedMillis(long elapsedMillis) { this.elapsedMillis = elapsedMillis; }
    public String getAdapterVersion() { return adapterVersion; }
    public void setAdapterVersion(String adapterVersion) { this.adapterVersion = adapterVersion; }

    public void increment(ProductPublicDetailSyncStatus status) {
        if (status == ProductPublicDetailSyncStatus.SUCCEEDED) {
            succeeded++;
        } else if (status == ProductPublicDetailSyncStatus.PARTIAL) {
            partial++;
        } else if (status == ProductPublicDetailSyncStatus.NOT_FOUND) {
            notFound++;
        } else if (status == ProductPublicDetailSyncStatus.FAILED) {
            failed++;
        }
    }
}
