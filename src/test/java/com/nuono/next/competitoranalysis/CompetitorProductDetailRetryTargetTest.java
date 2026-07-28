package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorProductDetailRetryTargetTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private NoonProductDetailAdapter detailAdapter;
    @Mock private CompetitorProductSnapshotService snapshotService;
    private CompetitorProductDetailRefreshService service;

    @BeforeEach
    void setUp() {
        service = new CompetitorProductDetailRefreshService(
                mapper, detailAdapter, snapshotService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(mapper.lockWatchProductForDetailWrite(180123L)).thenReturn(watchProduct());
        org.mockito.Mockito.lenient().when(mapper.updateCompetitorProductFromDetail(any())).thenReturn(1);
    }

    @Test
    void retriesOnlyFailedDetailTargetsWithoutRefetchingSuccessfulTargets() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductRow confirmed = confirmedProduct();
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(confirmed));
        when(mapper.lockConfirmedCompetitorProductForDetailWrite(180123L, 200010L))
                .thenReturn(confirmed);
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class)))
                .thenReturn(detail("ZSELF001"))
                .thenThrow(new NoonSearchProviderException(
                        "PROVIDER_UNAVAILABLE",
                        "Noon detail timed out.",
                        null,
                        null,
                        null
                ))
                .thenReturn(detail("ZCOMP001"));

        CompetitorProductDetailRefreshResult initial =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);
        CompetitorProductDetailRefreshResult retry = service.refreshTargets(
                watchProduct,
                initial.getRetryTargets(),
                220124L,
                150124L,
                601L
        );

        assertEquals(2, initial.getAttemptedCount());
        assertEquals(2, initial.getRequestAttemptCount());
        assertEquals(1, initial.getSucceededCount());
        assertEquals(1, initial.getFailedCount());
        assertEquals("ZSELF001", initial.getSucceededTargets().get(0).getNoonProductCode());
        assertEquals(1, initial.getRetryTargets().size());
        assertEquals(200010L, initial.getRetryTargets().get(0).getCompetitorProductId());
        assertEquals("ZCOMP001", initial.getRetryTargets().get(0).getNoonProductCode());
        assertEquals(1, retry.getAttemptedCount());
        assertEquals(1, retry.getRequestAttemptCount());
        assertEquals(1, retry.getSucceededCount());
        assertEquals(0, retry.getFailedCount());

        ArgumentCaptor<NoonProductDetailRequest> requests =
                ArgumentCaptor.forClass(NoonProductDetailRequest.class);
        verify(detailAdapter, times(3)).fetch(requests.capture());
        assertEquals("ZSELF001", requests.getAllValues().get(0).getNoonProductCode());
        assertEquals("ZCOMP001", requests.getAllValues().get(1).getNoonProductCode());
        assertEquals("ZCOMP001", requests.getAllValues().get(2).getNoonProductCode());
    }

    @Test
    void preservesEveryTargetWhenDetailAdapterIsUnavailable() {
        service = new CompetitorProductDetailRefreshService(
                mapper,
                null,
                snapshotService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(confirmedProduct()));

        CompetitorProductDetailRefreshResult result =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);
        CompetitorProductDetailRefreshResult retry = service.refreshTargets(
                watchProduct,
                List.of(result.getRetryTargets().get(1)),
                220124L,
                150124L,
                601L
        );

        assertEquals(2, result.getAttemptedCount());
        assertEquals(0, result.getRequestAttemptCount());
        assertEquals(2, result.getFailedCount());
        assertEquals("DETAIL_ADAPTER_UNAVAILABLE", result.getFirstErrorCode());
        assertEquals("ZSELF001", result.getRetryTargets().get(0).getNoonProductCode());
        assertEquals("ZCOMP001", result.getRetryTargets().get(1).getNoonProductCode());
        assertEquals(1, retry.getAttemptedCount());
        assertEquals(0, retry.getRequestAttemptCount());
        assertEquals(1, retry.getFailedCount());
        verify(snapshotService, never()).recordProductDetailSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    void canceledOrDeletedCompetitorBecomesTerminalStaleWithoutFetchingOrWriting() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductDetailTarget staleTarget =
                CompetitorProductDetailTarget.competitor(
                        200010L,
                        "ZCOMP001",
                        "https://www.noon.com/saudi-en/sample/ZCOMP001/p/"
                );
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of());

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watchProduct,
                List.of(staleTarget),
                220124L,
                150124L,
                601L
        );

        assertTerminalStale(result);
        assertNoDetailFetchOrWrite();
    }

    @Test
    void competitorCodeChangedForSameIdBecomesTerminalStaleWithoutFetchingOrWriting() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductDetailTarget oldTarget =
                CompetitorProductDetailTarget.competitor(
                        200010L,
                        "ZCOMP001",
                        "https://www.noon.com/saudi-en/sample/ZCOMP001/p/"
                );
        CompetitorProductRow current = confirmedProduct();
        current.setNoonProductCode("ZCOMP999");
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(current));

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watchProduct,
                List.of(oldTarget),
                220124L,
                150124L,
                601L
        );

        assertTerminalStale(result);
        assertNoDetailFetchOrWrite();
    }

    @Test
    void changedSelfCodeDoesNotLetOldRetryTargetFetchOrSucceedSilently() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        watchProduct.setSelfNoonProductCode("ZSELF999");
        CompetitorProductDetailTarget oldSelf =
                CompetitorProductDetailTarget.self("ZSELF001");
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of());

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watchProduct,
                List.of(oldSelf),
                220124L,
                150124L,
                601L
        );

        assertTerminalStale(result);
        assertNoDetailFetchOrWrite();
    }

    @Test
    void malformedRetryTargetIsTerminalFailureInsteadOfSilentSuccess() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductDetailTarget malformed = new CompetitorProductDetailTarget();
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of());

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watchProduct,
                List.of(malformed),
                220124L,
                150124L,
                601L
        );

        assertTerminalStale(result);
        assertNoDetailFetchOrWrite();
    }

    @Test
    void riskFailureDoesNotDeferAnAlreadyStaleFollowingTarget() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductDetailTarget stale = CompetitorProductDetailTarget.competitor(
                200010L,
                "ZCOMP001",
                null
        );
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of());
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class)))
                .thenThrow(new NoonSearchProviderException(
                        "RATE_LIMITED",
                        "risk hold",
                        429,
                        null,
                        null
                ));

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watchProduct,
                List.of(CompetitorProductDetailTarget.self("ZSELF001"), stale),
                220124L,
                150124L,
                601L
        );

        assertEquals(2, result.getFailedCount());
        assertEquals(1, result.getRequestAttemptCount());
        assertEquals(0, result.getDeferredCount());
        assertEquals("DETAIL_TARGET_STALE", result.getFailures().get(1).getErrorCode());
    }

    private void assertTerminalStale(CompetitorProductDetailRefreshResult result) {
        assertEquals(1, result.getAttemptedCount());
        assertEquals(0, result.getRequestAttemptCount());
        assertEquals(0, result.getSucceededCount());
        assertEquals(1, result.getFailedCount());
        assertEquals("DETAIL_TARGET_STALE", result.getFirstErrorCode());
        assertEquals("DETAIL_TARGET_STALE", result.getFailures().get(0).getErrorCode());
    }

    private void assertNoDetailFetchOrWrite() {
        verify(detailAdapter, never()).fetch(any(NoonProductDetailRequest.class));
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    private static CompetitorWatchProductRow watchProduct() {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode("ZSELF001");
        return row;
    }

    private static CompetitorProductRow confirmedProduct() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200010L);
        row.setWatchProductId(180123L);
        row.setNoonProductCode("ZCOMP001");
        row.setCodeType("Z_CODE");
        row.setCanonicalUrl("https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static NoonProductDetail detail(String code) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType("Z_CODE");
        detail.setDetailUrl("https://www.noon.com/saudi-en/sample/" + code + "/p/");
        detail.setTitleEn("Detail title");
        detail.setSnapshotHash("detail-hash-" + code);
        return detail;
    }
}
