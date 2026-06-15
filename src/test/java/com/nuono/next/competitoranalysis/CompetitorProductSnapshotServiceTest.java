package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.competitoranalysis.noon.NoonSearchPage;
import com.nuono.next.competitoranalysis.noon.NoonSearchResult;
import com.nuono.next.infrastructure.mapper.CompetitorProductSnapshotMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
}
