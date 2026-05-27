package com.nuono.next.foundation.standard;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class FoundationWorkflowStandard {

    private final List<String> statusKeys;
    private final List<String> transitions;
    private final List<String> recoveryRules;
    private final String referenceFlow;
    private final Set<String> transitionSet;

    public FoundationWorkflowStandard(
            List<String> statusKeys,
            List<String> transitions,
            List<String> recoveryRules,
            String referenceFlow
    ) {
        this.statusKeys = List.copyOf(statusKeys);
        this.transitions = List.copyOf(transitions);
        this.recoveryRules = List.copyOf(recoveryRules);
        this.referenceFlow = referenceFlow;
        this.transitionSet = transitions.stream().map(this::normalizeTransition).collect(Collectors.toUnmodifiableSet());
    }

    public List<String> getStatusKeys() {
        return statusKeys;
    }

    public List<String> getTransitions() {
        return transitions;
    }

    public List<String> getRecoveryRules() {
        return recoveryRules;
    }

    public String getReferenceFlow() {
        return referenceFlow;
    }

    public boolean isAllowedTransition(String fromStatus, String toStatus) {
        return transitionSet.contains(normalize(fromStatus) + "->" + normalize(toStatus));
    }

    private String normalizeTransition(String transition) {
        String[] parts = transition == null ? new String[0] : transition.split("->", 2);
        if (parts.length != 2) {
            return "";
        }
        return normalize(parts[0]) + "->" + normalize(parts[1]);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
