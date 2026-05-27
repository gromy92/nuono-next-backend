package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class Ali1688PriceProbeExecutorTest {

    @Test
    void executorRejectsNonSystemOrNonGatePassedRequestsBeforePreviewClientCall() {
        Ali1688PricePreviewClient client = Mockito.mock(Ali1688PricePreviewClient.class);
        Ali1688ControlledPriceProbeExecutor executor = executor(client);

        Ali1688PriceProbeResult unsafe = executor.probe(new Ali1688PriceProbeRequest(
                task(),
                gatePassedCandidate(),
                2,
                "颜色:black",
                "深圳",
                "direct_pay",
                307L
        ));
        Ali1688PriceProbeResult blocked = executor.probe(new Ali1688PriceProbeRequest(
                task(),
                gateBlockedCandidate(),
                2,
                "颜色:black",
                "深圳",
                "preview_only",
                307L
        ));

        assertEquals("failed", unsafe.status);
        assertEquals("preview_failed", unsafe.failureCode);
        assertEquals("failed", blocked.status);
        assertEquals("preview_failed", blocked.failureCode);
        verify(client, never()).previewOrder(Mockito.any());
    }

    @Test
    void executorReturnsSafePreviewSuccessWithoutCredentialMaterial() {
        Ali1688PricePreviewClient client = Mockito.mock(Ali1688PricePreviewClient.class);
        Ali1688PriceProbeResult clientResult = Ali1688PriceProbeResult.confirmed(
                "order_preview",
                "颜色:black",
                2,
                new BigDecimal("15.23"),
                new BigDecimal("8.00"),
                BigDecimal.ZERO,
                new BigDecimal("38.46"),
                "CNY",
                new BigDecimal("38.46"),
                BigDecimal.ONE,
                "深圳"
        );
        clientResult.rawSnapshotJson = "{\"cookie\":\"secret\",\"token\":\"secret\",\"total\":\"38.46\"}";
        when(client.previewOrder(Mockito.any())).thenReturn(clientResult);
        Ali1688ControlledPriceProbeExecutor executor = executor(client);

        Ali1688PriceProbeResult result = executor.probe(systemRequest());

        assertEquals("confirmed", result.status);
        assertEquals("preview_only", result.safetyMode);
        assertEquals("no_payment_no_order_no_message", result.sideEffectPolicy);
        assertNull(result.rawSnapshotJson);
        verify(client).previewOrder(Mockito.any());
        verify(client, never()).clickPayment(Mockito.any());
        verify(client, never()).submitPurchase(Mockito.any());
        verify(client, never()).sendSupplierMessage(Mockito.any(), Mockito.any());
    }

    @Test
    void executorPreservesTypedFailureOutcomesWithPreviewOnlySafety() {
        List<String> failureCodes = List.of(
                "login_required",
                "captcha_required",
                "rate_limited",
                "sku_selection_required",
                "stock_unavailable",
                "shipping_unavailable",
                "preview_failed"
        );

        for (String failureCode : failureCodes) {
            Ali1688PricePreviewClient client = Mockito.mock(Ali1688PricePreviewClient.class);
            when(client.previewOrder(Mockito.any())).thenReturn(Ali1688PriceProbeResult.failed(failureCode, "typed " + failureCode));
            Ali1688ControlledPriceProbeExecutor executor = executor(client);

            Ali1688PriceProbeResult result = executor.probe(systemRequest());

            assertEquals("failed", result.status);
            assertEquals(failureCode, result.failureCode);
            assertEquals("typed " + failureCode, result.failureMessage);
            assertEquals("preview_only", result.safetyMode);
            assertEquals("no_payment_no_order_no_message", result.sideEffectPolicy);
        }
    }

    @Test
    void executorReturnsDisabledFailureWhenPreviewClientIsUnavailable() {
        ObjectProvider<Ali1688PricePreviewClient> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        Ali1688ControlledPriceProbeExecutor executor = new Ali1688ControlledPriceProbeExecutor(provider);

        Ali1688PriceProbeResult result = executor.probe(systemRequest());

        assertEquals("failed", result.status);
        assertEquals("preview_failed", result.failureCode);
        assertEquals("真实价格预览客户端未启用。", result.failureMessage);
        assertEquals("preview_only", result.safetyMode);
    }

    private Ali1688ControlledPriceProbeExecutor executor(Ali1688PricePreviewClient client) {
        ObjectProvider<Ali1688PricePreviewClient> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return new Ali1688ControlledPriceProbeExecutor(provider);
    }

    private Ali1688PriceProbeRequest systemRequest() {
        return new Ali1688PriceProbeRequest(
                task(),
                gatePassedCandidate(),
                2,
                "颜色:black",
                "深圳",
                "preview_only",
                307L
        );
    }

    private Ali1688CollectionRecords.TaskRecord task() {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.updatedBy = 307L;
        return task;
    }

    private Ali1688CollectionRecords.CandidateRecord gatePassedCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88001L;
        candidate.taskId = 87001L;
        candidate.scoreStatus = "final";
        candidate.matchScore = 31;
        candidate.specScore = 14;
        candidate.scoreDetailJson = "{\"riskLevel\":\"low\"}";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord gateBlockedCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        candidate.scoreStatus = "partial";
        candidate.matchScore = null;
        candidate.specScore = null;
        return candidate;
    }
}
