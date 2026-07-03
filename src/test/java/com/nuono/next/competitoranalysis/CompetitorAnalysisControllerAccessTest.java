package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisControllerAccessTest {

    @Mock
    private CompetitorAnalysisService service;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private CompetitorAnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new CompetitorAnalysisController(service, businessAccessResolver);
    }

    @Test
    void createWatchProductRejectsStoreOutsideSessionScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CompetitorWatchProductCreateCommand command = new CompetitorWatchProductCreateCommand();
        command.setStoreCode("STR108065-NSA");
        command.setSiteCode("SA");
        command.setProductSiteOfferId(91001L);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "store forbidden"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.createWatchProduct(command, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
    }

    @Test
    void dashboardRequiresStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorDashboardView dashboard = CompetitorDashboardView.of(
                "STR108065-NSA",
                "SA",
                7,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                LocalDate.now().minusDays(1L),
                0L,
                List.of()
        );
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.dashboard(context, "STR108065-NSA", "SA", 7, "DOWN")).thenReturn(dashboard);

        CompetitorDashboardView result = controller.dashboard(" str108065-nsa ", " sa ", 7, "DOWN", request);

        assertEquals("STR108065-NSA", result.getStoreCode());
        verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
        verify(service).dashboard(context, "STR108065-NSA", "SA", 7, "DOWN");
    }

    @Test
    void detailLoadsWatchProductScopeBeforeCheckingStorePermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorWatchProductScopeRow scope = new CompetitorWatchProductScopeRow();
        scope.setId(180123L);
        scope.setOwnerUserId(501L);
        scope.setStoreCode("STR108065-NSA");
        scope.setSiteCode("SA");
        CompetitorWatchProductDetailView detail = new CompetitorWatchProductDetailView();
        when(service.requireWatchProductScope(180123L)).thenReturn(scope);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.detail(context, 180123L)).thenReturn(detail);

        CompetitorWatchProductDetailView result = controller.watchProductDetail(180123L, request);

        assertEquals(detail, result);
        InOrder order = inOrder(service, businessAccessResolver);
        order.verify(service).requireWatchProductScope(180123L);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
        verify(service).detail(context, 180123L);
    }

    @Test
    void productBaselinesUseStoreScopedCompetitorAnalysisListService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorWatchProductListView list = new CompetitorWatchProductListView();
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.listProductBaselines(eq(context), any(CompetitorWatchProductQuery.class))).thenReturn(list);

        CompetitorWatchProductListView result = controller.productBaselines(
                " str108065-nsa ",
                " sa ",
                " basket ",
                " sticky notes ",
                " zcomp ",
                " active ",
                true,
                true,
                "recent7dChangeCountAsc",
                2,
                15,
                request
        );

        assertEquals(list, result);
        ArgumentCaptor<CompetitorWatchProductQuery> queryCaptor =
                ArgumentCaptor.forClass(CompetitorWatchProductQuery.class);
        verify(service).listProductBaselines(eq(context), queryCaptor.capture());
        CompetitorWatchProductQuery query = queryCaptor.getValue();
        assertEquals("STR108065-NSA", query.getStoreCode());
        assertEquals("SA", query.getSiteCode());
        assertEquals("basket", query.getProductSearch());
        assertEquals("sticky notes", query.getKeywordSearch());
        assertEquals("zcomp", query.getCompetitorSearch());
        assertEquals("ACTIVE", query.getStatus());
        assertEquals(true, query.isConfirmedCompetitorCountZero());
        assertEquals(true, query.isPendingCandidateCountZero());
        assertEquals("RECENT_7D_CHANGE_COUNT_ASC", query.getSortBy());
        assertEquals(2, query.getPage());
        assertEquals(15, query.getPageSize());
    }

    @Test
    void rankHistoryLoadsWatchProductScopeBeforeCheckingStorePermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorWatchProductScopeRow scope = new CompetitorWatchProductScopeRow();
        scope.setId(180123L);
        scope.setOwnerUserId(501L);
        scope.setStoreCode("STR108065-NSA");
        scope.setSiteCode("SA");
        CompetitorRankHistoryView history = new CompetitorRankHistoryView();
        when(service.requireWatchProductScope(180123L)).thenReturn(scope);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.rankHistory(context, 180123L, 190001L, 30)).thenReturn(history);

        CompetitorRankHistoryView result = controller.rankHistory(180123L, 190001L, 30, request);

        assertEquals(history, result);
        InOrder order = inOrder(service, businessAccessResolver);
        order.verify(service).requireWatchProductScope(180123L);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
        verify(service).rankHistory(context, 180123L, 190001L, 30);
    }

    @Test
    void productChangesLoadsWatchProductScopeBeforeCheckingStorePermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorWatchProductScopeRow scope = new CompetitorWatchProductScopeRow();
        scope.setId(180123L);
        scope.setOwnerUserId(501L);
        scope.setStoreCode("STR108065-NSA");
        scope.setSiteCode("SA");
        CompetitorProductChangeListView changes = new CompetitorProductChangeListView();
        when(service.requireWatchProductScope(180123L)).thenReturn(scope);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.productChanges(context, 180123L, 80)).thenReturn(changes);

        CompetitorProductChangeListView result = controller.productChanges(180123L, 80, request);

        assertEquals(changes, result);
        InOrder order = inOrder(service, businessAccessResolver);
        order.verify(service).requireWatchProductScope(180123L);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
        verify(service).productChanges(context, 180123L, 80);
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
