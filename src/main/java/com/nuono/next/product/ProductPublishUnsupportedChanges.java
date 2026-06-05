package com.nuono.next.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

class ProductPublishUnsupportedChanges {

    private boolean groupChanged;

    private boolean variantStructureChanged;

    private final Set<String> unsupportedAttributeCodes = new LinkedHashSet<>();

    private final Map<String, Set<String>> unsupportedSiteFields = new LinkedHashMap<>();

    private final List<String> publishBlockers = new ArrayList<>();

    boolean isGroupChanged() {
        return groupChanged;
    }

    void setGroupChanged(boolean groupChanged) {
        this.groupChanged = groupChanged;
    }

    boolean isVariantStructureChanged() {
        return variantStructureChanged;
    }

    void setVariantStructureChanged(boolean variantStructureChanged) {
        this.variantStructureChanged = variantStructureChanged;
    }

    Set<String> getUnsupportedAttributeCodes() {
        return unsupportedAttributeCodes;
    }

    Map<String, Set<String>> getUnsupportedSiteFields() {
        return unsupportedSiteFields;
    }

    List<String> getPublishBlockers() {
        return publishBlockers;
    }

    void markUnsupportedSiteField(String siteCode, String field) {
        if (!StringUtils.hasText(siteCode) || !StringUtils.hasText(field)) {
            return;
        }
        unsupportedSiteFields.computeIfAbsent(siteCode, ignored -> new LinkedHashSet<>()).add(field);
    }
}
