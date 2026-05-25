package com.nuono.next.noonpull;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class NoonProductDetailBackfillPlanner {

    public NoonProductDetailBackfillPlan plan(NoonProductDetailBackfillRequest request) {
        Set<String> selected = new LinkedHashSet<>();
        int max = Math.max(request.getMaxDetailFetches(), 0);
        addOne(selected, request.getOpenedSkuParent(), max);
        addAll(selected, request.getMissingBaselineSkuParents(), max);
        addAll(selected, request.getExplicitRefreshSkuParents(), max);
        addAll(selected, request.getPublishReadbackSkuParents(), max);
        addAll(selected, request.getPrioritySkuParents(), max);
        boolean blindFullStore = false;
        if (!request.getAllSkuParents().isEmpty() && selected.size() >= request.getAllSkuParents().size()) {
            blindFullStore = selected.containsAll(request.getAllSkuParents());
        }
        return new NoonProductDetailBackfillPlan(List.copyOf(selected), "bounded_priority", blindFullStore);
    }

    private void addOne(Set<String> selected, String value, int max) {
        if (max > 0 && selected.size() >= max) {
            return;
        }
        if (StringUtils.hasText(value)) {
            selected.add(value.trim());
        }
    }

    private void addAll(Set<String> selected, List<String> values, int max) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (max > 0 && selected.size() >= max) {
                return;
            }
            addOne(selected, value, max);
        }
    }
}
