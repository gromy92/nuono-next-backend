package com.nuono.next.salesforecast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SalesForecastControllerAccessTest {

    @Mock
    private SalesForecastService forecastService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private SalesForecastController controller;

    @BeforeEach
    void setUp() {
        controller = new SalesForecastController(forecastService, businessAccessResolver);
    }

    @Test
    void overviewUsesAuthorizedStoreScopeAndReturnsRealEmptyState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(salesContext());
        when(forecastService.getOverview(any()))
                .thenReturn(SalesForecastOverviewView.empty("STR108065-NSA", "SA"));

        SalesForecastOverviewView overview = controller.getOverview("STR108065-NSA", "SA", request);

        assertEquals("empty", overview.getState());
        assertEquals("暂无销量预测结果", overview.getEmptyState().getTitle());
        assertEquals(0, overview.getRows().size());
        ArgumentCaptor<SalesForecastQuery> captor = ArgumentCaptor.forClass(SalesForecastQuery.class);
        verify(forecastService).getOverview(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
    }

    @Test
    void overviewDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "系统管理员不能操作店铺业务。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.getOverview("STR108065-NSA", "SA", request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(forecastService, never()).getOverview(any());
    }

    @Test
    void followUpUsesAuthorizedStoreScopeAndSessionOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(salesContext());
        when(forecastService.setFollowUp(any()))
                .thenReturn(new SalesForecastFollowUpView("PAPERSAYSB359", "SKU-1", true));

        SalesForecastFollowUpView view = controller.setFollowUp(
                new SalesForecastFollowUpRequest("STR108065-NSA", "SA", "PAPERSAYSB359", "SKU-1", true),
                request
        );

        assertEquals(true, view.isMarked());
        ArgumentCaptor<SalesForecastFollowUpCommand> captor = ArgumentCaptor.forClass(SalesForecastFollowUpCommand.class);
        verify(forecastService).setFollowUp(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals(307L, captor.getValue().getOperatorUserId());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals("PAPERSAYSB359", captor.getValue().getPartnerSku());
        assertEquals("SKU-1", captor.getValue().getSku());
        assertEquals(true, captor.getValue().isMarked());
    }

    @Test
    void recalculateUsesAuthorizedStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(salesContext());
        when(forecastService.recalculate(any()))
                .thenReturn(SalesForecastRunStatusView.failed("当前店铺还没有可用销量事实，无法重算销量预测。"));

        SalesForecastRunStatusView view = controller.recalculate("STR108065-NSA", "SA", request);

        assertEquals("failed", view.getStatus());
        ArgumentCaptor<SalesForecastQuery> captor = ArgumentCaptor.forClass(SalesForecastQuery.class);
        verify(forecastService).recalculate(captor.capture());
        assertEquals(307L, captor.getValue().getOwnerUserId());
        assertEquals("STR108065-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
    }

    @Test
    void exportUsesAuthorizedScopeAndPassesFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR108065-NSA"))
                .thenReturn(salesContext());
        when(forecastService.exportCsv(any(), any()))
                .thenReturn(new SalesForecastExportView(
                        "sales-forecast-STR108065-NSA-SA.csv",
                        "text/csv;charset=UTF-8",
                        "partnerSku,sku\nP1,S1\n"
                ));

        org.springframework.http.ResponseEntity<String> response = controller.export(
                "STR108065-NSA",
                "SA",
                60,
                "paper",
                "risk",
                "low",
                request
        );

        assertEquals(200, response.getStatusCodeValue());
        assertEquals("partnerSku,sku\nP1,S1\n", response.getBody());
        ArgumentCaptor<SalesForecastQuery> queryCaptor = ArgumentCaptor.forClass(SalesForecastQuery.class);
        ArgumentCaptor<SalesForecastExportQuery> exportCaptor = ArgumentCaptor.forClass(SalesForecastExportQuery.class);
        verify(forecastService).exportCsv(queryCaptor.capture(), exportCaptor.capture());
        assertEquals(307L, queryCaptor.getValue().getOwnerUserId());
        assertEquals(60, exportCaptor.getValue().getForecastWindow());
        assertEquals("paper", exportCaptor.getValue().getSearchKeyword());
        assertEquals("risk", exportCaptor.getValue().getRiskFilter());
        assertEquals("low", exportCaptor.getValue().getConfidenceFilter());
    }

    private BusinessAccessContext salesContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleId(2L)
                .roleLevel(1)
                .roleName("老板")
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/data/sales-forecast"))
                .build();
    }
}
