package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688GatewaySmokePreparationContractTest {

    @Test
    void preparesFixedSampleSqlAndSystemBrowserGatewayBoundary() {
        Ali1688GatewaySmokePreparation.Plan plan =
                Ali1688GatewaySmokePreparation.prepare(fixedSample(), readyBoundary());

        assertTrue(plan.isReady());
        assertTrue(plan.getFailures().isEmpty());
        assertEquals("@ali1688_smoke_source_collection_id", plan.getSourceCollectionIdToken());
        assertEquals("success_or_partial_success", plan.getExpectedOutcome());
        assertEquals("", plan.getExpectedGatewayErrorCode());
        assertEquals("true", plan.getEnvironment().get("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENABLED"));
        assertEquals("https://browser-gateway.internal/ali1688/image-search",
                plan.getEnvironment().get("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_ENDPOINT_URL"));
        assertEquals("10", plan.getEnvironment().get("NUONO_PRODUCT_SELECTION_ALI1688_IMAGE_SEARCH_MAX_CANDIDATES"));
        assertEquals("true", plan.getEnvironment().get("NUONO_PRODUCT_SELECTION_ALI1688_SCHEDULER_ENABLED"));
        assertEquals("system_browser_gateway", plan.getBoundaryRecord().get("gatewayServiceKind"));
        assertEquals("return_typed_error", plan.getBoundaryRecord().get("captchaBoundaryMode"));
        assertEquals(false, plan.getBoundaryRecord().get("captchaAutoSolveEnabled"));

        String createSql = plan.getCreateSampleSql();
        assertTrue(createSql.contains("product_management_id_sequence"));
        assertTrue(createSql.contains("next_id = LAST_INSERT_ID(next_id + 1)"));
        assertFalse(createSql.contains("current_value"));
        assertTrue(createSql.contains("product_selection_source_collection"));
        assertTrue(createSql.contains("@ali1688_smoke_source_collection_id"));
        assertFalse(createSql.toLowerCase().contains("max(id)"));

        assertTrue(plan.getIsolationSql().contains("product_selection_ali1688_collection_task"));
        assertTrue(plan.getIsolationSql().contains("@ali1688_smoke_source_collection_id"));
        assertTrue(plan.getCleanupSql().contains("active_candidate_key = NULL"));
    }

    @Test
    void rejectsIncompleteFixedSampleFields() {
        Ali1688GatewaySmokePreparation.FixedSample sample = fixedSample();
        sample.setSourceImageUrl("");
        sample.setSourceTitle(" ");
        sample.setOwnerUserId(null);
        sample.setLogicalStoreId(null);

        Ali1688GatewaySmokePreparation.Plan plan =
                Ali1688GatewaySmokePreparation.prepare(sample, readyBoundary());

        assertFalse(plan.isReady());
        List<String> failures = plan.getFailures();
        assertTrue(failures.contains("source image URL is required"));
        assertTrue(failures.contains("source title is required"));
        assertTrue(failures.contains("owner user ID is required"));
        assertTrue(failures.contains("logical store ID is required"));
    }

    @Test
    void rejectsPluginBridgeManualPayloadAndCaptchaAutoSolve() {
        Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary = readyBoundary();
        boundary.setEndpointUrl("http://127.0.0.1:17888/ali1688/image-search/latest");
        boundary.setUsesPluginBridge(true);
        boundary.setUsesLocalSmokeBridge(true);
        boundary.setUsesManualPayload(true);
        boundary.setCaptchaAutoSolveEnabled(true);
        boundary.setCaptchaBoundaryMode("auto_solve");

        Ali1688GatewaySmokePreparation.Plan plan =
                Ali1688GatewaySmokePreparation.prepare(fixedSample(), boundary);

        assertFalse(plan.isReady());
        List<String> failures = plan.getFailures();
        assertTrue(failures.contains("browser gateway endpoint must not point at the local smoke bridge"));
        assertTrue(failures.contains("system smoke must not use Chrome plugin bridge"));
        assertTrue(failures.contains("system smoke must not use local smoke bridge"));
        assertTrue(failures.contains("system smoke must not use manual payload sending"));
        assertTrue(failures.contains("CAPTCHA boundary mode must be return_typed_error"));
        assertTrue(failures.contains("CAPTCHA auto-solve must be disabled"));
    }

    @Test
    void preparesCaptchaBoundaryAsTypedGatewayErrorOutcome() {
        Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary = readyBoundary();
        boundary.setSessionState("captcha_required");
        boundary.setProviderTraceId("browser-trace-captcha");
        boundary.setDiagnosticNote("1688 showed slider captcha during session check");

        Ali1688GatewaySmokePreparation.Plan plan =
                Ali1688GatewaySmokePreparation.prepare(fixedSample(), boundary);

        assertTrue(plan.isReady());
        assertEquals("typed_gateway_error", plan.getExpectedOutcome());
        assertEquals("captcha_required", plan.getExpectedGatewayErrorCode());
        assertEquals("browser-trace-captcha", plan.getBoundaryRecord().get("providerTraceId"));
        assertEquals("1688 showed slider captcha during session check", plan.getBoundaryRecord().get("diagnosticNote"));
    }

    @Test
    void requiresTraceOrDiagnosticWhenSessionBoundaryIsBlocked() {
        Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary = readyBoundary();
        boundary.setSessionState("login_required");
        boundary.setProviderTraceId("");
        boundary.setDiagnosticNote("");

        Ali1688GatewaySmokePreparation.Plan plan =
                Ali1688GatewaySmokePreparation.prepare(fixedSample(), boundary);

        assertFalse(plan.isReady());
        assertTrue(plan.getFailures().contains("blocked gateway session requires provider trace or diagnostic note"));
    }

    private Ali1688GatewaySmokePreparation.FixedSample fixedSample() {
        Ali1688GatewaySmokePreparation.FixedSample sample = new Ali1688GatewaySmokePreparation.FixedSample();
        sample.setCollectionNo("ALI1688-SMOKE-FIXED-20260520");
        sample.setOwnerUserId(10002L);
        sample.setLogicalStoreId(301L);
        sample.setSourcePlatform("amazon");
        sample.setSourceUrl("https://www.amazon.com/dp/B0TESTSMOKE");
        sample.setSourceTitle("Artificial Flowers 6 Stems");
        sample.setSourceImageUrl("https://images.example.com/source.jpg");
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
}
