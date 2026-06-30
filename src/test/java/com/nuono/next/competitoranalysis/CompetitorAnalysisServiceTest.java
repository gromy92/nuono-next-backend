package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.CompetitorAnalysisMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccountType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisServiceTest {

    @Mock
    private CompetitorAnalysisMapper mapper;

    private CompetitorAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new CompetitorAnalysisService(mapper);
    }

    @Test
    void createWatchProductUsesProductSiteOfferPskuAsSelfNoonCode() {
        CompetitorWatchProductCreateCommand command = createCommand(" z6122basketsa ");
        CompetitorProductOptionRow option = productOption("Z6122BASKETSA");
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(option);
        when(mapper.nextWatchProductId()).thenReturn(180123L);
        when(mapper.insertWatchProduct(any())).thenReturn(1);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.listKeywordsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180123L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180123L, view.getWatchProduct().getId());
        assertEquals("Z6122BASKETSA", view.getWatchProduct().getSelfNoonProductCode());
        ArgumentCaptor<CompetitorWatchProductInsertCommand> captor =
                ArgumentCaptor.forClass(CompetitorWatchProductInsertCommand.class);
        verify(mapper).insertWatchProduct(captor.capture());
        assertEquals("Z6122BASKETSA", captor.getValue().getSelfNoonProductCode());
        assertEquals("Z_CODE", captor.getValue().getSelfCodeType());
    }

    @Test
    void createWatchProductRejectsForgedSelfNoonCodeFromFrontend() {
        CompetitorWatchProductCreateCommand command = createCommand("N51004211A");
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(productOption("Z6122BASKETSA"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createWatchProduct(operatorContext(), command)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("COMPETITOR_SELF_CODE_MISMATCH", error.getReason());
        verify(mapper, never()).insertWatchProduct(any());
    }

    @Test
    void createWatchProductRejectsProductWithoutNoonZNCode() {
        CompetitorWatchProductCreateCommand command = createCommand(null);
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(productOption("ASIN-BASKET-001"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.createWatchProduct(operatorContext(), command)
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertEquals("COMPETITOR_SELF_CODE_REQUIRED", error.getReason());
        verify(mapper, never()).insertWatchProduct(any());
    }

    @Test
    void createWatchProductReturnsExistingWatchProductForSameBusinessIdentity() {
        CompetitorWatchProductCreateCommand command = createCommand("Z6122BASKETSA");
        CompetitorProductOptionRow option = productOption("Z6122BASKETSA");
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(option);
        when(mapper.selectWatchProductByBusinessKey(
                501L,
                "STR108065-NSA",
                "SA",
                "BASKET-SA-001-BLUE",
                "Z6122BASKETSA"
        ))
                .thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.listKeywordsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180123L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180123L, view.getWatchProduct().getId());
        verify(mapper).updateWatchProductCurrentBinding(180123L, option, 601L);
        verify(mapper, never()).nextWatchProductId();
        verify(mapper, never()).insertWatchProduct(any());
    }

    @Test
    void createWatchProductReturnsExistingWatchProductForSameBusinessIdentityWhenOfferIdChanged() {
        CompetitorWatchProductCreateCommand command = createCommand("Z6122BASKETSA");
        CompetitorProductOptionRow option = productOption("Z6122BASKETSA");
        option.setProductSiteOfferId(91002L);
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(option);
        when(mapper.selectWatchProductByBusinessKey(
                501L,
                "STR108065-NSA",
                "SA",
                "BASKET-SA-001-BLUE",
                "Z6122BASKETSA"
        )).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.listKeywordsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180123L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180123L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180123L, view.getWatchProduct().getId());
        verify(mapper).updateWatchProductCurrentBinding(180123L, option, 601L);
        verify(mapper, never()).selectWatchProductByProductSiteOfferId(any(), any(), any(), any());
        verify(mapper, never()).nextWatchProductId();
        verify(mapper, never()).insertWatchProduct(any());
    }

    @Test
    void productOptionsNormalizeScopeAndKeepOnlyNoonZNProducts() {
        when(mapper.listProductOptions(501L, "STR108065-NSA", "SA", "basket", 50))
                .thenReturn(List.of(productOption("ASIN-BASKET-001"), productOption("N51004211A")));

        List<CompetitorProductOptionView> options = service.productOptions(
                operatorContext(),
                " str108065-nsa ",
                " sa ",
                " basket ",
                500
        );

        assertEquals(1, options.size());
        assertEquals("N51004211A", options.get(0).getNoonProductCode());
        assertEquals("N_CODE", options.get(0).getCodeType());
    }

    @Test
    void listWatchProductsUsesSessionStoreScopeWhenStoreFilterMissing() {
        CompetitorWatchProductQuery query = CompetitorWatchProductQuery.fromRequest(
                null,
                null,
                " basket ",
                " laundry basket ",
                " Z6122 ",
                " active ",
                2,
                10
        );
        CompetitorWatchProductListRow row = new CompetitorWatchProductListRow();
        row.setId(180123L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode("Z6122BASKETSA");
        row.setTitleSnapshot("Foldable Laundry Basket With Bamboo Handles");
        row.setActiveKeywordCount(3);
        row.setActiveKeywordSummary("laundry basket||foldable hamper||bamboo laundry basket");
        row.setPendingCandidateCount(2);
        row.setConfirmedCompetitorCount(1);
        when(mapper.countWatchProducts(501L, List.of("STR108065-NSA"), query)).thenReturn(21L);
        when(mapper.listWatchProducts(501L, List.of("STR108065-NSA"), query)).thenReturn(List.of(row));

        CompetitorWatchProductListView view = service.listWatchProducts(operatorContext(), query);

        assertEquals(21L, view.getPagination().getTotal());
        assertEquals(2, view.getPagination().getPage());
        assertEquals(10, view.getPagination().getPageSize());
        assertEquals(1, view.getItems().size());
        assertEquals(3, view.getItems().get(0).getActiveKeywordCount());
        assertEquals(List.of("laundry basket", "foldable hamper", "bamboo laundry basket"), view.getItems().get(0).getActiveKeywords());
        verify(mapper).listWatchProducts(501L, List.of("STR108065-NSA"), query);
    }

    @Test
    void listProductBaselinesUsesSelectedStoreScopeAndKeepsUnmonitoredRows() {
        CompetitorWatchProductQuery query = CompetitorWatchProductQuery.fromRequest(
                "STR108065-NSA",
                "SA",
                "basket",
                null,
                null,
                null,
                1,
                50
        );
        CompetitorWatchProductListRow row = new CompetitorWatchProductListRow();
        row.setProductSiteOfferId(91001L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode("Z6122BASKETSA");
        row.setTitleSnapshot("Foldable Laundry Basket With Bamboo Handles");
        row.setActiveKeywordCount(2);
        row.setActiveKeywordSummary("laundry basket||foldable hamper");
        row.setPendingCandidateCount(0);
        row.setConfirmedCompetitorCount(0);
        when(mapper.countProductBaselines(501L, "STR108065-NSA", "SA", query)).thenReturn(409L);
        when(mapper.listProductBaselines(501L, "STR108065-NSA", "SA", query)).thenReturn(List.of(row));

        CompetitorWatchProductListView view = service.listProductBaselines(operatorContext(), query);

        assertEquals(409L, view.getPagination().getTotal());
        assertEquals(1, view.getItems().size());
        assertEquals(91001L, view.getItems().get(0).getProductSiteOfferId());
        assertEquals(2, view.getItems().get(0).getActiveKeywordCount());
        assertEquals(List.of("laundry basket", "foldable hamper"), view.getItems().get(0).getActiveKeywords());
        verify(mapper).listProductBaselines(501L, "STR108065-NSA", "SA", query);
    }

    @Test
    void listWatchProductsRejectsExplicitStoreOutsideSessionScope() {
        CompetitorWatchProductQuery query = CompetitorWatchProductQuery.fromRequest(
                "STR-OTHER-NSA",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.listWatchProducts(operatorContext(), query)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("COMPETITOR_STORE_SCOPE_REQUIRED", error.getReason());
        verify(mapper, never()).countWatchProducts(any(), any(), any());
    }

    @Test
    void rankHistoryReturnsRowsForKeywordInsideWatchProductScope() {
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        CompetitorLatestRankPointRow point = rankPoint(190001L, "SELF", "Z6122BASKETSA", 4);
        when(mapper.listRankHistoryByWatchProductIdAndKeywordId(
                eq(180123L),
                eq(190001L),
                any(LocalDateTime.class),
                eq(1000)
        )).thenReturn(List.of(point));

        CompetitorRankHistoryView view = service.rankHistory(operatorContext(), 180123L, 190001L, 999);

        assertEquals(1, view.getItems().size());
        assertEquals("Z6122BASKETSA", view.getItems().get(0).getNoonProductCode());
        verify(mapper).listRankHistoryByWatchProductIdAndKeywordId(
                eq(180123L),
                eq(190001L),
                any(LocalDateTime.class),
                eq(1000)
        );
    }

    @Test
    void rankHistoryRejectsKeywordOutsideWatchProduct() {
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180999L));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.rankHistory(operatorContext(), 180123L, 190001L, 30)
        );

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals("COMPETITOR_KEYWORD_SCOPE_MISMATCH", error.getReason());
        verify(mapper, never()).listRankHistoryByWatchProductIdAndKeywordId(any(), any(), any(), any());
    }

    @Test
    void browserObservationMarksLatestKeywordRunResultAsSponsored() {
        CompetitorBrowserObservationCommand command = browserObservationCommand(
                browserObservationItem("ZF47007A9D75977AB9A83Z", 1, "QiLi 30 Pcs Wooden Black HB Pencils")
        );
        CompetitorProductRow product = competitorProduct(200020L, "ZF47007A9D75977AB9A83Z", "PENDING");
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L)).thenReturn(keywordRun(230017L, 220013L));
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectCompetitorProductByCode(180123L, "ZF47007A9D75977AB9A83Z")).thenReturn(product);
        when(mapper.markSearchResultSponsored(230017L, "ZF47007A9D75977AB9A83Z", 601L)).thenReturn(1);
        when(mapper.nextKeywordProductId()).thenReturn(210020L);

        CompetitorBrowserObservationResultView result = service.applyBrowserObservations(
                operatorContext(),
                190001L,
                command
        );

        assertEquals(1, result.getSponsoredObservedCount());
        assertEquals(1, result.getSearchResultUpdatedCount());
        assertEquals(0, result.getSearchResultInsertedCount());
        assertEquals(0, result.getCompetitorInsertedCount());
        ArgumentCaptor<CompetitorKeywordProductSearchCommand> relationCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordProductSearchCommand.class);
        verify(mapper).upsertKeywordProductRelationFromSearch(relationCaptor.capture());
        assertEquals(190001L, relationCaptor.getValue().getKeywordId());
        assertEquals(200020L, relationCaptor.getValue().getCompetitorProductId());
        assertEquals("DISCOVERED", relationCaptor.getValue().getRelationStatus());
        assertEquals(220013L, relationCaptor.getValue().getSearchRunId());
        assertEquals(1, relationCaptor.getValue().getRankNo());
        assertEquals(Boolean.TRUE, relationCaptor.getValue().getSponsored());
        verify(mapper).markRankFactSponsored(230017L, "ZF47007A9D75977AB9A83Z", 601L);
    }

    @Test
    void browserObservationInsertsSponsoredCandidateWhenCatalogDidNotContainTheAdCard() {
        CompetitorBrowserObservationCommand command = browserObservationCommand(
                browserObservationItem("N51360862A", 2, "Translucent Frosted Back Protective Case")
        );
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        when(mapper.selectLatestSucceededKeywordRunByKeywordId(190001L)).thenReturn(keywordRun(230017L, 220013L));
        when(mapper.selectWatchProductForRefresh(180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectCompetitorProductByCode(180123L, "N51360862A")).thenReturn(null);
        when(mapper.nextCompetitorProductId()).thenReturn(200021L);
        when(mapper.markSearchResultSponsored(230017L, "N51360862A", 601L)).thenReturn(0);
        when(mapper.nextSearchResultId()).thenReturn(240021L);
        when(mapper.nextKeywordProductId()).thenReturn(210021L);

        CompetitorBrowserObservationResultView result = service.applyBrowserObservations(
                operatorContext(),
                190001L,
                command
        );

        assertEquals(1, result.getSponsoredObservedCount());
        assertEquals(0, result.getSearchResultUpdatedCount());
        assertEquals(1, result.getSearchResultInsertedCount());
        assertEquals(1, result.getCompetitorInsertedCount());

        ArgumentCaptor<CompetitorProductInsertCommand> productCaptor =
                ArgumentCaptor.forClass(CompetitorProductInsertCommand.class);
        verify(mapper).insertCompetitorProduct(productCaptor.capture());
        assertEquals(200021L, productCaptor.getValue().getId());
        assertEquals("N51360862A", productCaptor.getValue().getNoonProductCode());
        assertEquals("PENDING", productCaptor.getValue().getReviewStatus());
        assertEquals("SEARCH_DISCOVERY", productCaptor.getValue().getSourceType());

        ArgumentCaptor<CompetitorSearchResultInsertCommand> resultCaptor =
                ArgumentCaptor.forClass(CompetitorSearchResultInsertCommand.class);
        verify(mapper).insertSearchResult(resultCaptor.capture());
        assertEquals(240021L, resultCaptor.getValue().getId());
        assertEquals(230017L, resultCaptor.getValue().getKeywordRunId());
        assertEquals(2, resultCaptor.getValue().getResultPosition());
        assertEquals("N51360862A", resultCaptor.getValue().getNoonProductCode());
        assertEquals(Boolean.TRUE, resultCaptor.getValue().getSponsored());

        verify(mapper).upsertKeywordProductRelationFromSearch(any());
        verify(mapper).markRankFactSponsored(230017L, "N51360862A", 601L);
    }

    private static CompetitorWatchProductCreateCommand createCommand(String selfNoonProductCode) {
        CompetitorWatchProductCreateCommand command = new CompetitorWatchProductCreateCommand();
        command.setStoreCode(" str108065-nsa ");
        command.setSiteCode(" sa ");
        command.setProductSiteOfferId(91001L);
        command.setSelfNoonProductCode(selfNoonProductCode);
        return command;
    }

    private static CompetitorProductOptionRow productOption(String pskuCode) {
        CompetitorProductOptionRow row = new CompetitorProductOptionRow();
        row.setOwnerUserId(501L);
        row.setLogicalStoreId(701L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setProductMasterId(801L);
        row.setProductVariantId(901L);
        row.setProductSiteOfferId(91001L);
        row.setSkuParent("BASKET-SA-001");
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setChildSku("BASKET-SA-001-01");
        row.setPskuCode(pskuCode);
        row.setTitle("Foldable Laundry Basket With Bamboo Handles");
        row.setBrand("Canman");
        row.setImageUrl("https://image.example/basket.jpg");
        row.setProductFulltype("home-laundry-baskets");
        return row;
    }

    private static CompetitorWatchProductRow watchProduct(Long id, String selfNoonProductCode) {
        CompetitorWatchProductRow row = new CompetitorWatchProductRow();
        row.setId(id);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setProductSiteOfferId(91001L);
        row.setPartnerSku("BASKET-SA-001-BLUE");
        row.setSelfNoonProductCode(selfNoonProductCode);
        row.setSelfCodeType("Z_CODE");
        row.setTitleSnapshot("Foldable Laundry Basket With Bamboo Handles");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorKeywordScopeRow keywordScope(Long keywordId, Long watchProductId) {
        CompetitorKeywordScopeRow row = new CompetitorKeywordScopeRow();
        row.setKeywordId(keywordId);
        row.setWatchProductId(watchProductId);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        row.setStatus("ACTIVE");
        return row;
    }

    private static CompetitorLatestRankPointRow rankPoint(
            Long keywordId,
            String trackedProductType,
            String noonProductCode,
            Integer rankNo
    ) {
        CompetitorLatestRankPointRow row = new CompetitorLatestRankPointRow();
        row.setKeywordId(keywordId);
        row.setKeyword("laundry basket");
        row.setTrackedProductType(trackedProductType);
        row.setNoonProductCode(noonProductCode);
        row.setRankStatus(rankNo == null ? "NOT_IN_TOP_20" : "RANKED");
        row.setRankNo(rankNo);
        row.setSponsored(false);
        row.setFactTime(LocalDateTime.of(2026, 6, 5, 8, 4));
        return row;
    }

    private static CompetitorBrowserObservationCommand browserObservationCommand(
            CompetitorBrowserObservationItem... items
    ) {
        CompetitorBrowserObservationCommand command = new CompetitorBrowserObservationCommand();
        command.setSourceUrl("https://www.noon.com/saudi-en/search/?q=Qili#nuonoKeywordId=190001");
        command.setItems(List.of(items));
        return command;
    }

    private static CompetitorBrowserObservationItem browserObservationItem(
            String noonProductCode,
            int position,
            String title
    ) {
        CompetitorBrowserObservationItem item = new CompetitorBrowserObservationItem();
        item.setNoonProductCode(noonProductCode);
        item.setPosition(position);
        item.setSponsored(true);
        item.setCanonicalUrl("https://www.noon.com/saudi-en/item/" + noonProductCode + "/p/");
        item.setTitle(title);
        item.setBrand("Qili");
        item.setImageUrl("https://f.nooncdn.com/p/" + noonProductCode + ".jpg");
        item.setCurrencyCode("SAR");
        return item;
    }

    private static CompetitorKeywordRunRow keywordRun(Long keywordRunId, Long searchRunId) {
        CompetitorKeywordRunRow row = new CompetitorKeywordRunRow();
        row.setId(keywordRunId);
        row.setSearchRunId(searchRunId);
        row.setKeywordId(190001L);
        row.setKeywordSnapshot("Qili");
        row.setCapturedAt(LocalDateTime.of(2026, 6, 7, 11, 49, 10));
        return row;
    }

    private static CompetitorProductRow competitorProduct(Long id, String noonProductCode, String reviewStatus) {
        CompetitorProductRow row = new CompetitorProductRow();
        row.setId(id);
        row.setWatchProductId(180123L);
        row.setNoonProductCode(noonProductCode);
        row.setCodeType(noonProductCode.startsWith("Z") ? "Z_CODE" : "N_CODE");
        row.setReviewStatus(reviewStatus);
        return row;
    }

    private static BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(601L)
                .businessOwnerUserId(501L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleLevel(3)
                .roleName("运营")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 501L))
                .menuPaths(Set.of("/operations/competitor-analysis"))
                .build();
    }
}
