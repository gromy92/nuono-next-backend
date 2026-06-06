package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
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
