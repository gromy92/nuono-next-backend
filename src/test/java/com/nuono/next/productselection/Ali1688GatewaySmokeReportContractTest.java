package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Ali1688GatewaySmokeReportContractTest {

    @Test
    void smokeReportExposesSnapshotContract() {
        Class<?> reportClass = assertDoesNotThrow(
                () -> Class.forName("com.nuono.next.productselection.Ali1688GatewaySmokeReport")
        );
        Class<?> snapshotClass = assertDoesNotThrow(
                () -> Class.forName("com.nuono.next.productselection.Ali1688GatewaySmokeReport$Snapshot")
        );

        Method fromSnapshot = assertDoesNotThrow(() -> reportClass.getMethod("from", snapshotClass));

        assertNotNull(fromSnapshot);
    }

    @Test
    void requiredSideEffectDeltasIncludeProcurementInquirySessionAndMessageEventTables() {
        List<String> requiredNames = Ali1688GatewaySmokeReport.requiredDownstreamSideEffectDeltaNames();

        assertTrue(requiredNames.contains("procurement_auto_inquiry_task"));
        assertTrue(requiredNames.contains("procurement_auto_inquiry_session"));
        assertTrue(requiredNames.contains("procurement_auto_inquiry_event"));
    }

    @Test
    void successSmokePassesWhenCandidatesRanksSnapshotAndNoProcurementSideEffectsArePresent() {
        Object report = buildReport(successSnapshot());

        assertTrue((Boolean) invoke(report, "isPassed"));
        assertEquals("success", invoke(report, "getOutcome"));
        assertEquals("cleanup", invoke(report, "getCleanupDecision"));
        assertTrue(castList(invoke(report, "getFailures")).isEmpty());
        assertEquals(0L, castMap(invoke(report, "getDownstreamSideEffectDeltas")).get("procurement_candidate"));

        Map<String, Object> output = castMap(invoke(report, "getOutputRecord"));
        assertEquals(86001L, output.get("sourceCollectionId"));
        assertEquals(87001L, output.get("taskId"));
        assertEquals("success", output.get("status"));
        assertEquals(3, output.get("candidateCount"));
        assertEquals(3, output.get("selectedRankCount"));
        assertEquals("cleanup", output.get("cleanupDecision"));
    }

    @Test
    void successSmokeFailsWhenProcurementSideEffectsWereCreated() {
        Object snapshot = successSnapshot();
        Map<String, Long> deltas = sideEffectDeltas();
        deltas.put("procurement_candidate", 1L);
        invoke(snapshot, "setDownstreamSideEffectDeltas", new Class<?>[]{Map.class}, deltas);

        Object report = buildReport(snapshot);

        assertFalse((Boolean) invoke(report, "isPassed"));
        assertTrue(castList(invoke(report, "getFailures"))
                .contains("downstream side effect delta must be 0: procurement_candidate=1"));
    }

    @Test
    void typedGatewayErrorPassesWithoutCandidatesWhenItIsDocumentedAndHasNoSideEffects() {
        Object snapshot = newSnapshot();
        invoke(snapshot, "setSourceCollectionId", new Class<?>[]{Long.class}, 86001L);
        invoke(snapshot, "setTaskId", new Class<?>[]{Long.class}, 87002L);
        invoke(snapshot, "setStatus", new Class<?>[]{String.class}, "failed");
        invoke(snapshot, "setGatewayErrorCode", new Class<?>[]{String.class}, "captcha_required");
        invoke(snapshot, "setGatewayServiceKind", new Class<?>[]{String.class}, "system_browser_gateway");
        invoke(snapshot, "setCandidateCount", new Class<?>[]{Integer.class}, 0);
        invoke(snapshot, "setSelectedRankCount", new Class<?>[]{Integer.class}, 0);
        invoke(snapshot, "setProviderTraceId", new Class<?>[]{String.class}, "browser-trace-captcha");
        invoke(snapshot, "setDiagnosticNote", new Class<?>[]{String.class}, "1688 returned CAPTCHA boundary");
        invoke(snapshot, "setDownstreamSideEffectDeltas", new Class<?>[]{Map.class}, sideEffectDeltas());

        Object report = buildReport(snapshot);

        assertTrue((Boolean) invoke(report, "isPassed"));
        assertEquals("typed_gateway_error", invoke(report, "getOutcome"));
        assertTrue(castList(invoke(report, "getFailures")).isEmpty());
        assertEquals("captcha_required", castMap(invoke(report, "getOutputRecord")).get("gatewayErrorCode"));
    }

    @Test
    void smokeFailsWhenItUsesPluginBridgeOrManualPayloadInsteadOfSystemGateway() {
        Object snapshot = successSnapshot();
        invoke(snapshot, "setGatewayServiceKind", new Class<?>[]{String.class}, "local_smoke_bridge");
        invoke(snapshot, "setUsedPluginBridge", new Class<?>[]{boolean.class}, true);
        invoke(snapshot, "setUsedManualPayload", new Class<?>[]{boolean.class}, true);

        Object report = buildReport(snapshot);

        assertFalse((Boolean) invoke(report, "isPassed"));
        List<String> failures = castList(invoke(report, "getFailures"));
        assertTrue(failures.contains("gateway service kind must be system_browser_gateway"));
        assertTrue(failures.contains("system smoke must not use Chrome plugin bridge"));
        assertTrue(failures.contains("system smoke must not use manual payload sending"));
    }

    @Test
    void successSmokeFailsWithoutRawSnapshotTraceOrDiagnosticNote() {
        Object snapshot = successSnapshot();
        invoke(snapshot, "setRawSnapshotPresent", new Class<?>[]{boolean.class}, false);
        invoke(snapshot, "setProviderTraceId", new Class<?>[]{String.class}, "");
        invoke(snapshot, "setDiagnosticNote", new Class<?>[]{String.class}, "");

        Object report = buildReport(snapshot);

        assertFalse((Boolean) invoke(report, "isPassed"));
        assertTrue(castList(invoke(report, "getFailures"))
                .contains("raw gateway snapshot, provider trace, or diagnostic note is required"));
    }

    @Test
    void successSmokeFailsWhenCandidateCountExceedsGatewayLimit() {
        Object snapshot = successSnapshot();
        invoke(snapshot, "setCandidateCount", new Class<?>[]{Integer.class}, 11);

        Object report = buildReport(snapshot);

        assertFalse((Boolean) invoke(report, "isPassed"));
        assertTrue(castList(invoke(report, "getFailures"))
                .contains("success or partial_success candidate count must be between 1 and 10"));
    }

    private Object successSnapshot() {
        Object snapshot = newSnapshot();
        invoke(snapshot, "setSourceCollectionId", new Class<?>[]{Long.class}, 86001L);
        invoke(snapshot, "setTaskId", new Class<?>[]{Long.class}, 87001L);
        invoke(snapshot, "setStatus", new Class<?>[]{String.class}, "success");
        invoke(snapshot, "setGatewayServiceKind", new Class<?>[]{String.class}, "system_browser_gateway");
        invoke(snapshot, "setCandidateCount", new Class<?>[]{Integer.class}, 3);
        invoke(snapshot, "setSelectedRankCount", new Class<?>[]{Integer.class}, 3);
        invoke(snapshot, "setRawSnapshotPresent", new Class<?>[]{boolean.class}, true);
        invoke(snapshot, "setDownstreamSideEffectDeltas", new Class<?>[]{Map.class}, sideEffectDeltas());
        return snapshot;
    }

    private Map<String, Long> sideEffectDeltas() {
        Map<String, Long> deltas = new LinkedHashMap<>();
        deltas.put("procurement_candidate", 0L);
        deltas.put("procurement_order", 0L);
        deltas.put("procurement_demand_item", 0L);
        deltas.put("procurement_auto_inquiry_task", 0L);
        deltas.put("procurement_auto_inquiry_session", 0L);
        deltas.put("procurement_auto_inquiry_event", 0L);
        deltas.put("procurement_candidate_pool", 0L);
        deltas.put("procurement_candidate_pool_item", 0L);
        deltas.put("procurement_final_candidate", 0L);
        return deltas;
    }

    private Object newSnapshot() {
        Class<?> snapshotClass = assertDoesNotThrow(
                () -> Class.forName("com.nuono.next.productselection.Ali1688GatewaySmokeReport$Snapshot")
        );
        return assertDoesNotThrow(() -> snapshotClass.getDeclaredConstructor().newInstance());
    }

    private Object buildReport(Object snapshot) {
        Class<?> reportClass = assertDoesNotThrow(
                () -> Class.forName("com.nuono.next.productselection.Ali1688GatewaySmokeReport")
        );
        Method fromSnapshot = assertDoesNotThrow(() -> reportClass.getMethod("from", snapshot.getClass()));
        return assertDoesNotThrow(() -> fromSnapshot.invoke(null, snapshot));
    }

    private Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        Method method = assertDoesNotThrow(() -> target.getClass().getMethod(methodName, parameterTypes));
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Cannot access method " + methodName, e);
        } catch (InvocationTargetException e) {
            throw new AssertionError("Method " + methodName + " threw an exception", e.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castList(Object value) {
        return (List<String>) value;
    }

    @SuppressWarnings("unchecked")
    private <T> Map<String, T> castMap(Object value) {
        return (Map<String, T>) value;
    }
}
