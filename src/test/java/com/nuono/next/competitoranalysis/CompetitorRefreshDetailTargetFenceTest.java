package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorRefreshDetailTargetFenceTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorProductSnapshotService snapshotService;

    @Test
    void competitorUpdateCasMissIsExplicitTargetStaleAndNeverSnapshots() {
        CompetitorWatchProductRow watch = watchProduct("ZSELF001");
        CompetitorProductRow product = competitorProduct("ZCOMP001");
        when(mapper.lockWatchProductForDetailWrite(180001L)).thenReturn(watch);
        when(mapper.lockConfirmedCompetitorProductForDetailWrite(
                180001L, 200001L
        )).thenReturn(product);
        when(mapper.updateCompetitorProductFromDetail(any())).thenReturn(0);
        CompetitorProductDetailWriteGuard writeGuard =
                new CompetitorProductDetailWriteGuard(
                        mapper,
                        snapshotService,
                        CompetitorRefreshLeaseGuard.disabled(mapper)
                );

        CompetitorDetailTargetStaleException exception = assertThrows(
                CompetitorDetailTargetStaleException.class,
                () -> writeGuard.write(
                        150001L,
                        220001L,
                        watch,
                        product,
                        productUpdate(),
                        detail("ZCOMP001"),
                        501L
                )
        );

        assertEquals(
                "DETAIL_TARGET_STALE",
                CompetitorDetailTargetStaleException.ERROR_CODE
        );
        assertEquals("详情写入前目标已发生变化。", exception.getMessage());
        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void changedSelfCodeNeverWritesSnapshot() {
        CompetitorWatchProductRow expected = watchProduct("ZSELF001");
        when(mapper.lockWatchProductForDetailWrite(180001L))
                .thenReturn(watchProduct("ZSELF999"));
        CompetitorProductDetailWriteGuard writeGuard =
                new CompetitorProductDetailWriteGuard(
                        mapper,
                        snapshotService,
                        CompetitorRefreshLeaseGuard.disabled(mapper)
                );

        assertThrows(
                CompetitorDetailTargetStaleException.class,
                () -> writeGuard.write(
                        150001L,
                        220001L,
                        expected,
                        null,
                        null,
                        detail("ZSELF001"),
                        501L
                )
        );

        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(), any(), any(), any(), any()
        );
    }

    private static CompetitorWatchProductRow watchProduct(String selfCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180001L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode(selfCode);
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorProductRow competitorProduct(String code) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200001L);
        row.setWatchProductId(180001L);
        row.setNoonProductCode(code);
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static CompetitorProductInsertCommand productUpdate() {
        CompetitorProductInsertCommand command = new CompetitorProductInsertCommand();
        command.setId(200001L);
        command.setWatchProductId(180001L);
        command.setNoonProductCode("ZCOMP001");
        return command;
    }

    private static NoonProductDetail detail(String code) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        return detail;
    }
}
