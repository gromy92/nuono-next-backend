package com.nuono.next.datapull.orchestration;

/** Database-derived immutable identities bound into one managed DP release. */
public final class DataPullReleaseDatabaseBinding {
    private String schemaBindingSha256;
    private String cutoverBindingSha256;
    private Long cutoverOperationCount;

    public String getSchemaBindingSha256() { return schemaBindingSha256; }
    public void setSchemaBindingSha256(String value) { schemaBindingSha256 = value; }
    public String getCutoverBindingSha256() { return cutoverBindingSha256; }
    public void setCutoverBindingSha256(String value) { cutoverBindingSha256 = value; }
    public Long getCutoverOperationCount() { return cutoverOperationCount; }
    public void setCutoverOperationCount(Long value) { cutoverOperationCount = value; }
}
