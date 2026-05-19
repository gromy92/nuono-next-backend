package com.nuono.next.foundation;

import java.util.ArrayList;
import java.util.List;

public class FoundationUserScopeSummary {

    private Integer directCompanyCount;

    private String directCompanies;

    private Integer managedStoreCount;

    private Integer managedAuthorizedStoreCount;

    private Integer managedCompanyCount;

    private String managedCompanies;

    private Integer descendantUserCount;

    private Integer descendantStoreCount;

    private Integer descendantCompanyCount;

    private List<FoundationUserStoreLink> descendantStoreLinks = new ArrayList<>();

    public Integer getDirectCompanyCount() {
        return directCompanyCount;
    }

    public void setDirectCompanyCount(Integer directCompanyCount) {
        this.directCompanyCount = directCompanyCount;
    }

    public String getDirectCompanies() {
        return directCompanies;
    }

    public void setDirectCompanies(String directCompanies) {
        this.directCompanies = directCompanies;
    }

    public Integer getManagedStoreCount() {
        return managedStoreCount;
    }

    public void setManagedStoreCount(Integer managedStoreCount) {
        this.managedStoreCount = managedStoreCount;
    }

    public Integer getManagedAuthorizedStoreCount() {
        return managedAuthorizedStoreCount;
    }

    public void setManagedAuthorizedStoreCount(Integer managedAuthorizedStoreCount) {
        this.managedAuthorizedStoreCount = managedAuthorizedStoreCount;
    }

    public Integer getManagedCompanyCount() {
        return managedCompanyCount;
    }

    public void setManagedCompanyCount(Integer managedCompanyCount) {
        this.managedCompanyCount = managedCompanyCount;
    }

    public String getManagedCompanies() {
        return managedCompanies;
    }

    public void setManagedCompanies(String managedCompanies) {
        this.managedCompanies = managedCompanies;
    }

    public Integer getDescendantUserCount() {
        return descendantUserCount;
    }

    public void setDescendantUserCount(Integer descendantUserCount) {
        this.descendantUserCount = descendantUserCount;
    }

    public Integer getDescendantStoreCount() {
        return descendantStoreCount;
    }

    public void setDescendantStoreCount(Integer descendantStoreCount) {
        this.descendantStoreCount = descendantStoreCount;
    }

    public Integer getDescendantCompanyCount() {
        return descendantCompanyCount;
    }

    public void setDescendantCompanyCount(Integer descendantCompanyCount) {
        this.descendantCompanyCount = descendantCompanyCount;
    }

    public List<FoundationUserStoreLink> getDescendantStoreLinks() {
        return descendantStoreLinks;
    }

    public void setDescendantStoreLinks(List<FoundationUserStoreLink> descendantStoreLinks) {
        this.descendantStoreLinks = descendantStoreLinks;
    }
}
