package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.List;

public class ProductListingFieldValidationView {

    private List<ProductListingValidationIssue> issues = new ArrayList<>();

    public List<ProductListingValidationIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ProductListingValidationIssue> issues) {
        this.issues = issues == null ? new ArrayList<>() : issues;
    }
}
