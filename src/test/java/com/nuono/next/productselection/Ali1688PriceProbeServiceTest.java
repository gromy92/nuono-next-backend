package com.nuono.next.productselection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.Ali1688CollectionMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class Ali1688PriceProbeServiceTest {

    @Mock
    private Ali1688CollectionMapper mapper;

    @Mock
    private ObjectProvider<Ali1688PriceProbeExecutor> executorProvider;

    private Ali1688PriceProbeService service;

    @BeforeEach
    void setUp() {
        service = new Ali1688PriceProbeService(
                mapper,
                new Ali1688CandidateGateService(new ObjectMapper()),
                executorProvider
        );
    }

    @Test
    void requestProbeRejectsCandidateBlockedByGateWithoutSnapshotOrExecutorCall() {
        Ali1688CollectionRecords.CandidateRecord candidate = gateBlockedCandidate();
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.requestProbe(String.valueOf(candidate.id), command(), 307L)
        );

        assertEquals("候选未通过 AI 门禁，不能执行真实价格探针。", error.getMessage());
        verify(executorProvider, never()).getIfAvailable();
        verify(mapper, never()).insertRealPriceSnapshot(any());
    }

    @Test
    void requestProbeRejectsCandidateWithFailedPriceSnapshotBeforeExecutorCall() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectLatestRealPriceSnapshotByCandidate(candidate.id)).thenReturn(failedPriceSnapshot(candidate.id));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.requestProbe(String.valueOf(candidate.id), command(), 307L)
        );

        assertEquals("候选未通过 AI 门禁，不能执行真实价格探针。", error.getMessage());
        verify(executorProvider, never()).getIfAvailable();
        verify(mapper, never()).insertRealPriceSnapshot(any());
    }

    @Test
    void requestProbePersistsSuccessfulPreviewSnapshotSeparatelyFromListPrice() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        CapturingExecutor executor = new CapturingExecutor(Ali1688PriceProbeResult.confirmed(
                "order_preview",
                "颜色: black; 型号: Razr Fold",
                2,
                new BigDecimal("15.23"),
                new BigDecimal("8.00"),
                new BigDecimal("0.00"),
                new BigDecimal("38.46"),
                "CNY",
                new BigDecimal("38.46"),
                BigDecimal.ONE,
                "深圳"
        ));

        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(mapper.nextRealPriceSnapshotId()).thenReturn(91001L);
        when(executorProvider.getIfAvailable()).thenReturn(executor);

        Ali1688RealPriceSnapshot snapshot = service.requestProbe(String.valueOf(candidate.id), command(), 307L);

        assertEquals("confirmed", snapshot.status);
        assertEquals("preview_only", snapshot.safetyMode);
        assertEquals("order_preview", snapshot.source);
        assertEquals(new BigDecimal("15.23"), snapshot.unitPrice);
        assertEquals(new BigDecimal("38.46"), snapshot.totalPrice);
        assertEquals("CNY", snapshot.currency);
        assertEquals("no_payment_no_order_no_message", snapshot.sideEffectPolicy);
        assertEquals("¥ 6 .93 运费4元起 4400+件 50件起批", candidate.priceText);
        assertEquals(88001L, executor.request.candidate.id);
        assertEquals("preview_only", executor.request.safetyMode);

        ArgumentCaptor<Ali1688RealPriceSnapshot> snapshotCaptor = ArgumentCaptor.forClass(Ali1688RealPriceSnapshot.class);
        verify(mapper).insertRealPriceSnapshot(snapshotCaptor.capture());
        Ali1688RealPriceSnapshot persisted = snapshotCaptor.getValue();
        assertEquals(91001L, persisted.id);
        assertEquals(candidate.id, persisted.candidateId);
        assertEquals("颜色: black; 型号: Razr Fold", persisted.skuText);
        assertEquals(2, persisted.quantity);
        assertEquals("preview_only", persisted.safetyMode);
        assertEquals("no_payment_no_order_no_message", persisted.sideEffectPolicy);
    }

    @Test
    void requestProbePersistsDisabledAdapterFailureWhenExecutorIsUnavailable() {
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(mapper.nextRealPriceSnapshotId()).thenReturn(91002L);
        when(executorProvider.getIfAvailable()).thenReturn(null);

        Ali1688RealPriceSnapshot snapshot = service.requestProbe(String.valueOf(candidate.id), command(), 307L);

        assertEquals("failed", snapshot.status);
        assertEquals("preview_failed", snapshot.failureCode);
        assertEquals("真实价格探针执行器未启用。", snapshot.failureMessage);
        assertEquals("preview_only", snapshot.safetyMode);
        verify(mapper).insertRealPriceSnapshot(any());
    }

    @Test
    void requestProbePersistsAllTypedFailureSnapshots() {
        List<String> failureCodes = List.of(
                "login_required",
                "captcha_required",
                "rate_limited",
                "sku_selection_required",
                "stock_unavailable",
                "shipping_unavailable",
                "preview_failed"
        );
        Ali1688CollectionRecords.CandidateRecord candidate = gatePassedCandidate();
        when(mapper.selectCandidateById(candidate.id)).thenReturn(candidate);
        when(mapper.selectTaskById(candidate.taskId)).thenReturn(task());
        when(mapper.nextRealPriceSnapshotId()).thenReturn(91010L, 91011L, 91012L, 91013L, 91014L, 91015L, 91016L);

        for (String failureCode : failureCodes) {
            when(executorProvider.getIfAvailable()).thenReturn(new CapturingExecutor(
                    Ali1688PriceProbeResult.failed(failureCode, "typed failure " + failureCode)
            ));

            Ali1688RealPriceSnapshot snapshot = service.requestProbe(String.valueOf(candidate.id), command(), 307L);

            assertEquals("failed", snapshot.status);
            assertEquals(failureCode, snapshot.failureCode);
            assertEquals("typed failure " + failureCode, snapshot.failureMessage);
            assertEquals("preview_only", snapshot.safetyMode);
        }

        verify(mapper, org.mockito.Mockito.times(failureCodes.size())).insertRealPriceSnapshot(any());
    }

    private Ali1688PriceProbeCommand command() {
        Ali1688PriceProbeCommand command = new Ali1688PriceProbeCommand();
        command.setQuantity(2);
        command.setSkuText("颜色: black; 型号: Razr Fold");
        command.setRegionText("深圳");
        return command;
    }

    private Ali1688CollectionRecords.CandidateRecord gatePassedCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        candidate.scoreStatus = "final";
        candidate.matchScore = 31;
        candidate.specScore = 14;
        candidate.scoreDetailJson = "{\"riskLevel\":\"low\"}";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord gateBlockedCandidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = candidate();
        candidate.scoreStatus = "partial";
        candidate.aiAssessmentStatus = "pending";
        return candidate;
    }

    private Ali1688CollectionRecords.CandidateRecord candidate() {
        Ali1688CollectionRecords.CandidateRecord candidate = new Ali1688CollectionRecords.CandidateRecord();
        candidate.id = 88001L;
        candidate.taskId = 87001L;
        candidate.sourceCollectionId = 86001L;
        candidate.ownerUserId = 307L;
        candidate.logicalStoreId = 301L;
        candidate.offerId = "88201";
        candidate.candidateUrl = "https://detail.1688.com/offer/88201.html";
        candidate.title = "Razr Fold 候选";
        candidate.priceText = "¥ 6 .93 运费4元起 4400+件 50件起批";
        candidate.updatedBy = 307L;
        return candidate;
    }

    private Ali1688CollectionRecords.TaskRecord task() {
        Ali1688CollectionRecords.TaskRecord task = new Ali1688CollectionRecords.TaskRecord();
        task.id = 87001L;
        task.sourceCollectionId = 86001L;
        task.ownerUserId = 307L;
        task.logicalStoreId = 301L;
        task.updatedBy = 307L;
        return task;
    }

    private Ali1688RealPriceSnapshot failedPriceSnapshot(Long candidateId) {
        Ali1688RealPriceSnapshot snapshot = new Ali1688RealPriceSnapshot();
        snapshot.candidateId = candidateId;
        snapshot.status = "failed";
        snapshot.failureCode = "captcha_required";
        snapshot.failureMessage = "1688 要求验证码";
        return snapshot;
    }

    private static class CapturingExecutor implements Ali1688PriceProbeExecutor {
        private final Ali1688PriceProbeResult result;
        private Ali1688PriceProbeRequest request;

        private CapturingExecutor(Ali1688PriceProbeResult result) {
            this.result = result;
        }

        @Override
        public Ali1688PriceProbeResult probe(Ali1688PriceProbeRequest request) {
            this.request = request;
            return result;
        }
    }
}
