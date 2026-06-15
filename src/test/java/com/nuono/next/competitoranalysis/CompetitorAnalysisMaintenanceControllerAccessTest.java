package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisMaintenanceControllerAccessTest {

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
    void addKeywordLoadsWatchProductScopeBeforeStorePermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CompetitorWatchProductScopeRow scope = watchScope();
        CompetitorKeywordCommand command = new CompetitorKeywordCommand();
        command.setKeyword("laundry basket");
        BusinessAccessContext context = operatorContext();
        CompetitorWatchProductDetailView detail = new CompetitorWatchProductDetailView();
        when(service.requireWatchProductScope(180123L)).thenReturn(scope);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.addKeyword(context, 180123L, command)).thenReturn(detail);

        CompetitorWatchProductDetailView result = controller.addKeyword(180123L, command, request);

        assertEquals(detail, result);
        InOrder order = inOrder(service, businessAccessResolver);
        order.verify(service).requireWatchProductScope(180123L);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
    }

    @Test
    void confirmCandidateLoadsKeywordCandidateScopeBeforeStorePermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CompetitorWatchProductScopeRow scope = watchScope();
        BusinessAccessContext context = operatorContext();
        CompetitorWatchProductDetailView detail = new CompetitorWatchProductDetailView();
        when(service.requireKeywordCandidateScope(190001L, 200001L)).thenReturn(scope);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(service.confirmCandidate(context, 190001L, 200001L)).thenReturn(detail);

        CompetitorWatchProductDetailView result = controller.confirmCandidate(190001L, 200001L, request);

        assertEquals(detail, result);
        InOrder order = inOrder(service, businessAccessResolver);
        order.verify(service).requireKeywordCandidateScope(190001L, 200001L);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
    }

    private static CompetitorWatchProductScopeRow watchScope() {
        CompetitorWatchProductScopeRow row = new CompetitorWatchProductScopeRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
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
