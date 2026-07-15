package com.nuono.next.product;

import java.util.List;

public class NoonImageTechnicalComplianceView {
    private String status;
    private String policyVersion;
    private String policySource;
    private List<NoonImageComplianceCheckView> checks;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public String getPolicySource() { return policySource; }
    public void setPolicySource(String policySource) { this.policySource = policySource; }
    public List<NoonImageComplianceCheckView> getChecks() { return checks; }
    public void setChecks(List<NoonImageComplianceCheckView> checks) { this.checks = checks; }
}
