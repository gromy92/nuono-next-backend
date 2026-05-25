package com.nuono.next.noonpull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NoonPullSmokeGateResult {
    private final List<String> missingRequirements;

    public NoonPullSmokeGateResult(List<String> missingRequirements) {
        this.missingRequirements = missingRequirements == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(missingRequirements));
    }

    public boolean isReleaseAllowed() {
        return missingRequirements.isEmpty();
    }

    public List<String> getMissingRequirements() {
        return missingRequirements;
    }
}
