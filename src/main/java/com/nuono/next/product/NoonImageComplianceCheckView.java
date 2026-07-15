package com.nuono.next.product;

public class NoonImageComplianceCheckView {
    private String key;
    private String status;
    private String actual;
    private String requirement;
    private String message;

    public NoonImageComplianceCheckView() {
    }

    public NoonImageComplianceCheckView(String key, String status, String actual, String requirement, String message) {
        this.key = key;
        this.status = status;
        this.actual = actual;
        this.requirement = requirement;
        this.message = message;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getActual() { return actual; }
    public void setActual(String actual) { this.actual = actual; }
    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
