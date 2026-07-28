package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonProductDetail;
import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorProductSnapshotMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@ResourceLock("default-time-zone")
class CompetitorProductSnapshotServiceTest {

    @Mock
    private CompetitorProductSnapshotMapper mapper;

    @Test
    void recordsDailySnapshotAndPriceChangeEventFromSearchResult() {
        CompetitorProductSnapshotService service = new CompetitorProductSnapshotService(mapper);
        when(mapper.nextProductSnapshotId()).thenReturn(260123L);
        when(mapper.nextProductChangeEventId()).thenReturn(270123L);
        when(mapper.selectPreviousSnapshot(180123L, "COMPETITOR", "N51360862A", LocalDate.parse("2026-06-08")))
                .thenReturn(previousSnapshot());
        Map<String, NoonSearchResult> resultsByCode = new LinkedHashMap<>();
        resultsByCode.put("N51360862A", result("N51360862A", "Storage Box", "89.90"));

        int changed = service.recordSearchSnapshots(
                context(),
                page(),
                resultsByCode,
                Map.of("N51360862A", 200020L)
        );

        assertEquals(1, changed);
        ArgumentCaptor<CompetitorProductSnapshotCommand> snapshotCaptor =
                ArgumentCaptor.forClass(CompetitorProductSnapshotCommand.class);
        verify(mapper).insertProductSnapshot(snapshotCaptor.capture());
        assertEquals(260123L, snapshotCaptor.getValue().getId());
        assertEquals("COMPETITOR", snapshotCaptor.getValue().getSubjectType());
        assertEquals("N51360862A", snapshotCaptor.getValue().getNoonProductCode());
        assertEquals(new BigDecimal("89.90"), snapshotCaptor.getValue().getPriceAmount());

        ArgumentCaptor<CompetitorProductChangeEventCommand> eventCaptor =
                ArgumentCaptor.forClass(CompetitorProductChangeEventCommand.class);
        verify(mapper).insertProductChangeEvent(eventCaptor.capture());
        assertEquals(270123L, eventCaptor.getValue().getId());
        assertEquals(260123L, eventCaptor.getValue().getSnapshotId());
        assertEquals(260001L, eventCaptor.getValue().getPreviousSnapshotId());
        assertEquals("price", eventCaptor.getValue().getFieldKey());
        assertEquals("99.90", eventCaptor.getValue().getOldValueJson());
        assertEquals("89.90", eventCaptor.getValue().getNewValueJson());
        assertEquals("WARNING", eventCaptor.getValue().getSeverity());
        verify(mapper).softDeleteChangeEventsBySnapshotId(260123L, 601L);
    }

    @Test
    void recordsSelfProductDetailSnapshotWithoutCompetitorProductReference() {
        CompetitorProductSnapshotService service = new CompetitorProductSnapshotService(mapper);
        when(mapper.nextProductSnapshotId()).thenReturn(260124L);

        int changed = service.recordProductDetailSnapshot(
                context().getWatchProduct(),
                null,
                selfDetail(),
                220123L,
                601L
        );

        assertEquals(0, changed);
        ArgumentCaptor<CompetitorProductSnapshotCommand> snapshotCaptor =
                ArgumentCaptor.forClass(CompetitorProductSnapshotCommand.class);
        verify(mapper).insertProductSnapshot(snapshotCaptor.capture());
        assertEquals(260124L, snapshotCaptor.getValue().getId());
        assertEquals("SELF", snapshotCaptor.getValue().getSubjectType());
        assertNull(snapshotCaptor.getValue().getCompetitorProductId());
        assertEquals("ZSELF001", snapshotCaptor.getValue().getNoonProductCode());
        assertEquals(new BigDecimal("35.50"), snapshotCaptor.getValue().getPriceAmount());
        verify(mapper).selectPreviousSnapshot(180123L, "SELF", "ZSELF001", LocalDate.parse("2026-06-08"));
    }

    @Test
    void assignsShanghaiMidnightSnapshotToTheNewBusinessDate() {
        CompetitorProductSnapshotService service = new CompetitorProductSnapshotService(mapper);
        when(mapper.nextProductSnapshotId()).thenReturn(260125L);
        NoonProductDetail detail = selfDetail();
        detail.setCapturedAt(LocalDateTime.parse("2026-07-27T00:00:00"));

        service.recordProductDetailSnapshot(
                context().getWatchProduct(),
                null,
                detail,
                220123L,
                601L
        );

        ArgumentCaptor<CompetitorProductSnapshotCommand> snapshotCaptor =
                ArgumentCaptor.forClass(CompetitorProductSnapshotCommand.class);
        verify(mapper).insertProductSnapshot(snapshotCaptor.capture());
        assertEquals(LocalDate.parse("2026-07-27"), snapshotCaptor.getValue().getFactDate());
        assertEquals(LocalDateTime.parse("2026-07-27T00:00:00"), snapshotCaptor.getValue().getCapturedAt());
    }

    @Test
    void fillsMissingCapturedAtInShanghaiWhenJvmDefaultIsUtc() {
        TimeZone previousZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            CompetitorProductSnapshotService service = new CompetitorProductSnapshotService(mapper);
            when(mapper.nextProductSnapshotId()).thenReturn(260126L);
            NoonProductDetail detail = selfDetail();
            detail.setCapturedAt(null);
            LocalDateTime before = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).minusSeconds(1);

            service.recordProductDetailSnapshot(
                    context().getWatchProduct(),
                    null,
                    detail,
                    220123L,
                    601L
            );

            LocalDateTime after = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusSeconds(1);
            ArgumentCaptor<CompetitorProductSnapshotCommand> snapshotCaptor =
                    ArgumentCaptor.forClass(CompetitorProductSnapshotCommand.class);
            verify(mapper).insertProductSnapshot(snapshotCaptor.capture());
            LocalDateTime capturedAt = snapshotCaptor.getValue().getCapturedAt();
            assertEquals(capturedAt.toLocalDate(), snapshotCaptor.getValue().getFactDate());
            org.junit.jupiter.api.Assertions.assertFalse(capturedAt.isBefore(before));
            org.junit.jupiter.api.Assertions.assertFalse(capturedAt.isAfter(after));
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }

    private static CompetitorKeywordRefreshContext context() {
        CompetitorWatchProductRow watchProduct = new CompetitorWatchProductRow();
        watchProduct.setId(180123L);
        watchProduct.setOwnerUserId(501L);
        watchProduct.setSiteCode("SA");
        watchProduct.setSelfNoonProductCode("ZSELF001");
        return CompetitorKeywordRefreshContext.builder()
                .searchRunId(220123L)
                .keywordRunId(230123L)
                .watchProduct(watchProduct)
                .actorUserId(601L)
                .build();
    }

    private static NoonSearchPage page() {
        NoonSearchPage page = new NoonSearchPage();
        page.setCapturedAt(LocalDateTime.parse("2026-06-08T08:00:00"));
        page.setSourceUrl("https://www.noon.com/saudi-en/search?q=storage");
        return page;
    }

    private static NoonSearchResult result(String code, String title, String price) {
        NoonSearchResult result = new NoonSearchResult();
        result.setNoonProductCode(code);
        result.setCodeType("N_CODE");
        result.setCanonicalUrl("https://www.noon.com/saudi-en/item/" + code + "/p/");
        result.setTitle(title);
        result.setBrand("Qili");
        result.setImageUrl("https://f.nooncdn.com/p/" + code + ".jpg?width=240");
        result.setPriceAmount(new BigDecimal(price));
        result.setCurrencyCode("SAR");
        result.setRawResultJson("{\"sku\":\"" + code + "\"}");
        return result;
    }

    private static CompetitorProductSnapshotRow previousSnapshot() {
        CompetitorProductSnapshotRow row = new CompetitorProductSnapshotRow();
        row.setId(260001L);
        row.setTitleEn("Storage Box");
        row.setBrand("Qili");
        row.setPriceAmount(new BigDecimal("99.90"));
        row.setCurrencyCode("SAR");
        row.setMainImageAssetKey("N51360862A.jpg");
        return row;
    }

    private static NoonProductDetail selfDetail() {
        NoonProductDetail detail = new NoonProductDetail();
        detail.setNoonProductCode("ZSELF001");
        detail.setCodeType("Z_CODE");
        detail.setDetailUrl("https://www.noon.com/saudi-en/self/ZSELF001/p/");
        detail.setTitleEn("Self basket");
        detail.setBrand("Papersay");
        detail.setPriceAmount(new BigDecimal("35.50"));
        detail.setCurrencyCode("SAR");
        detail.setMainImageUrlRaw("https://f.nooncdn.com/p/ZSELF001.jpg?width=240");
        detail.setMainImageUrlNormalized("https://f.nooncdn.com/p/ZSELF001.jpg?width=240");
        detail.setRawDetailJson("{\"sku\":\"ZSELF001\"}");
        detail.setCapturedAt(LocalDateTime.parse("2026-06-08T08:00:00"));
        return detail;
    }
}
