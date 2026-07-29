package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorProductDetailTakeoverFenceTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private NoonProductDetailAdapter detailAdapter;
    @Mock private CompetitorProductSnapshotService snapshotService;

    @Test
    void leaseLossDuringBatchTakeoverStopsBeforeFirstHttpRequest() {
        CompetitorWatchProductRow watchProduct = new CompetitorWatchProductRow();
        watchProduct.setId(180123L);
        watchProduct.setSiteCode("SA");
        watchProduct.setSelfNoonProductCode("ZSELF001");
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of());
        CompetitorProductDetailRefreshService service =
                new CompetitorProductDetailRefreshService(
                        mapper, detailAdapter, snapshotService, Clock.systemUTC()
                );

        assertThrows(
                CompetitorRefreshLeaseLostException.class,
                () -> service.refreshConfirmedCompetitors(
                        watchProduct, 220123L, 150123L, 601L,
                        () -> {
                            throw new CompetitorRefreshLeaseLostException(
                                    150123L, 220123L
                            );
                        }
                )
        );

        verify(detailAdapter, never()).fetch(any(NoonProductDetailRequest.class));
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(), any(), any(), any(), any()
        );
    }
}
