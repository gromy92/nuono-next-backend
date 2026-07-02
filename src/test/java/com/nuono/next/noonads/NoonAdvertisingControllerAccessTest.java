package com.nuono.next.noonads;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class NoonAdvertisingControllerAccessTest {

    @Mock
    private NoonAdvertisingAnalyticsService analyticsService;

    @Mock
    private NoonAdvertisingImportService importService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private NoonAdvertisingController controller;

    @BeforeEach
    void setUp() {
        controller = new NoonAdvertisingController(analyticsService, importService, businessAccessResolver);
    }

    @Test
    void noonAdvertisingControllerIsRestController() {
        assertNotNull(NoonAdvertisingController.class.getAnnotation(RestController.class));
    }

    @Test
    void dashboardUsesAuthorizedOwnerAndReportWindow() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR69486-NSA"))
                .thenReturn(advertisingContext());
        when(analyticsService.dashboard(any()))
                .thenReturn(new NoonAdvertisingDashboardView(null, null, null, null, null, null));

        controller.dashboard(
                " PRJ69486 ",
                " STR69486-NSA ",
                " SA ",
                "2026-05-26",
                "2026-06-25",
                request
        );

        ArgumentCaptor<NoonAdvertisingDashboardQuery> captor = ArgumentCaptor.forClass(NoonAdvertisingDashboardQuery.class);
        verify(analyticsService).dashboard(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("PRJ69486", captor.getValue().getProjectCode());
        assertEquals("STR69486-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 26), captor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 6, 25), captor.getValue().getDateTo());
    }

    @Test
    void latestReportWindowUsesAuthorizedOwnerAndScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR69486-NSA"))
                .thenReturn(advertisingContext());
        when(analyticsService.latestReportWindow(any()))
                .thenReturn(new NoonAdvertisingLatestReportWindowView(
                        true,
                        LocalDate.of(2026, 5, 26),
                        LocalDate.of(2026, 6, 25)
                ));

        NoonAdvertisingLatestReportWindowView view = controller.latestReportWindow(
                " PRJ69486 ",
                " STR69486-NSA ",
                " SA ",
                request
        );

        ArgumentCaptor<NoonAdvertisingScopeQuery> captor = ArgumentCaptor.forClass(NoonAdvertisingScopeQuery.class);
        verify(analyticsService).latestReportWindow(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("PRJ69486", captor.getValue().getProjectCode());
        assertEquals("STR69486-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 26), view.getDateFrom());
        assertEquals(LocalDate.of(2026, 6, 25), view.getDateTo());
    }

    @Test
    void dashboardDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR69486-NSA"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.dashboard(
                        "PRJ69486",
                        "STR69486-NSA",
                        "SA",
                        "2026-05-26",
                        "2026-06-25",
                        request
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(analyticsService, never()).dashboard(any());
    }

    @Test
    void dashboardRejectsInvalidDateWindowBeforeQueryingService() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.dashboard(
                        "PRJ69486",
                        "STR69486-NSA",
                        "SA",
                        "2026-06-25",
                        "2026-05-26",
                        request
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        verify(analyticsService, never()).dashboard(any());
    }

    @Test
    void importReportUsesAuthorizedOwnerAndRequester() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR69486-NSA"))
                .thenReturn(advertisingContext());
        when(importService.importReport(any()))
                .thenReturn(new NoonAdvertisingImportResult(
                        200001L,
                        1,
                        1,
                        LocalDate.of(2026, 5, 26),
                        LocalDate.of(2026, 6, 25)
                ));

        controller.importReport(importRequest(), request);

        ArgumentCaptor<NoonAdvertisingReportImportCommand> captor = ArgumentCaptor.forClass(NoonAdvertisingReportImportCommand.class);
        verify(importService).importReport(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(10003L, captor.getValue().getRequestedBy());
        assertEquals("PRJ69486", captor.getValue().getProjectCode());
        assertEquals("STR69486-NSA", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 26), captor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 6, 25), captor.getValue().getDateTo());
        assertEquals(1, captor.getValue().getCampaignRows().size());
        assertEquals(1, captor.getValue().getQueryRows().size());
    }

    @Test
    void importReportDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR69486-NSA"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.importReport(importRequest(), request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(importService, never()).importReport(any());
    }

    private NoonAdvertisingReportImportRequest importRequest() {
        NoonAdvertisingReportImportRequest body = new NoonAdvertisingReportImportRequest();
        body.setProjectCode("PRJ69486");
        body.setStoreCode("STR69486-NSA");
        body.setSiteCode("SA");
        body.setDateFrom("2026-05-26");
        body.setDateTo("2026-06-25");
        body.setSourceName("campaign-query-normalized.json");
        body.setSourceDigestSha256("sha256-sample");
        body.setNotes("manual normalized import");

        NoonAdvertisingCampaignFact campaign = new NoonAdvertisingCampaignFact();
        campaign.setCampaignCode("C_U9Q0F611VL");
        campaign.setCampaignName("Brand towels exact");
        campaign.setSpendAmount(new BigDecimal("150.59"));

        NoonAdvertisingQueryFact query = new NoonAdvertisingQueryFact();
        query.setCampaignCode("C_NBN9Z09F58");
        query.setPartnerSku("ZDD-SAMPLE-001");
        query.setQueryText("دبدوب");
        query.setSpendAmount(new BigDecimal("21.57"));

        body.setCampaignRows(List.of(campaign));
        body.setQueryRows(List.of(query));
        return body;
    }

    private BusinessAccessContext advertisingContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleId(3L)
                .roleLevel(2)
                .roleName("运营")
                .storeCodes(Set.of("STR69486-NSA"))
                .storeOwnerUserIds(Map.of("STR69486-NSA", 10002L))
                .menuPaths(Set.of("/operations/noon-ads"))
                .build();
    }
}
