package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailAdapter;
import com.nuono.next.competitoranalysis.noon.NoonProductDetailRequest;
import com.nuono.next.competitoranalysis.noon.NoonSearchProviderException;
import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitorProductDetailRefreshServiceTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    @Mock
    private NoonProductDetailAdapter detailAdapter;

    @Mock
    private CompetitorProductSnapshotService snapshotService;

    private CompetitorProductDetailRefreshService service;

    @BeforeEach
    void setUp() {
        service = new CompetitorProductDetailRefreshService(
                mapper,
                detailAdapter,
                snapshotService,
                Clock.fixed(Instant.parse("2026-06-06T08:00:00Z"), ZoneOffset.UTC)
        );
        org.mockito.Mockito.lenient().when(mapper.selectWatchProductForRefresh(180123L))
                .thenReturn(watchProduct());
        org.mockito.Mockito.lenient().when(mapper.updateCompetitorProductFromDetail(any()))
                .thenReturn(1);
    }

    @Test
    void refreshesSelfAndConfirmedCompetitorDetailsOncePerCode() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductRow confirmed = confirmedProduct(200010L, "zcomp001", "https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(
                        confirmed,
                        confirmedProduct(200011L, "ZCOMP001", "https://www.noon.com/saudi-en/duplicate/ZCOMP001/p/"),
                        confirmedProduct(200012L, "ZSELF001", "https://www.noon.com/saudi-en/self/ZSELF001/p/")
                ));
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class))).thenAnswer((invocation) -> {
            NoonProductDetailRequest request = invocation.getArgument(0);
            return detail(request.getNoonProductCode());
        });

        CompetitorProductDetailRefreshResult result =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);

        assertEquals(2, result.getAttemptedCount());
        assertEquals(2, result.getSucceededCount());
        assertEquals(0, result.getFailedCount());
        ArgumentCaptor<NoonProductDetailRequest> requestCaptor =
                ArgumentCaptor.forClass(NoonProductDetailRequest.class);
        verify(detailAdapter, times(2)).fetch(requestCaptor.capture());
        assertEquals("SA", requestCaptor.getAllValues().get(0).getSiteCode());
        assertEquals("en-SA", requestCaptor.getAllValues().get(0).getLocale());
        assertEquals("ZSELF001", requestCaptor.getAllValues().get(0).getNoonProductCode());
        assertEquals("ZCOMP001", requestCaptor.getAllValues().get(1).getNoonProductCode());
        assertEquals("https://www.noon.com/saudi-en/sample/ZCOMP001/p/", requestCaptor.getAllValues().get(1).getCanonicalUrl());

        verify(mapper).updateCompetitorProductFromDetail(argThat((command) ->
                Long.valueOf(200010L).equals(command.getId())
                        && "ZCOMP001".equals(command.getNoonProductCode())
                        && "PRODUCT_DETAIL".equals(command.getSourceType())
                        && "Detail title".equals(command.getTitleSnapshot())
                        && new BigDecimal("12.34").compareTo(command.getPriceAmountSnapshot()) == 0
        ));
        verify(snapshotService).recordProductDetailSnapshot(
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.isNull(),
                argThat((detail) -> "ZSELF001".equals(detail.getNoonProductCode())),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(601L)
        );
        verify(snapshotService).recordProductDetailSnapshot(
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.eq(confirmed),
                argThat((detail) -> "ZCOMP001".equals(detail.getNoonProductCode())),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(601L)
        );
    }

    @Test
    void fillsMissingCapturedAtWithShanghaiBusinessTime() {
        service = new CompetitorProductDetailRefreshService(
                mapper,
                detailAdapter,
                snapshotService,
                Clock.fixed(Instant.parse("2026-07-26T18:00:00Z"), ZoneOffset.UTC)
        );
        CompetitorWatchProductRow watchProduct = watchProduct();
        NoonProductDetail detail = detail("ZSELF001");
        detail.setCapturedAt(null);
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class))).thenReturn(detail);
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L)).thenReturn(List.of());

        CompetitorProductDetailRefreshResult result =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);

        assertEquals(1, result.getSucceededCount());
        ArgumentCaptor<NoonProductDetail> detailCaptor = ArgumentCaptor.forClass(NoonProductDetail.class);
        verify(snapshotService).recordProductDetailSnapshot(
                org.mockito.ArgumentMatchers.eq(watchProduct),
                org.mockito.ArgumentMatchers.isNull(),
                detailCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(220123L),
                org.mockito.ArgumentMatchers.eq(601L)
        );
        assertEquals(
                LocalDateTime.parse("2026-07-27T02:00:00"),
                detailCaptor.getValue().getCapturedAt()
        );
    }

    @Test
    void doesNotRecordSearchDiscoveryAsDetailWhenDetailFetchFails() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        CompetitorProductRow confirmed = confirmedProduct(200010L, "ZCOMP001", "https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
        confirmed.setTitleSnapshot("Search title");
        confirmed.setBrandSnapshot("Search brand");
        confirmed.setImageUrlSnapshot("https://f.nooncdn.com/p/search.jpg");
        confirmed.setPriceAmountSnapshot(new BigDecimal("22.34"));
        confirmed.setCurrencyCodeSnapshot("SAR");
        confirmed.setRatingSnapshot(new BigDecimal("4.30"));
        confirmed.setReviewCountSnapshot(88);
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L)).thenReturn(List.of(confirmed));
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class)))
                .thenThrow(new IllegalStateException("Noon catalog returned empty product list"));

        CompetitorProductDetailRefreshResult result =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);

        assertEquals(2, result.getAttemptedCount());
        assertEquals(0, result.getSucceededCount());
        assertEquals(2, result.getFailedCount());
        assertEquals("DETAIL_REFRESH_FAILED", result.getFirstErrorCode());
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
    void stopsRequestingMoreDetailsAfterRiskFailureEvenWhenAnEarlierFailureWasOrdinary() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(
                        confirmedProduct(200010L, "ZCOMP001", "https://www.noon.com/saudi-en/sample/ZCOMP001/p/"),
                        confirmedProduct(200011L, "ZCOMP002", "https://www.noon.com/saudi-en/sample/ZCOMP002/p/")
                ));
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class)))
                .thenThrow(new IllegalStateException("Noon detail parse failed"))
                .thenThrow(new NoonSearchProviderException(
                        "RATE_LIMITED",
                        "Noon 前台商品详情返回 HTTP 429。",
                        429,
                        null,
                        null
                ));

        CompetitorProductDetailRefreshResult result =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);

        assertEquals(2, result.getAttemptedCount());
        assertEquals(0, result.getSucceededCount());
        assertEquals(2, result.getFailedCount());
        assertEquals(1, result.getDeferredCount());
        assertEquals(2, result.getFailedTargets().size());
        assertEquals("ZSELF001", result.getFailedTargets().get(0).getNoonProductCode());
        assertEquals("ZCOMP001", result.getFailedTargets().get(1).getNoonProductCode());
        assertEquals(1, result.getDeferredTargets().size());
        assertEquals("ZCOMP002", result.getDeferredTargets().get(0).getNoonProductCode());
        assertEquals(3, result.getRetryTargets().size());
        verify(detailAdapter, times(2)).fetch(any(NoonProductDetailRequest.class));
    }

    @Test
    void defersAllCompetitorsWithoutRequestingThemWhenSelfDetailHitsRiskFailure() {
        CompetitorWatchProductRow watchProduct = watchProduct();
        when(mapper.listConfirmedCompetitorProductsByWatchProductId(180123L))
                .thenReturn(List.of(
                        confirmedProduct(200010L, "ZCOMP001", "https://www.noon.com/saudi-en/sample/ZCOMP001/p/"),
                        confirmedProduct(200011L, "ZCOMP002", "https://www.noon.com/saudi-en/sample/ZCOMP002/p/")
                ));
        when(detailAdapter.fetch(any(NoonProductDetailRequest.class)))
                .thenThrow(new NoonSearchProviderException(
                        "BLOCKED_BY_RISK_CONTROL",
                        "Noon 前台商品详情触发风控。",
                        403,
                        null,
                        null
                ));

        CompetitorProductDetailRefreshResult result =
                service.refreshConfirmedCompetitors(watchProduct, 220123L, 150123L, 601L);

        assertEquals(1, result.getAttemptedCount());
        assertEquals(1, result.getFailedCount());
        assertEquals(2, result.getDeferredCount());
        assertEquals("ZCOMP001", result.getDeferredTargets().get(0).getNoonProductCode());
        assertEquals("ZCOMP002", result.getDeferredTargets().get(1).getNoonProductCode());
        assertEquals(3, result.getRetryTargets().size());
        verify(detailAdapter, times(1)).fetch(any(NoonProductDetailRequest.class));
        verify(mapper).listConfirmedCompetitorProductsByWatchProductId(180123L);
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

    private static CompetitorProductRow confirmedProduct(Long id, String code, String canonicalUrl) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setNoonProductCode(code);
        row.setCodeType(code.startsWith("Z") || code.startsWith("z") ? "Z_CODE" : "N_CODE");
        row.setCanonicalUrl(canonicalUrl);
        row.setReviewStatus("CONFIRMED");
        return row;
    }

    private static NoonProductDetail detail(String code) {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode(code);
        detail.setCodeType("Z_CODE");
        detail.setDetailUrl("https://www.noon.com/saudi-en/sample/ZCOMP001/p/");
        detail.setTitleEn("Detail title");
        detail.setBrand("Detail brand");
        detail.setSellerName("Detail seller");
        detail.setPriceAmount(new BigDecimal("12.34"));
        detail.setCurrencyCode("SAR");
        detail.setRating(new BigDecimal("4.60"));
        detail.setReviewCount(321);
        detail.setMainImageUrlRaw("https://f.nooncdn.com/p/detail.jpg");
        detail.setMainImageUrlNormalized("https://f.nooncdn.com/p/detail.jpg");
        detail.setAvailabilityStatus("IN_STOCK");
        detail.setSnapshotHash("detail-hash");
        detail.setRawDetailJson("{\"sku\":\"ZCOMP001\"}");
        detail.setCapturedAt(LocalDateTime.parse("2026-06-06T08:00:00"));
        return detail;
    }
}
