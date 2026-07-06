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
import com.nuono.next.productkeyword.ProductKeywordCompetitorIndexer;
import java.lang.reflect.Method;
import java.time.LocalDate;
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

    @Mock
    private ProductKeywordCompetitorIndexer productKeywordCompetitorIndexer;

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
    void createWatchProductFallsBackToSkuParentWhenOfferPskuIsNotNoonCode() {
        CompetitorWatchProductCreateCommand command = createCommand(" z6122basketsa ");
        CompetitorProductOptionRow option = productOption("a19dc53c023bc493963d0fae19dfd9ba");
        option.setSkuParent("Z6122BASKETSA");
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(option);
        when(mapper.nextWatchProductId()).thenReturn(180124L);
        when(mapper.insertWatchProduct(any())).thenReturn(1);
        when(mapper.selectWatchProductById(501L, 180124L)).thenReturn(watchProduct(180124L, "Z6122BASKETSA"));
        when(mapper.listKeywordsByWatchProductId(180124L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180124L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180124L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180124L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180124L, view.getWatchProduct().getId());
        ArgumentCaptor<CompetitorWatchProductInsertCommand> captor =
                ArgumentCaptor.forClass(CompetitorWatchProductInsertCommand.class);
        verify(mapper).insertWatchProduct(captor.capture());
        assertEquals("Z6122BASKETSA", captor.getValue().getSelfNoonProductCode());
        assertEquals("Z_CODE", captor.getValue().getSelfCodeType());
    }

    @Test
    void createWatchProductReusesExistingPartnerSkuBindingBeforeInsert() {
        CompetitorWatchProductCreateCommand command = createCommand(" z6122basketsa ");
        CompetitorProductOptionRow option = productOption("a19dc53c023bc493963d0fae19dfd9ba");
        option.setSkuParent("Z6122BASKETSA");
        CompetitorWatchProductRow existingWatchProduct = watchProduct(180472L, "Z6122BASKETSA");
        existingWatchProduct.setProductSiteOfferId(55202L);
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(option);
        when(mapper.selectReusableWatchProductByProductIdentity(
                501L,
                "STR108065-NSA",
                "SA",
                701L,
                91001L,
                "BASKET-SA-001-BLUE"
        )).thenReturn(existingWatchProduct);
        when(mapper.selectWatchProductById(501L, 180472L)).thenReturn(existingWatchProduct);
        when(mapper.listKeywordsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180472L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180472L, view.getWatchProduct().getId());
        verify(mapper).updateWatchProductCurrentBinding(180472L, option, "Z6122BASKETSA", "Z_CODE", 601L);
        verify(mapper, never()).nextWatchProductId();
        verify(mapper, never()).insertWatchProduct(any());
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
        verify(mapper).updateWatchProductCurrentBinding(180123L, option, "Z6122BASKETSA", "Z_CODE", 601L);
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
        verify(mapper).updateWatchProductCurrentBinding(180123L, option, "Z6122BASKETSA", "Z_CODE", 601L);
        verify(mapper, never()).selectWatchProductByProductSiteOfferId(any(), any(), any(), any());
        verify(mapper, never()).nextWatchProductId();
        verify(mapper, never()).insertWatchProduct(any());
    }

    @Test
    void createWatchProductRefreshesCurrentBindingWhenOfferAndSelfCodeChangedButPartnerSkuSame() {
        CompetitorWatchProductCreateCommand command = createCommand("ZNEWBASKETSA");
        CompetitorProductOptionRow option = productOption("ZNEWBASKETSA");
        option.setProductVariantId(99002L);
        option.setProductSiteOfferId(99003L);
        option.setSkuParent("ZNEWBASKETSA");
        option.setPskuCode("a-new-noon-psku-code");
        CompetitorWatchProductRow existingWatchProduct = watchProduct(180472L, "ZOLDBASKETSA");
        existingWatchProduct.setProductVariantId(901L);
        existingWatchProduct.setProductSiteOfferId(91001L);
        existingWatchProduct.setPskuCode("old-noon-psku-code");
        when(mapper.selectProductOptionByOfferId(501L, "STR108065-NSA", "SA", 91001L))
                .thenReturn(option);
        when(mapper.selectReusableWatchProductByProductIdentity(
                501L,
                "STR108065-NSA",
                "SA",
                701L,
                99003L,
                "BASKET-SA-001-BLUE"
        )).thenReturn(existingWatchProduct);
        when(mapper.selectWatchProductById(501L, 180472L)).thenReturn(existingWatchProduct);
        when(mapper.listKeywordsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180472L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180472L, view.getWatchProduct().getId());
        verify(mapper).updateWatchProductCurrentBinding(180472L, option, "ZNEWBASKETSA", "Z_CODE", 601L);
        verify(mapper, never()).selectWatchProductByBusinessKey(any(), any(), any(), any(), any());
        verify(mapper, never()).nextWatchProductId();
        verify(mapper, never()).insertWatchProduct(any());
    }

    @Test
    void createWatchProductResolvesCurrentOfferByPartnerSkuWhenOfferIdMissing() {
        CompetitorWatchProductCreateCommand command = createCommand("ZNEWBASKETSA");
        command.setProductSiteOfferId(null);
        command.setPartnerSku(" basket-sa-001-blue ");
        CompetitorProductOptionRow option = productOption("ZNEWBASKETSA");
        option.setProductVariantId(99002L);
        option.setProductSiteOfferId(99003L);
        option.setSkuParent("ZNEWBASKETSA");
        CompetitorWatchProductRow existingWatchProduct = watchProduct(180472L, "ZOLDBASKETSA");
        when(mapper.selectProductOptionByPartnerSku(501L, "STR108065-NSA", "SA", "BASKET-SA-001-BLUE"))
                .thenReturn(option);
        when(mapper.selectReusableWatchProductByProductIdentity(
                501L,
                "STR108065-NSA",
                "SA",
                701L,
                99003L,
                "BASKET-SA-001-BLUE"
        )).thenReturn(existingWatchProduct);
        when(mapper.selectWatchProductById(501L, 180472L)).thenReturn(existingWatchProduct);
        when(mapper.listKeywordsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(180472L)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(180472L)).thenReturn(List.of());

        CompetitorWatchProductDetailView view = service.createWatchProduct(operatorContext(), command);

        assertEquals(180472L, view.getWatchProduct().getId());
        verify(mapper, never()).selectProductOptionByOfferId(any(), any(), any(), any());
        verify(mapper).updateWatchProductCurrentBinding(180472L, option, "ZNEWBASKETSA", "Z_CODE", 601L);
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
    void productOptionsKeepPskuBindingSeparateFromNoonCode() {
        CompetitorProductOptionRow option = productOption("a19dc53c023bc493963d0fae19dfd9ba");
        option.setSkuParent("Z6122BASKETSA");
        when(mapper.listProductOptions(501L, "STR108065-NSA", "SA", "basket", 20))
                .thenReturn(List.of(option));

        List<CompetitorProductOptionView> options = service.productOptions(
                operatorContext(),
                "STR108065-NSA",
                "SA",
                "basket",
                20
        );

        assertEquals(1, options.size());
        assertEquals("Z6122BASKETSA", options.get(0).getNoonProductCode());
        assertEquals("Z_CODE", options.get(0).getCodeType());
    }

    @Test
    void addKeywordStoresUnifiedProductKeywordIdOnCompetitorKeyword() throws Exception {
        service.setProductKeywordCompetitorIndexer(productKeywordCompetitorIndexer);
        when(mapper.selectWatchProductById(501L, 180123L)).thenReturn(watchProduct(180123L, "Z6122BASKETSA"));
        when(mapper.selectKeywordByNorm(180123L, "milk bottle")).thenReturn(null);
        when(mapper.nextKeywordId()).thenReturn(190222L);
        when(productKeywordCompetitorIndexer.indexKeyword(any())).thenReturn(300123L);
        when(mapper.insertKeyword(any())).thenReturn(1);
        stubDetail(180123L);

        service.addKeyword(operatorContext(), 180123L, keywordCommand(" Milk Bottle "));

        ArgumentCaptor<CompetitorKeywordInsertCommand> insertCaptor =
                ArgumentCaptor.forClass(CompetitorKeywordInsertCommand.class);
        verify(mapper).insertKeyword(insertCaptor.capture());
        Method getter = CompetitorKeywordInsertCommand.class.getMethod("getProductKeywordId");
        assertEquals(300123L, getter.invoke(insertCaptor.getValue()));
    }

    @Test
    void deleteKeywordWritesRemovedProductKeywordEvent() {
        service.setProductKeywordCompetitorIndexer(productKeywordCompetitorIndexer);
        when(mapper.selectKeywordScopeById(190001L)).thenReturn(keywordScope(190001L, 180123L));
        CompetitorKeywordRow keyword = keywordRow(190001L, 180123L, "Milk Bottle", "milk bottle");
        when(mapper.selectKeywordById(190001L)).thenReturn(keyword);
        when(mapper.softDeleteKeyword(190001L, 601L)).thenReturn(1);
        stubDetail(180123L);

        service.deleteKeyword(operatorContext(), 190001L);

        ArgumentCaptor<ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand> commandCaptor =
                ArgumentCaptor.forClass(ProductKeywordCompetitorIndexer.CompetitorKeywordIndexCommand.class);
        verify(productKeywordCompetitorIndexer).indexKeyword(commandCaptor.capture());
        assertEquals(190001L, commandCaptor.getValue().getKeywordId());
        assertEquals("Milk Bottle", commandCaptor.getValue().getKeyword());
        assertEquals("DELETED", commandCaptor.getValue().getStatus());
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
    void dashboardNormalizesScopeAndDays() {
        when(mapper.countPendingCandidates(501L, "STR108065-NSA", "SA")).thenReturn(4L);
        when(mapper.countMonitoringShortageProducts(501L, "STR108065-NSA", "SA", 3)).thenReturn(2L);
        when(mapper.countRankAnomalyProducts(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(3L);
        when(mapper.countCompetitorChangeProducts(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(5L);
        when(mapper.listDashboardIssueTrend(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(List.of());
        when(mapper.listCoverageTopProducts(501L, "STR108065-NSA", "SA", 3, 10)).thenReturn(List.of());
        when(mapper.listRankIssueTopProducts(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class), eq(10)))
                .thenReturn(List.of());
        when(mapper.listChangeTypeDistribution(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(List.of());
        when(mapper.listChangedProductTop(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class), eq(10)))
                .thenReturn(List.of());
        ArgumentCaptor<LocalDate> rankFromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> rankToDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        when(mapper.listRankChanges(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("SELF"),
                rankFromDateCaptor.capture(),
                rankToDateCaptor.capture(),
                eq("DOWN"),
                eq(100)
        ))
                .thenReturn(List.of(rankChange("SELF", 18, 11)));
        when(mapper.listRankChanges(eq(501L), eq("STR108065-NSA"), eq("SA"), eq("COMPETITOR"), any(LocalDate.class), any(LocalDate.class), eq("DOWN"), eq(100)))
                .thenReturn(List.of(rankChange("COMPETITOR", 10, 7)));
        ArgumentCaptor<LocalDate> attributeChangeFromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        when(mapper.listCompetitorAttributeChanges(eq(501L), eq("STR108065-NSA"), eq("SA"), attributeChangeFromDateCaptor.capture(), eq(30)))
                .thenReturn(List.of(attributeChange()));
        when(mapper.countCompetitorAttributeSnapshots(eq(501L), eq("STR108065-NSA"), eq("SA"), attributeChangeFromDateCaptor.capture()))
                .thenReturn(9L);

        CompetitorDashboardView view = service.dashboard(operatorContext(), " str108065-nsa ", " sa ", 99, "down");

        assertEquals("STR108065-NSA", view.getStoreCode());
        assertEquals("SA", view.getSiteCode());
        assertEquals(30, view.getDays());
        assertEquals(4, view.getIssueSummary().size());
        assertEquals(4L, view.getIssueSummary().get(0).getValue());
        assertEquals(CompetitorDashboardIssueType.PENDING_CANDIDATE, view.getIssueSummary().get(0).getIssueType());
        assertEquals("竞品详情变化", view.getIssueSummary().get(3).getLabel());
        assertEquals(1, view.getSelfRankChanges().size());
        assertEquals(7, view.getCompetitorRankChanges().get(0).getRankNo());
        assertEquals("PRICE", view.getCompetitorAttributeChanges().get(0).getChangeType());
        assertEquals("sticky notes", view.getCompetitorAttributeChanges().get(0).getSelfLatestRankKeyword());
        assertEquals(8, view.getCompetitorAttributeChanges().get(0).getChangeDateRankNo());
        assertEquals("RANKED", view.getCompetitorAttributeChanges().get(0).getSelfLatestRankStatus());
        assertEquals(3, view.getCompetitorAttributeChanges().get(0).getSelfLatestRankNo());
        assertEquals(100, view.getCompetitorAttributeChanges().get(0).getSelfLatestScanDepth());
        assertEquals(LocalDate.now().minusDays(29L), view.getCompetitorAttributeChangeDate());
        assertEquals(9L, view.getCompetitorAttributeSnapshotCount());
        assertEquals(LocalDate.now().minusDays(29L), attributeChangeFromDateCaptor.getAllValues().get(0));
        assertEquals(LocalDate.now().minusDays(29L), attributeChangeFromDateCaptor.getAllValues().get(1));
        assertEquals(LocalDate.now().minusDays(30L), rankFromDateCaptor.getValue());
        assertEquals(LocalDate.now().minusDays(1L), rankToDateCaptor.getValue());
    }

    @Test
    void dashboardUsesYesterdayToTodayRankWindowWhenDaysIsOne() {
        when(mapper.countPendingCandidates(501L, "STR108065-NSA", "SA")).thenReturn(4L);
        when(mapper.countMonitoringShortageProducts(501L, "STR108065-NSA", "SA", 3)).thenReturn(2L);
        when(mapper.countRankAnomalyProducts(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(3L);
        when(mapper.countCompetitorChangeProducts(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(5L);
        when(mapper.listDashboardIssueTrend(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(List.of());
        when(mapper.listCoverageTopProducts(501L, "STR108065-NSA", "SA", 3, 10)).thenReturn(List.of());
        when(mapper.listRankIssueTopProducts(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class), eq(10)))
                .thenReturn(List.of());
        when(mapper.listChangeTypeDistribution(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(List.of());
        when(mapper.listChangedProductTop(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class), eq(10)))
                .thenReturn(List.of());
        ArgumentCaptor<LocalDate> rankFromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> rankToDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        when(mapper.listRankChanges(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("SELF"),
                rankFromDateCaptor.capture(),
                rankToDateCaptor.capture(),
                eq("UP"),
                eq(100)
        ))
                .thenReturn(List.of(rankChange("SELF", 18, 11)));
        when(mapper.listRankChanges(eq(501L), eq("STR108065-NSA"), eq("SA"), eq("COMPETITOR"), any(LocalDate.class), any(LocalDate.class), eq("UP"), eq(100)))
                .thenReturn(List.of(rankChange("COMPETITOR", 18, 11)));
        when(mapper.listCompetitorAttributeChanges(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class), eq(30)))
                .thenReturn(List.of());
        when(mapper.countCompetitorAttributeSnapshots(eq(501L), eq("STR108065-NSA"), eq("SA"), any(LocalDate.class)))
                .thenReturn(0L);

        CompetitorDashboardView view = service.dashboard(operatorContext(), "STR108065-NSA", "SA", 1, "up");

        assertEquals(1, view.getDays());
        assertEquals(LocalDate.now().minusDays(1L), rankFromDateCaptor.getValue());
        assertEquals(LocalDate.now(), rankToDateCaptor.getValue());
    }

    @Test
    void dashboardUsesLatestAvailableRankDateWhenRequestedEndpointHasNoRankFacts() {
        LocalDate latestRankDate = LocalDate.now().minusDays(2L);
        when(mapper.selectLatestRankFactDate(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq(LocalDate.now().minusDays(1L))
        )).thenReturn(latestRankDate);
        ArgumentCaptor<LocalDate> rankFromDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> rankToDateCaptor = ArgumentCaptor.forClass(LocalDate.class);
        when(mapper.listRankChanges(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("SELF"),
                rankFromDateCaptor.capture(),
                rankToDateCaptor.capture(),
                eq("DOWN"),
                eq(100)
        )).thenReturn(List.of());
        when(mapper.listRankChanges(
                eq(501L),
                eq("STR108065-NSA"),
                eq("SA"),
                eq("COMPETITOR"),
                any(LocalDate.class),
                any(LocalDate.class),
                eq("DOWN"),
                eq(100)
        )).thenReturn(List.of());

        service.dashboard(operatorContext(), "STR108065-NSA", "SA", 7, "down");

        assertEquals(latestRankDate.minusDays(6L), rankFromDateCaptor.getValue());
        assertEquals(latestRankDate, rankToDateCaptor.getValue());
    }

    @Test
    void dashboardRejectsStoreOutsideScope() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> service.dashboard(operatorContext(), "STR-OTHER-NSA", "SA", 7, null)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        assertEquals("COMPETITOR_STORE_SCOPE_REQUIRED", error.getReason());
        verify(mapper, never()).countPendingCandidates(any(), any(), any());
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

    private static CompetitorKeywordCommand keywordCommand(String keyword) {
        CompetitorKeywordCommand command = new CompetitorKeywordCommand();
        command.setKeyword(keyword);
        return command;
    }

    private static CompetitorDashboardRankChangeRow rankChange(String trackedProductType, Integer previousRankNo, Integer rankNo) {
        CompetitorDashboardRankChangeRow row = new CompetitorDashboardRankChangeRow();
        row.setTrackedProductType(trackedProductType);
        row.setPreviousRankNo(previousRankNo);
        row.setRankNo(rankNo);
        row.setRankDelta(previousRankNo - rankNo);
        row.setPartnerSku("SKU-1");
        row.setKeyword("sticky notes");
        return row;
    }

    private static CompetitorDashboardAttributeChangeRow attributeChange() {
        CompetitorDashboardAttributeChangeRow row = new CompetitorDashboardAttributeChangeRow();
        row.setChangeType("PRICE");
        row.setLabel("价格变化");
        row.setPreviousValue("12.90");
        row.setCurrentValue("10.90");
        row.setPartnerSku("SKU-1");
        row.setLatestRankKeyword("sticky notes");
        row.setChangeDateRankNo(8);
        row.setSelfLatestRankKeyword("sticky notes");
        row.setSelfLatestRankStatus("RANKED");
        row.setSelfLatestRankNo(3);
        row.setSelfLatestScanDepth(100);
        return row;
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

    private static CompetitorKeywordRow keywordRow(Long id, Long watchProductId, String keyword, String keywordNorm) {
        CompetitorKeywordRow row = new CompetitorKeywordRow();
        row.setId(id);
        row.setWatchProductId(watchProductId);
        row.setKeyword(keyword);
        row.setKeywordNorm(keywordNorm);
        row.setStatus("ACTIVE");
        return row;
    }

    private void stubDetail(Long watchProductId) {
        when(mapper.selectWatchProductById(501L, watchProductId)).thenReturn(watchProduct(watchProductId, "Z6122BASKETSA"));
        when(mapper.listKeywordsByWatchProductId(watchProductId)).thenReturn(List.of());
        when(mapper.listProductsByWatchProductId(watchProductId)).thenReturn(List.of());
        when(mapper.listKeywordProductRelationsByWatchProductId(watchProductId)).thenReturn(List.of());
        when(mapper.listLatestRankPointsByWatchProductId(watchProductId)).thenReturn(List.of());
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
