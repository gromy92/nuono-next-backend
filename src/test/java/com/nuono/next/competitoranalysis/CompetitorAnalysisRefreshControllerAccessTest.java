package com.nuono.next.competitoranalysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ResponseStatus;

@ExtendWith(MockitoExtension.class)
class CompetitorAnalysisRefreshControllerAccessTest {

    @Mock
    private CompetitorAnalysisService service;

    @Mock
    private CompetitorAnalysisRefreshService refreshService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private CompetitorAnalysisController controller;

    @BeforeEach
    void setUp() {
        controller = new CompetitorAnalysisController(service, refreshService, businessAccessResolver);
    }

    @Test
    void refreshWatchProductLoadsScopeBeforeStorePermission() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        CompetitorWatchProductScopeRow scope = watchScope();
        BusinessAccessContext context = operatorContext();
        CompetitorRefreshRunView view = refreshView();
        when(service.requireWatchProductScope(180123L)).thenReturn(scope);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(refreshService.requestRefresh(context, 180123L)).thenReturn(view);

        CompetitorRefreshRunView result = controller.refreshWatchProduct(180123L, request);

        assertEquals(view, result);
        InOrder order = inOrder(service, businessAccessResolver, refreshService);
        order.verify(service).requireWatchProductScope(180123L);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
        order.verify(refreshService).requestRefresh(context, 180123L);
    }

    @Test
    void refreshWatchProductReturnsAccepted() throws Exception {
        Method method = CompetitorAnalysisController.class.getMethod(
                "refreshWatchProduct",
                Long.class,
                HttpServletRequest.class
        );

        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        assertNotNull(responseStatus);
        assertEquals(HttpStatus.ACCEPTED, responseStatus.value());
    }

    @Test
    void manualMonitoringUsesStorePermissionBeforeDelegating() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorTaskView view = new CompetitorTaskView();
        view.setTaskId(150001L);
        when(businessAccessResolver.requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        )).thenReturn(context);
        when(refreshService.requestStoreMonitoring(context, "STR108065-NSA", "SA")).thenReturn(view);

        CompetitorTaskView result = controller.manualMonitoring("STR108065-NSA", "sa", request);

        assertEquals(view, result);
        InOrder order = inOrder(businessAccessResolver, refreshService);
        order.verify(businessAccessResolver).requireStoreAccess(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS,
                "STR108065-NSA"
        );
        order.verify(refreshService).requestStoreMonitoring(context, "STR108065-NSA", "SA");
    }

    @Test
    void manualMonitoringReturnsAccepted() throws Exception {
        Method method = CompetitorAnalysisController.class.getMethod(
                "manualMonitoring",
                String.class,
                String.class,
                HttpServletRequest.class
        );

        ResponseStatus responseStatus = method.getAnnotation(ResponseStatus.class);

        assertNotNull(responseStatus);
        assertEquals(HttpStatus.ACCEPTED, responseStatus.value());
    }

    @Test
    void refreshRunUsesBusinessContextBeforeDelegating() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorRefreshRunView view = refreshView();
        when(businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
        )).thenReturn(context);
        when(refreshService.getRefreshRun(context, 220123L)).thenReturn(view);

        CompetitorRefreshRunView result = controller.refreshRun(220123L, request);

        assertEquals(view, result);
        InOrder order = inOrder(businessAccessResolver, refreshService);
        order.verify(businessAccessResolver).requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
        );
        order.verify(refreshService).getRefreshRun(context, 220123L);
    }

    @Test
    void taskUsesBusinessContextBeforeDelegating() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        CompetitorTaskView view = new CompetitorTaskView();
        view.setTaskId(150000L);
        when(businessAccessResolver.requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
        )).thenReturn(context);
        when(refreshService.getTask(context, 150000L)).thenReturn(view);

        CompetitorTaskView result = controller.task(150000L, request);

        assertEquals(view, result);
        InOrder order = inOrder(businessAccessResolver, refreshService);
        order.verify(businessAccessResolver).requireBusinessContext(
                request,
                BusinessCapability.OPERATIONS_COMPETITOR_ANALYSIS
        );
        order.verify(refreshService).getTask(context, 150000L);
    }

    private static CompetitorWatchProductScopeRow watchScope() {
        CompetitorWatchProductScopeRow row = new CompetitorWatchProductScopeRow();
        row.setId(180123L);
        row.setOwnerUserId(501L);
        row.setStoreCode("STR108065-NSA");
        row.setSiteCode("SA");
        return row;
    }

    private static CompetitorRefreshRunView refreshView() {
        CompetitorRefreshRunView view = new CompetitorRefreshRunView();
        view.setTaskId(150000L);
        view.setRunId(220123L);
        view.setRunStatus("RUNNING");
        return view;
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
