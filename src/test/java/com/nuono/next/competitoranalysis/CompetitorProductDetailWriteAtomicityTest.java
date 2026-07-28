package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.infrastructure.mapper.CompetitorProductDetailWriteMapper;
import java.lang.reflect.Method;
import java.util.Locale;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class CompetitorProductDetailWriteAtomicityTest {
    @Mock private CompetitorAnalysisMapper mapper;
    @Mock private CompetitorProductSnapshotService snapshotService;
    private CompetitorProductDetailWriteGuard writeGuard;

    @BeforeEach
    void setUp() {
        writeGuard = new CompetitorProductDetailWriteGuard(mapper, snapshotService);
    }

    @Test
    void writeBoundaryIsTransactional() throws Exception {
        Method method = CompetitorProductDetailWriteGuard.class.getMethod(
                "writeIfCurrent",
                CompetitorWatchProductRow.class,
                CompetitorProductRow.class,
                CompetitorProductDetailTarget.class,
                NoonProductDetail.class,
                Long.class,
                Long.class
        );

        assertTrue(method.isAnnotationPresent(Transactional.class));
    }

    @Test
    void competitorWriteLocksCurrentScopeThenUpdatesAndSnapshotsInsideBoundary() {
        CompetitorWatchProductRow expectedWatch = watch("ZSELF001");
        CompetitorWatchProductRow currentWatch = watch("ZSELF001");
        CompetitorProductRow expectedProduct = competitor("ZCOMP001");
        CompetitorProductRow currentProduct = competitor("ZCOMP001");
        CompetitorProductDetailTarget target = CompetitorProductDetailTarget.competitor(
                currentProduct.getId(),
                "ZCOMP001",
                currentProduct.getCanonicalUrl()
        );
        NoonProductDetail detail = detail("ZCOMP001");
        when(mapper.lockWatchProductForDetailWrite(expectedWatch.getId())).thenReturn(currentWatch);
        when(mapper.lockConfirmedCompetitorProductForDetailWrite(
                expectedWatch.getId(),
                expectedProduct.getId()
        )).thenReturn(currentProduct);
        when(mapper.updateCompetitorProductFromDetail(any())).thenReturn(1);

        boolean written = writeGuard.writeIfCurrent(
                expectedWatch,
                expectedProduct,
                target,
                detail,
                220124L,
                601L
        );

        assertTrue(written);
        InOrder order = inOrder(mapper, snapshotService);
        order.verify(mapper).lockWatchProductForDetailWrite(expectedWatch.getId());
        order.verify(mapper).lockConfirmedCompetitorProductForDetailWrite(
                expectedWatch.getId(),
                expectedProduct.getId()
        );
        order.verify(mapper).updateCompetitorProductFromDetail(any());
        order.verify(snapshotService).recordProductDetailSnapshot(
                currentWatch,
                currentProduct,
                detail,
                220124L,
                601L
        );
    }

    @Test
    void competitorChangedBeforeAtomicCommitCannotBeUpdatedOrSnapshotted() {
        CompetitorWatchProductRow watch = watch("ZSELF001");
        CompetitorProductRow product = competitor("ZCOMP001");
        CompetitorProductDetailTarget target = CompetitorProductDetailTarget.competitor(
                product.getId(),
                "ZCOMP001",
                product.getCanonicalUrl()
        );
        when(mapper.lockWatchProductForDetailWrite(watch.getId())).thenReturn(watch);
        when(mapper.lockConfirmedCompetitorProductForDetailWrite(
                watch.getId(),
                product.getId()
        )).thenReturn(null);

        boolean written = writeGuard.writeIfCurrent(
                watch,
                product,
                target,
                detail("ZCOMP001"),
                220124L,
                601L
        );

        assertFalse(written);
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void selfChangedBeforeAtomicCommitCannotBeSnapshotted() {
        CompetitorWatchProductRow expected = watch("ZSELF001");
        when(mapper.lockWatchProductForDetailWrite(expected.getId()))
                .thenReturn(watch("ZSELF999"));

        boolean written = writeGuard.writeIfCurrent(
                expected,
                null,
                CompetitorProductDetailTarget.self("ZSELF001"),
                detail("ZSELF001"),
                220124L,
                601L
        );

        assertFalse(written);
        verify(mapper, never()).updateCompetitorProductFromDetail(any());
        verify(snapshotService, never()).recordProductDetailSnapshot(
                any(),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void persistenceQueriesLockActiveCurrentRows() throws Exception {
        Method watchMethod = CompetitorProductDetailWriteMapper.class.getMethod(
                "lockWatchProductForDetailWrite",
                Long.class
        );
        String watchSql = sql(watchMethod);
        assertTrue(watchSql.contains("STATUS = 'ACTIVE'"));
        assertTrue(watchSql.contains("IS_DELETED = B'0'"));
        assertTrue(watchSql.contains("FOR UPDATE"));

        Method competitorMethod = CompetitorProductDetailWriteMapper.class.getMethod(
                "lockConfirmedCompetitorProductForDetailWrite",
                Long.class,
                Long.class
        );
        String competitorSql = sql(competitorMethod);
        assertTrue(competitorSql.contains("REVIEW_STATUS = 'CONFIRMED'"));
        assertTrue(competitorSql.contains("IS_DELETED = B'0'"));
        assertTrue(competitorSql.contains("FOR UPDATE"));
    }

    private static String sql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private static CompetitorWatchProductRow watch(String selfCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setSelfNoonProductCode(selfCode);
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorProductRow competitor(String code) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(200010L);
        row.setWatchProductId(180123L);
        row.setNoonProductCode(code);
        row.setCanonicalUrl("https://www.noon.com/saudi-en/sample/" + code + "/p/");
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
