package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ali1688GatewaySmokeRunReadinessContractTest {

    @Test
    void buildsReadyRunPlanFromFixedSampleBoundaryAndRuntimeEnvironment() {
        Ali1688GatewaySmokeRunReadiness readiness = Ali1688GatewaySmokeRunReadiness.evaluate(
                fixedSample(),
                readyBoundary(),
                gatewayEnvironment("https://browser-gateway.internal/ali1688/image-search")
        );

        assertTrue(readiness.isReady());
        assertTrue(readiness.getFailures().isEmpty());
        assertEquals("86006", readiness.getRunPlan().getSourceCollectionId());
        assertEquals("POST", readiness.getRunPlan().getTriggerStep().getMethod());
        assertEquals("/api/product-selection/source-collections/86006/ali1688/recollect",
                readiness.getRunPlan().getTriggerStep().getPath());
        assertEquals("success_or_partial_success", readiness.getRunPlan().getExpectedOutcome());
        assertEquals("https://browser-gateway.internal/ali1688/image-search",
                readiness.getEffectiveEnvironment().get("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENDPOINT_URL"));
    }

    @Test
    void refusesRealRunWhenRuntimeGatewayEndpointIsMissing() {
        Map<String, String> environment = gatewayEnvironment("");

        Ali1688GatewaySmokeRunReadiness readiness =
                Ali1688GatewaySmokeRunReadiness.evaluate(fixedSample(), readyBoundary(), environment);

        assertFalse(readiness.isReady());
        assertTrue(readiness.getFailures().contains(
                "runtime browser gateway endpoint must be configured before real smoke run"));
    }

    @Test
    void refusesRealRunWhenRuntimeGatewayIsDisabled() {
        Map<String, String> environment = gatewayEnvironment("https://browser-gateway.internal/ali1688/image-search");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENABLED", "false");

        Ali1688GatewaySmokeRunReadiness readiness =
                Ali1688GatewaySmokeRunReadiness.evaluate(fixedSample(), readyBoundary(), environment);

        assertFalse(readiness.isReady());
        assertTrue(readiness.getFailures().contains(
                "runtime 1688 image-search gateway must be enabled for real smoke run"));
    }

    @Test
    void refusesRealRunWhenRuntimeEndpointDiffersFromConfirmedBoundary() {
        Ali1688GatewaySmokeRunReadiness readiness = Ali1688GatewaySmokeRunReadiness.evaluate(
                fixedSample(),
                readyBoundary(),
                gatewayEnvironment("https://other-gateway.internal/ali1688/image-search")
        );

        assertFalse(readiness.isReady());
        assertTrue(readiness.getFailures().contains(
                "runtime browser gateway endpoint must match the confirmed boundary endpoint"));
    }

    @Test
    void refusesLocalBridgeEndpointAndCandidateLimitAboveTen() {
        Map<String, String> environment = gatewayEnvironment("http://127.0.0.1:17888/ali1688/image-search/latest");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_MAX_CANDIDATES", "20");

        Ali1688GatewaySmokeRunReadiness readiness =
                Ali1688GatewaySmokeRunReadiness.evaluate(fixedSample(), localBridgeBoundary(), environment);

        assertFalse(readiness.isReady());
        assertTrue(readiness.getFailures().contains("browser gateway endpoint must not point at the local smoke bridge"));
        assertTrue(readiness.getFailures().contains("runtime max candidates must be between 1 and 10"));
    }

    private Ali1688GatewaySmokePreparation.FixedSample fixedSample() {
        Ali1688GatewaySmokePreparation.FixedSample sample = new Ali1688GatewaySmokePreparation.FixedSample();
        sample.setSourceCollectionId(86006L);
        sample.setCollectionNo("PSC-86006");
        sample.setOwnerUserId(307L);
        sample.setLogicalStoreId(50005L);
        sample.setSourcePlatform("Amazon");
        sample.setSourceUrl("https://www.amazon.com/dp/B0C7RHFC3F");
        sample.setSourceTitle("DUYONE Artificial Flowers 6 Stems");
        sample.setSourceImageUrl("https://m.media-amazon.com/images/I/715Txj8syIL._AC_SL1500_.jpg");
        return sample;
    }

    private Ali1688GatewaySmokePreparation.BrowserGatewayBoundary readyBoundary() {
        Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary =
                new Ali1688GatewaySmokePreparation.BrowserGatewayBoundary();
        boundary.setGatewayServiceKind("system_browser_gateway");
        boundary.setEndpointUrl("https://browser-gateway.internal/ali1688/image-search");
        boundary.setSessionState("ready");
        boundary.setCaptchaBoundaryMode("return_typed_error");
        boundary.setCaptchaAutoSolveEnabled(false);
        boundary.setConfirmedBy("ops-owner");
        boundary.setProviderTraceId("session-check-001");
        return boundary;
    }

    private Ali1688GatewaySmokePreparation.BrowserGatewayBoundary localBridgeBoundary() {
        Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary = readyBoundary();
        boundary.setEndpointUrl("http://127.0.0.1:17888/ali1688/image-search/latest");
        return boundary;
    }

    private Map<String, String> gatewayEnvironment(String endpointUrl) {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENABLED", "true");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENDPOINT_URL", endpointUrl);
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_AUTH_HEADER_NAME", "Authorization");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_TIMEOUT_SECONDS", "30");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_MAX_CANDIDATES", "10");
        environment.put("NUONO_PRODUCT_SELECTION_ALI1688_SCHEDULER_ENABLED", "true");
        return environment;
    }
}
