package com.nuono.next.product;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

public class ProductUnsupportedChanges {

    private boolean groupChanged;

    private boolean variantStructureChanged;

    private final Set<String> unsupportedAttributeCodes = new LinkedHashSet<>();

    private final Map<String, Set<String>> unsupportedSiteFields = new LinkedHashMap<>();

    private final List<String> publishBlockers = new ArrayList<>();

    public boolean isGroupChanged() {
        return groupChanged;
    }

    public void setGroupChanged(boolean groupChanged) {
        this.groupChanged = groupChanged;
    }

    public boolean isVariantStructureChanged() {
        return variantStructureChanged;
    }

    public void setVariantStructureChanged(boolean variantStructureChanged) {
        this.variantStructureChanged = variantStructureChanged;
    }

    public Set<String> getUnsupportedAttributeCodes() {
        return Collections.unmodifiableSet(unsupportedAttributeCodes);
    }

    public Map<String, Set<String>> getUnsupportedSiteFields() {
        Map<String, Set<String>> view = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : unsupportedSiteFields.entrySet()) {
            view.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(view);
    }

    public List<String> getPublishBlockers() {
        return Collections.unmodifiableList(publishBlockers);
    }

    public void addUnsupportedAttributeCode(String code) {
        if (!StringUtils.hasText(code)) {
            return;
        }
        unsupportedAttributeCodes.add(code.trim());
    }

    public void addPublishBlocker(String blocker) {
        if (!StringUtils.hasText(blocker)) {
            return;
        }
        publishBlockers.add(blocker.trim());
    }

    public void addPublishBlockers(Collection<String> blockers) {
        if (blockers == null) {
            return;
        }
        for (String blocker : blockers) {
            addPublishBlocker(blocker);
        }
    }

    public void markUnsupportedSiteField(String siteCode, String field) {
        if (!StringUtils.hasText(siteCode) || !StringUtils.hasText(field)) {
            return;
        }
        unsupportedSiteFields
                .computeIfAbsent(siteCode.trim(), ignored -> new LinkedHashSet<>())
                .add(field.trim());
    }
}
