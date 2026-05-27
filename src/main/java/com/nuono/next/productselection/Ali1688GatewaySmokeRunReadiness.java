package com.nuono.next.productselection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

public final class Ali1688GatewaySmokeRunReadiness {

    private static final String ENABLED_KEY = "NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENABLED";
    private static final String ENDPOINT_URL_KEY = "NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENDPOINT_URL";
    private static final String MAX_CANDIDATES_KEY = "NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_MAX_CANDIDATES";

    private final boolean ready;
    private final List<String> failures;
    private final Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan;
    private final Map<String, String> effectiveEnvironment;

    private Ali1688GatewaySmokeRunReadiness(
            boolean ready,
            List<String> failures,
            Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan,
            Map<String, String> effectiveEnvironment
    ) {
        this.ready = ready;
        this.failures = Collections.unmodifiableList(new ArrayList<>(failures));
        this.runPlan = runPlan;
        this.effectiveEnvironment = Collections.unmodifiableMap(new LinkedHashMap<>(effectiveEnvironment));
    }

    public static Ali1688GatewaySmokeRunReadiness evaluate(
            Ali1688GatewaySmokePreparation.FixedSample sample,
            Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary,
            Map<String, String> runtimeEnvironment
    ) {
        Ali1688GatewaySmokePreparation.Plan preparation = Ali1688GatewaySmokePreparation.prepare(sample, boundary);
        Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan = Ali1688GatewaySmokeIntegrationGate.from(preparation);
        Map<String, String> environment = normalizeEnvironment(runtimeEnvironment);
        List<String> failures = new ArrayList<>(runPlan.getFailures());
        validateRuntimeEnvironment(preparation, environment, failures);
        return new Ali1688GatewaySmokeRunReadiness(
                runPlan.isReady() && failures.isEmpty(),
                failures,
                runPlan,
                environment
        );
    }

    public boolean isReady() {
        return ready;
    }

    public List<String> getFailures() {
        return failures;
    }

    public Ali1688GatewaySmokeIntegrationGate.RunPlan getRunPlan() {
        return runPlan;
    }

    public Map<String, String> getEffectiveEnvironment() {
        return effectiveEnvironment;
    }

    private static void validateRuntimeEnvironment(
            Ali1688GatewaySmokePreparation.Plan preparation,
            Map<String, String> environment,
            List<String> failures
    ) {
        if (!"true".equalsIgnoreCase(environment.get(ENABLED_KEY))) {
            failures.add("runtime 1688 image-search gateway must be enabled for real smoke run");
        }

        String endpointUrl = normalize(environment.get(ENDPOINT_URL_KEY));
        if (!StringUtils.hasText(endpointUrl)) {
            failures.add("runtime browser gateway endpoint must be configured before real smoke run");
        } else {
            if (looksLikeLocalBridge(endpointUrl)) {
                failures.add("runtime browser gateway endpoint must not point at the local smoke bridge");
            }
            Object confirmedEndpoint = preparation.getBoundaryRecord().get("endpointUrl");
            String confirmedEndpointUrl = confirmedEndpoint instanceof String ? normalize((String) confirmedEndpoint) : "";
            if (StringUtils.hasText(confirmedEndpointUrl)
                    && !endpointUrl.equals(confirmedEndpointUrl)) {
                failures.add("runtime browser gateway endpoint must match the confirmed boundary endpoint");
            }
        }

        Integer maxCandidates = parseInteger(environment.get(MAX_CANDIDATES_KEY));
        if (maxCandidates == null || maxCandidates < 1 || maxCandidates > 10) {
            failures.add("runtime max candidates must be between 1 and 10");
        }
    }

    private static Map<String, String> normalizeEnvironment(Map<String, String> runtimeEnvironment) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (runtimeEnvironment != null) {
            for (Map.Entry<String, String> entry : runtimeEnvironment.entrySet()) {
                normalized.put(entry.getKey(), normalize(entry.getValue()));
            }
        }
        return normalized;
    }

    private static boolean looksLikeLocalBridge(String endpointUrl) {
        String lower = endpointUrl.toLowerCase();
        return lower.contains("smoke_bridge")
                || lower.contains("smoke-bridge")
                || lower.contains("/ali1688/image-search/latest")
                || lower.contains("plugin");
    }

    private static Integer parseInteger(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
