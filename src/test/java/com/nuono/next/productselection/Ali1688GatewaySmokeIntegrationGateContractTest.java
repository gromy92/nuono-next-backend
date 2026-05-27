package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688GatewaySmokeIntegrationGateContractTest {

    @Test
    void buildsSystemApiRunPlanForPreparedFixedSample() {
        Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan =
                Ali1688GatewaySmokeIntegrationGate.from(preparedPlan(86006L));

        assertTrue(runPlan.isReady());
        assertTrue(runPlan.getFailures().isEmpty());
        assertEquals("86006", runPlan.getSourceCollectionId());
        assertEquals("POST", runPlan.getTriggerStep().getMethod());
        assertEquals("/api/product-selection/source-collections/86006/ali1688/recollect",
                runPlan.getTriggerStep().getPath());
        assertEquals("GET", runPlan.getDetailReadStep().getMethod());
        assertEquals("/api/product-selection/source-collections/86006/ali1688",
                runPlan.getDetailReadStep().getPath());
        assertEquals("GET", runPlan.getWorkbenchReadStep().getMethod());
        assertEquals("/api/product-selection/ali1688-collections",
                runPlan.getWorkbenchReadStep().getPath());
        assertEquals("LocalDbAli1688CollectionService.processQueuedTasksOnce or enabled scheduler",
                runPlan.getSchedulerStep());
        assertTrue(runPlan.getPostRunDbAssertionSql().contains("procurement_candidate"));
        assertTrue(runPlan.getPostRunDbAssertionSql().contains("procurement_auto_inquiry_event"));
        assertTrue(runPlan.getPostRunDbAssertionSql().contains("procurement_final_candidate"));
        assertFalse(runPlan.getForbiddenPaths().isEmpty());
        assertTrue(runPlan.getForbiddenPaths().contains("Chrome plugin button"));
        assertTrue(runPlan.getForbiddenPaths().contains("manual payload sending"));
    }

    @Test
    void refusesRealRunWithoutFixedSourceCollectionId() {
        Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan =
                Ali1688GatewaySmokeIntegrationGate.from(preparedPlan(null));

        assertFalse(runPlan.isReady());
        assertTrue(runPlan.getFailures().contains("fixed source collection ID is required for real gateway smoke run"));
    }

    @Test
    void carriesTypedGatewayExpectationFromCaptchaBoundary() {
        Ali1688GatewaySmokePreparation.FixedSample sample = fixedSample(86006L);
        Ali1688GatewaySmokePreparation.BrowserGatewayBoundary boundary = readyBoundary();
        boundary.setSessionState("captcha_required");
        boundary.setProviderTraceId("browser-trace-captcha");
        boundary.setDiagnosticNote("1688 showed slider captcha during session check");
        Ali1688GatewaySmokePreparation.Plan preparation =
                Ali1688GatewaySmokePreparation.prepare(sample, boundary);

        Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan =
                Ali1688GatewaySmokeIntegrationGate.from(preparation);

        assertTrue(runPlan.isReady());
        assertEquals("typed_gateway_error", runPlan.getExpectedOutcome());
        assertEquals("captcha_required", runPlan.getExpectedGatewayErrorCode());
    }

    @Test
    void refusesRunWhenPreparationGateHasFailures() {
        Ali1688GatewaySmokePreparation.FixedSample sample = fixedSample(86006L);
        sample.setSourceImageUrl("");

        Ali1688GatewaySmokeIntegrationGate.RunPlan runPlan =
                Ali1688GatewaySmokeIntegrationGate.from(
                        Ali1688GatewaySmokePreparation.prepare(sample, readyBoundary())
                );

        assertFalse(runPlan.isReady());
        assertTrue(runPlan.getFailures().contains("source image URL is required"));
    }

    private Ali1688GatewaySmokePreparation.Plan preparedPlan(Long sourceCollectionId) {
        return Ali1688GatewaySmokePreparation.prepare(fixedSample(sourceCollectionId), readyBoundary());
    }

    private Ali1688GatewaySmokePreparation.FixedSample fixedSample(Long sourceCollectionId) {
        Ali1688GatewaySmokePreparation.FixedSample sample = new Ali1688GatewaySmokePreparation.FixedSample();
        sample.setSourceCollectionId(sourceCollectionId);
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
}
