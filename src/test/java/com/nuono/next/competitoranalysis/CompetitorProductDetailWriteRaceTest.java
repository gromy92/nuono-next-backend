package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorProductDetailWriteRaceTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private NoonProductDetailAdapter detailAdapter;
    @Mock private CompetitorProductSnapshotService snapshotService;
    private CompetitorProductDetailRefreshService service;

    @BeforeEach
    void setUp() {
        service = new CompetitorProductDetailRefreshService(
                mapper,
                detailAdapter,
                snapshotService,
                Clock.fixed(Instant.parse("2026-07-28T02:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void competitorChangedDuringHttpFetchCannotBeUpdatedOrSnapshotted() {
        CompetitorWatchProductRow watch = watch("ZSELF001");
        CompetitorProductRow competitor = competitor();
        CompetitorProductDetailTarget target = CompetitorProductDetailTarget.competitor(
                competitor.getId(),
                competitor.getNoonProductCode(),
                competitor.getCanonicalUrl()
        );
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(watch.getId()))
                .thenReturn(List.of(competitor));
        when(detailAdapter.fetch(any())).thenReturn(detail("ZCOMP001"));
        when(mapper.lockWatchProductForDetailWrite(watch.getId())).thenReturn(watch);
        when(mapper.lockConfirmedCompetitorProductForDetailWrite(
                watch.getId(),
                competitor.getId()
        )).thenReturn(competitor);
        when(mapper.updateCompetitorProductFromDetail(any())).thenReturn(0);

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watch,
                List.of(target),
                220124L,
                150124L,
                601L
        );

        assertStale(result);
        verify(mapper).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(any(), any(), any(), any(), any());
    }

    @Test
    void selfCodeChangedDuringHttpFetchCannotBeSnapshotted() {
        CompetitorWatchProductRow watch = watch("ZSELF001");
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(watch.getId()))
                .thenReturn(List.of());
        when(detailAdapter.fetch(any())).thenReturn(detail("ZSELF001"));
        when(mapper.lockWatchProductForDetailWrite(watch.getId()))
                .thenReturn(watch("ZSELF999"));

        CompetitorProductDetailRefreshResult result = service.refreshTargets(
                watch,
                List.of(CompetitorProductDetailTarget.self("ZSELF001")),
                220124L,
                150124L,
                601L
        );

        assertStale(result);
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(any(), any(), any(), any(), any());
    }

    private static void assertStale(CompetitorProductDetailRefreshResult result) {
        assertEquals(1, result.getAttemptedCount());
        assertEquals(0, result.getSucceededCount());
        assertEquals(1, result.getFailedCount());
        assertEquals("DETAIL_TARGET_STALE", result.getFirstErrorCode());
    }

    private static CompetitorWatchProductRow watch(String selfCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode(selfCode);
        return row;
    }

    private static CompetitorProductRow competitor() {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200010L);
        row.setWatchProductId(180123L);
        row.setNoonProductCode("ZCOMP001");
        row.setCanonicalUrl("https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
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
