package com.nuono.next.intransit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.InTransitSuperSearchMapper;
import com.nuono.next.intransit.InTransitSuperSearchCommands.InTransitSuperSearchQuery;
import com.nuono.next.intransit.InTransitSuperSearchRecords.SuperSearchItemRow;
import com.nuono.next.intransit.InTransitSuperSearchRecords.SuperSearchView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InTransitSuperSearchServiceTest {

    @Mock
    private InTransitSuperSearchMapper mapper;

    private InTransitSuperSearchService service;

    @BeforeEach
    void setUp() {
        service = new InTransitSuperSearchService(mapper);
    }

    @Test
    void shouldSearchActiveInTransitBatchesByProductKeyword() {
        InTransitSuperSearchQuery query = new InTransitSuperSearchQuery();
        query.setOwnerUserId(10002L);
        query.setKeyword("  PAPERSAYSB036  ");
        query.setProjectCode(" PRJ108065 ");

        SuperSearchItemRow row = new SuperSearchItemRow();
        row.setPsku("PAPERSAYSB036");
        row.setProductName("PHOMEMO无墨打印机M08F");
        row.setProductTitle("Phomemo Inkless Printer M08F");
        row.setProductTitleCn("PHOMEMO无墨打印机M08F");
        row.setProductImageUrl("https://img.example/phomemo.jpg");
        row.setBatchId(53001L);
        row.setBatchReferenceNo("XGGEKSA04077");
        row.setRawForwarderName("启客");
        row.setTransportMode("AIR");
        row.setBatchStatus("in_transit");
        row.setTargetStoreCode("RUH");
        row.setTargetSiteCode("SA");
        row.setTargetWarehouseName("FBN-RUH");
        row.setSourceCreatedAt(LocalDateTime.parse("2026-06-09T10:33:00"));
        row.setDomesticReceivedAt(LocalDateTime.parse("2026-06-12T16:42:28"));
        row.setLatestNodeHappenedAt(LocalDateTime.parse("2026-06-12T16:42:28"));
        row.setLatestNodeStatus("handed_to_forwarder");
        row.setLatestNodeDescription("国内收货");
        row.setEtaDate(LocalDate.parse("2026-06-18"));
        row.setBoxCount(2);
        row.setShippedQuantityTotal(16);
        row.setReceivedQuantityTotal(0);
        row.setRemainingQuantityTotal(16);

        when(mapper.searchLineMatchedInTransitProducts(query)).thenReturn(List.of(row));
        when(mapper.searchTitleMatchedInTransitProducts(query)).thenReturn(List.of());

        SuperSearchView result = service.search(query);

        assertEquals("PAPERSAYSB036", result.getKeyword());
        assertEquals(false, result.isIncludeHistory());
        assertEquals(1, result.getTotalCount());
        assertEquals("XGGEKSA04077", result.getItems().get(0).getBatchReferenceNo());
        assertEquals("PHOMEMO无墨打印机M08F", result.getItems().get(0).getProductTitleCn());
        assertEquals(LocalDateTime.parse("2026-06-12T16:42:28"), result.getItems().get(0).getDomesticReceivedAt());

        ArgumentCaptor<InTransitSuperSearchQuery> captor = ArgumentCaptor.forClass(InTransitSuperSearchQuery.class);
        verify(mapper).searchLineMatchedInTransitProducts(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("PAPERSAYSB036", captor.getValue().getKeyword());
        assertEquals("PRJ108065", captor.getValue().getProjectCode());
        assertEquals(false, captor.getValue().isIncludeHistory());
        assertEquals(20, captor.getValue().getLimit());
        verify(mapper).searchTitleMatchedInTransitProducts(captor.getValue());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void shouldMergeLineAndProductTitleMatchesForOneKeyword() {
        InTransitSuperSearchQuery query = new InTransitSuperSearchQuery();
        query.setOwnerUserId(10002L);
        query.setKeyword("Printer");

        SuperSearchItemRow lineRow = new SuperSearchItemRow();
        lineRow.setPsku("PAPERSAYSB036");
        lineRow.setBatchId(53001L);
        lineRow.setBatchReferenceNo("XGGEKSA04077");
        lineRow.setSourceCreatedAt(LocalDateTime.parse("2026-06-09T10:33:00"));

        SuperSearchItemRow titleRow = new SuperSearchItemRow();
        titleRow.setPsku("THERMALPAPER001");
        titleRow.setBatchId(53002L);
        titleRow.setBatchReferenceNo("XGGEKSA04078");
        titleRow.setSourceCreatedAt(LocalDateTime.parse("2026-06-15T11:56:00"));

        SuperSearchItemRow duplicateTitleRow = new SuperSearchItemRow();
        duplicateTitleRow.setPsku("PAPERSAYSB036");
        duplicateTitleRow.setBatchId(53001L);
        duplicateTitleRow.setBatchReferenceNo("XGGEKSA04077");
        duplicateTitleRow.setSourceCreatedAt(LocalDateTime.parse("2026-06-09T10:33:00"));

        when(mapper.searchLineMatchedInTransitProducts(query)).thenReturn(List.of(lineRow));
        when(mapper.searchTitleMatchedInTransitProducts(query)).thenReturn(List.of(titleRow, duplicateTitleRow));

        SuperSearchView result = service.search(query);

        assertEquals(2, result.getTotalCount());
        assertEquals("XGGEKSA04078", result.getItems().get(0).getBatchReferenceNo());
        assertEquals("XGGEKSA04077", result.getItems().get(1).getBatchReferenceNo());
        verify(mapper).searchLineMatchedInTransitProducts(query);
        verify(mapper).searchTitleMatchedInTransitProducts(query);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void shouldFallBackToProductTitleSearchWhenLineKeywordHasNoMatch() {
        InTransitSuperSearchQuery query = new InTransitSuperSearchQuery();
        query.setOwnerUserId(10002L);
        query.setKeyword("Inkless Printer");

        SuperSearchItemRow row = new SuperSearchItemRow();
        row.setPsku("PAPERSAYSB036");
        row.setProductTitle("Phomemo Inkless Printer M08F");
        row.setBatchId(53001L);
        row.setBatchReferenceNo("XGGEKSA04077");

        when(mapper.searchLineMatchedInTransitProducts(query)).thenReturn(List.of());
        when(mapper.searchTitleMatchedInTransitProducts(query)).thenReturn(List.of(row));

        SuperSearchView result = service.search(query);

        assertEquals(1, result.getTotalCount());
        assertEquals("XGGEKSA04077", result.getItems().get(0).getBatchReferenceNo());
        verify(mapper).searchLineMatchedInTransitProducts(query);
        verify(mapper).searchTitleMatchedInTransitProducts(query);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void shouldUseLegacySearchFallbackWhenLineAndTitleFastPathsHaveNoMatch() {
        InTransitSuperSearchQuery query = new InTransitSuperSearchQuery();
        query.setOwnerUserId(10002L);
        query.setKeyword("rare keyword");

        SuperSearchItemRow row = new SuperSearchItemRow();
        row.setPsku("PAPERSAYSB036");
        row.setProductTitle("Rare title");
        row.setBatchId(53001L);
        row.setBatchReferenceNo("XGGEKSA04077");

        when(mapper.searchLineMatchedInTransitProducts(query)).thenReturn(List.of());
        when(mapper.searchTitleMatchedInTransitProducts(query)).thenReturn(List.of());
        when(mapper.searchInTransitProducts(query)).thenReturn(List.of(row));

        SuperSearchView result = service.search(query);

        assertEquals(1, result.getTotalCount());
        assertEquals("XGGEKSA04077", result.getItems().get(0).getBatchReferenceNo());
        verify(mapper).searchLineMatchedInTransitProducts(query);
        verify(mapper).searchTitleMatchedInTransitProducts(query);
        verify(mapper).searchInTransitProducts(query);
    }

    @Test
    void shouldReturnEmptyViewForBlankKeywordWithoutQueryingMapper() {
        InTransitSuperSearchQuery query = new InTransitSuperSearchQuery();
        query.setOwnerUserId(10002L);
        query.setKeyword("  ");

        SuperSearchView result = service.search(query);

        assertEquals("", result.getKeyword());
        assertEquals(0, result.getTotalCount());
        assertEquals(List.of(), result.getItems());
        verifyNoInteractions(mapper);
    }
}
