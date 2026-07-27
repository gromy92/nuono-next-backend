package com.nuono.next.productlisting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ProductListingAiValidationResult {

    private final List<String> issues;
    private final List<String> hardConflicts;

    ProductListingAiValidationResult(List<String> issues, List<String> hardConflicts) {
        this.issues = issues == null ? List.of() : new ArrayList<>(issues);
        this.hardConflicts = hardConflicts == null ? List.of() : new ArrayList<>(hardConflicts);
    }

    boolean isReady() {
        return issues.isEmpty();
    }

    boolean hasHardConflicts() {
        return !hardConflicts.isEmpty();
    }

    List<String> messages() {
        return new ArrayList<>(issues);
    }

    List<Map<String, Object>> repairIssues() {
        return issues.stream()
                .map(message -> {
                    Map<String, Object> issue = new LinkedHashMap<>();
                    issue.put("code", isHardConflictMessage(message)
                            ? "SOURCE_FACT_CONFLICT_CANDIDATE"
                            : "DETERMINISTIC_OUTPUT_VALIDATION");
                    issue.put("message", message);
                    issue.put("repairable", true);
                    return issue;
                })
                .collect(Collectors.toList());
    }

    private boolean isHardConflictMessage(String message) {
        return hardConflicts.stream().anyMatch(message::contains);
    }
}
