package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CompetitorCorrectionWriterFenceOrderingTest {
    @BeforeEach
    void openTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void keywordRefreshLocksFenceBeforeAllocatingOrWriting() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        when(mapper.lockCompetitorCorrectionWriterFence()).thenReturn("OPEN");
        when(mapper.nextKeywordRunId()).thenReturn(230001L);
        CompetitorKeywordRefreshTransactionRunner runner =
                new CompetitorKeywordRefreshTransactionRunner(
                        mapper,
                        ignored -> CompetitorKeywordRefreshOutcome.success(0),
                        CompetitorRefreshLeaseGuard.disabled(mapper),
                        new CompetitorCorrectionWriterFenceGuard(mapper)
                );

        runner.runKeyword(
                150001L, 220001L, watchProduct(), keyword(), 501L
        );

        assertFirstMapperCallIsFence(mapper);
        InOrder order = inOrder(mapper);
        order.verify(mapper).lockCompetitorCorrectionWriterFence();
        order.verify(mapper).nextKeywordRunId();
        order.verify(mapper).insertKeywordRun(any());
    }

    @Test
    void detailSnapshotLocksFenceBeforeLeaseOrSnapshotMutation() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        CompetitorProductSnapshotService snapshots =
                mock(CompetitorProductSnapshotService.class);
        when(mapper.lockCompetitorCorrectionWriterFence()).thenReturn("OPEN");
        when(mapper.lockWatchProductForDetailWrite(180001L))
                .thenReturn(watchProduct());
        CompetitorProductDetailWriteGuard guard =
                new CompetitorProductDetailWriteGuard(
                        mapper,
                        snapshots,
                        CompetitorRefreshLeaseGuard.disabled(mapper),
                        new CompetitorCorrectionWriterFenceGuard(mapper)
                );

        guard.write(
                150001L,
                220001L,
                watchProduct(),
                null,
                null,
                detail("NSELF0001"),
                501L
        );

        assertFirstMapperCallIsFence(mapper);
        InOrder order = inOrder(mapper, snapshots);
        order.verify(mapper).lockCompetitorCorrectionWriterFence();
        order.verify(mapper).lockWatchProductForDetailWrite(180001L);
        order.verify(snapshots).recordProductDetailSnapshot(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void browserRankLocksFenceBeforeLockingRunOrWritingRank() {
        CompetitorAnalysisMapper mapper = browserMapper();
        CompetitorAnalysisService service = service(mapper);

        service.applyBrowserObservations(
                context(), 190001L, sponsoredObservation()
        );

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectKeywordScopeById(190001L);
        order.verify(mapper).selectLatestSucceededKeywordRunByKeywordId(190001L);
        order.verify(mapper).lockCompetitorCorrectionWriterFence();
        order.verify(mapper).lockKeywordRunForBrowserObservation(230001L);
        order.verify(mapper).insertRankFact(any());
    }

    @Test
    void bothIndependentBrowserEntrypointsFailBeforeMutationWhenActive() {
        CompetitorAnalysisMapper mapper = browserMapper();
        when(mapper.lockCompetitorCorrectionWriterFence())
                .thenReturn("ACTIVE");
        when(mapper.selectWatchProductById(501L, 180001L))
                .thenReturn(watchProduct());
        when(mapper.selectKeywordByNorm(180001L, "laundry basket"))
                .thenReturn(keyword());
        CompetitorAnalysisService service = service(mapper);

        assertThrows(
                CompetitorCorrectionMaintenanceException.class,
                () -> service.applyBrowserObservations(
                        context(), 190001L, sponsoredObservation()
                )
        );
        assertThrows(
                CompetitorCorrectionMaintenanceException.class,
                () -> service.applyBrowserObservationsByKeyword(
                        context(), 180001L, sponsoredObservation()
                )
        );

        verify(mapper, times(2)).lockCompetitorCorrectionWriterFence();
        verify(mapper, never()).insertRankFact(any());
        verify(mapper, never()).insertSearchResult(any());
    }

    private static CompetitorAnalysisService service(
            CompetitorAnalysisMapper mapper
    ) {
        CompetitorAnalysisService service = new CompetitorAnalysisService(
                mapper,
                null,
                Clock.system(ZoneId.of("Asia/Shanghai"))
        );
        service.setCorrectionFenceGuard(
                new CompetitorCorrectionWriterFenceGuard(mapper)
        );
        return service;
    }

    private static CompetitorAnalysisMapper browserMapper() {
        CompetitorAnalysisMapper mapper = mock(CompetitorAnalysisMapper.class);
        when(mapper.lockCompetitorCorrectionWriterFence()).thenReturn("OPEN");
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope());
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L))
                .thenReturn(keywordRun());
        when(mapper.lockKeywordRunForBrowserObservation(230001L))
                .thenReturn(230001L);
        when(mapper.selectWatchProductForRefresh(180001L))
                .thenReturn(watchProduct());
        when(mapper.selectCompetitorProductByCode(180001L, "NCOMP0001"))
                .thenReturn(confirmedProduct());
        when(mapper.selectNextSearchResultPosition(230001L)).thenReturn(1);
        when(mapper.nextSearchResultId()).thenReturn(240001L);
        when(mapper.nextKeywordProductId()).thenReturn(245001L);
        when(mapper.selectRankFactId(
                230001L, "COMPETITOR", "NCOMP0001", "SPONSORED"
        )).thenReturn(null);
        when(mapper.nextRankFactId()).thenReturn(250001L);
        when(mapper.insertRankFact(any())).thenReturn(1);
        return mapper;
    }

    private static void assertFirstMapperCallIsFence(
            CompetitorAnalysisMapper mapper
    ) {
        String method = Mockito.mockingDetails(mapper)
                .getInvocations()
                .iterator()
                .next()
                .getMethod()
                .getName();
        assertEquals("lockCompetitorCorrectionWriterFence", method);
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("NSELF0001");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRow keyword() {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(190001L);
        row.setWatchProductId(180001L);
        row.setKeyword("laundry basket");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordScopeRow keywordScope() {
        CompetitorKeywordScopeRow row = new CompetitorKeywordScopeRow();
        row.setKeywordId(190001L);
        row.setWatchProductId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordRunRow keywordRun() {
        CompetitorKeywordRunRow row = new CompetitorKeywordRunRow();
        row.setId(230001L);
        row.setSearchRunId(220001L);
        row.setKeywordId(190001L);
        row.setRequestedResultLimit(200);
        row.setCapturedAt(
                LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
                        .minusMinutes(1)
                        .withNano(0)
        );
        return row;
    }

    private static CompetitorBrowserObservationCommand sponsoredObservation() {
        CompetitorBrowserObservationItem item =
                new CompetitorBrowserObservationItem();
        item.setNoonProductCode("NCOMP0001");
        item.setPosition(1);
        item.setSponsored(true);
        CompetitorBrowserObservationCommand command =
                new CompetitorBrowserObservationCommand();
        command.setItems(List.of(item));
        command.setKeyword("laundry basket");
        return command;
    }

    private static CompetitorProductRow confirmedProduct() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200001L);
        row.setWatchProductId(180001L);
        row.setNoonProductCode("NCOMP0001");
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static NoonProductDetail detail(String code) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        return detail;
    }

    private static BusinessAccessContext context() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .build();
    }
}
