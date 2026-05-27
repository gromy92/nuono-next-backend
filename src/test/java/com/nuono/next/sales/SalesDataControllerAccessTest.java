package com.nuono.next.sales;

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
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SalesDataControllerAccessTest {

    @Mock
    private NoonSalesCsvImportService importService;

    @Mock
    private SalesAnalyticsService analyticsService;

    @Mock
    private SalesSyncTaskService syncTaskService;

    @Mock
    private SalesImportQualityService importQualityService;

    @Mock
    private SalesActivityWindowService activityWindowService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    private SalesDataController controller;

    @BeforeEach
    void setUp() {
        controller = new SalesDataController(
                importService,
                analyticsService,
                syncTaskService,
                importQualityService,
                activityWindowService,
                businessAccessResolver
        );
    }

    @Test
    void importNoonSalesCsvUsesAuthorizedOwnerAndStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(importService.importCsv(any()))
                .thenReturn(new NoonSalesCsvImportResult(
                        NoonSalesCsvImportService.SOURCE_SYSTEM,
                        9001L,
                        "confirmed-sanitized.csv",
                        2,
                        2,
                        0,
                        LocalDate.of(2026, 4, 30),
                        LocalDate.of(2026, 5, 4)
                ));
        SalesCsvImportRequest body = new SalesCsvImportRequest();
        body.setStoreCode("STR245027-SAU");
        body.setSiteCode("SA");
        body.setLogicalStoreId(245027L);
        body.setSourceFilename("confirmed-sanitized.csv");
        body.setCsv("Visit_Date,Partner_SKU\n");

        controller.importNoonProductViewsAndSalesData(body, request);

        ArgumentCaptor<NoonSalesCsvImportCommand> captor = ArgumentCaptor.forClass(NoonSalesCsvImportCommand.class);
        verify(importService).importCsv(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(245027L, captor.getValue().getLogicalStoreId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
    }

    @Test
    void importNoonSalesCsvDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));
        SalesCsvImportRequest body = new SalesCsvImportRequest();
        body.setStoreCode("STR245027-SAU");
        body.setSiteCode("SA");
        body.setCsv("Visit_Date,Partner_SKU\n");

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.importNoonProductViewsAndSalesData(body, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(importService, never()).importCsv(any());
    }

    @Test
    void listDailySalesFactsUsesAuthorizedOwnerAndFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(analyticsService.listDailyFacts(any()))
                .thenReturn(new SalesDailyFactsView(List.of()));

        controller.listDailyFacts(
                "STR245027-SAU",
                "SA",
                "2026-04-30",
                "2026-05-04",
                "SAMPLE-SKU-001",
                null,
                request
        );

        ArgumentCaptor<SalesFactQuery> captor = ArgumentCaptor.forClass(SalesFactQuery.class);
        verify(analyticsService).listDailyFacts(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(LocalDate.of(2026, 4, 30), captor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 4), captor.getValue().getDateTo());
        assertEquals("SAMPLE-SKU-001", captor.getValue().getPartnerSku());
    }

    @Test
    void salesAnalyticsSummaryUsesAuthorizedOwnerAndSearchFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(analyticsService.getSummary(any()))
                .thenReturn(new SalesAnalyticsSummary(1, 1, 1, 0, java.math.BigDecimal.TEN, 10, 20, null, null));

        controller.getSalesAnalyticsSummary(
                "STR245027-SAU",
                "SA",
                "2026-05-01",
                "2026-05-31",
                "papersay",
                "Paper Says",
                "Stationery-Paper-Sticker",
                "product_dimension_matched",
                "stable",
                null,
                request
        );

        ArgumentCaptor<SalesFactQuery> captor = ArgumentCaptor.forClass(SalesFactQuery.class);
        verify(analyticsService).getSummary(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals("papersay", captor.getValue().getSearchKeyword());
        assertEquals("Paper Says", captor.getValue().getBrand());
        assertEquals("Stationery-Paper-Sticker", captor.getValue().getProductFulltype());
        assertEquals("product_dimension_matched", captor.getValue().getDataQualityCode());
        assertEquals("stable", captor.getValue().getLifecycleCode());
    }

    @Test
    void salesAnalyticsExportUsesAuthorizedScopeAndReturnsCsvDownload() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(analyticsService.exportDailyFactsCsv(any()))
                .thenReturn("factDate,netUnits\n2026-05-18,8");

        ResponseEntity<String> response = controller.exportSalesAnalyticsDailyFacts(
                "STR245027-SAU",
                "SA",
                "2026-05-01",
                "2026-05-31",
                "papersay",
                null,
                null,
                null,
                null,
                null,
                request
        );

        assertEquals("factDate,netUnits\n2026-05-18,8", response.getBody());
        assertEquals("text/csv;charset=UTF-8", response.getHeaders().getContentType().toString());
        ArgumentCaptor<SalesFactQuery> captor = ArgumentCaptor.forClass(SalesFactQuery.class);
        verify(analyticsService).exportDailyFactsCsv(captor.capture());
        assertEquals("papersay", captor.getValue().getSearchKeyword());
    }

    @Test
    void salesAnalyticsExportDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "系统管理员不能操作店铺业务。"));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.exportSalesAnalyticsDailyFacts(
                        "STR245027-SAU",
                        "SA",
                        "2026-05-01",
                        "2026-05-31",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        request
                )
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(analyticsService, never()).exportDailyFactsCsv(any());
    }

    @Test
    void createActivityWindowUsesAuthorizedOwnerAndOperator() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(activityWindowService.save(any()))
                .thenReturn(activityWindowRecord());
        SalesActivityWindowRequest body = new SalesActivityWindowRequest();
        body.setStoreCode("STR245027-SAU");
        body.setSiteCode("SA");
        body.setName("Ramadan Peak");
        body.setActivityType("holiday");
        body.setCategoryScope("stationery");
        body.setDateFrom("2026-03-01");
        body.setDateTo("2026-04-10");
        body.setFactor(new java.math.BigDecimal("1.35"));
        body.setEnabled(true);

        controller.saveActivityWindow(body, request);

        ArgumentCaptor<SalesActivityWindowCommand> captor = ArgumentCaptor.forClass(SalesActivityWindowCommand.class);
        verify(activityWindowService).save(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(10003L, captor.getValue().getOperatorUserId());
    }

    @Test
    void activeActivityWindowSnapshotRequiresStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(activityWindowService.activeSnapshot(any()))
                .thenReturn(new SalesActivityWindowSnapshot(List.of(activityWindowRecord())));

        SalesActivityWindowSnapshot snapshot = controller.getActiveActivityWindows(
                "STR245027-SAU",
                "SA",
                "2026-03-20",
                "2026-03-31",
                request
        );

        assertEquals(1, snapshot.getWindows().size());
        ArgumentCaptor<SalesActivityWindowScope> captor = ArgumentCaptor.forClass(SalesActivityWindowScope.class);
        verify(activityWindowService).activeSnapshot(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
    }

    @Test
    void triggerNoonSalesSyncTaskUsesAuthorizedOwnerAndRequester() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(syncTaskService.triggerAndRun(any()))
                .thenReturn(succeededTask());
        SalesSyncTaskRequest body = syncRequest();

        controller.triggerNoonProductViewsAndSalesDataSync(body, request);

        ArgumentCaptor<SalesSyncTaskCommand> captor = ArgumentCaptor.forClass(SalesSyncTaskCommand.class);
        verify(syncTaskService).triggerAndRun(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals(245027L, captor.getValue().getLogicalStoreId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(LocalDate.of(2026, 4, 30), captor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 4), captor.getValue().getDateTo());
        assertEquals(10003L, captor.getValue().getRequestedBy());
        assertEquals("manual", captor.getValue().getTriggerType());
    }

    @Test
    void triggerNoonSalesSyncTaskDoesNotCallServiceWhenStoreAccessIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不能操作该店铺。"));
        SalesSyncTaskRequest body = syncRequest();

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> controller.triggerNoonProductViewsAndSalesDataSync(body, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(syncTaskService, never()).triggerAndRun(any());
    }

    @Test
    void getSalesSyncTaskStatusRequiresAccessToTaskStore() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(syncTaskService.getTask(5001L))
                .thenReturn(succeededTask());
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());

        SalesSyncTaskRecord task = controller.getSalesSyncTask(5001L, request);

        assertEquals("succeeded", task.getStatus());
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU");
    }

    @Test
    void listImportBatchesUsesAuthorizedOwnerAndFilters() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());
        when(importQualityService.listBatches(any()))
                .thenReturn(new SalesImportBatchListView(List.of()));

        controller.listImportBatches(
                "STR245027-SAU",
                "SA",
                "2026-05-01",
                "2026-05-05",
                request
        );

        ArgumentCaptor<SalesImportBatchQuery> captor = ArgumentCaptor.forClass(SalesImportBatchQuery.class);
        verify(importQualityService).listBatches(captor.capture());
        assertEquals(10002L, captor.getValue().getOwnerUserId());
        assertEquals("STR245027-SAU", captor.getValue().getStoreCode());
        assertEquals("SA", captor.getValue().getSiteCode());
        assertEquals(LocalDate.of(2026, 5, 1), captor.getValue().getDateFrom());
        assertEquals(LocalDate.of(2026, 5, 5), captor.getValue().getDateTo());
    }

    @Test
    void getImportBatchDetailRequiresAccessToBatchStore() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(importQualityService.getBatchDetail(10001L))
                .thenReturn(batchDetail());
        when(businessAccessResolver.requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU"))
                .thenReturn(salesContext());

        SalesImportBatchDetail detail = controller.getImportBatch(10001L, request);

        assertEquals("imported_with_exceptions", detail.getBatch().getStatus());
        assertEquals(1, detail.getExceptions().size());
        verify(businessAccessResolver).requireStoreAccess(request, BusinessCapability.SALES_DATA, "STR245027-SAU");
    }

    private SalesSyncTaskRequest syncRequest() {
        SalesSyncTaskRequest body = new SalesSyncTaskRequest();
        body.setLogicalStoreId(245027L);
        body.setStoreCode("STR245027-SAU");
        body.setSiteCode("SA");
        body.setDateFrom("2026-04-30");
        body.setDateTo("2026-05-04");
        return body;
    }

    private SalesSyncTaskRecord succeededTask() {
        return new SalesSyncTaskRecord(
                5001L,
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 4),
                10003L,
                "manual",
                "succeeded",
                9001L,
                1,
                1,
                0,
                LocalDate.of(2026, 5, 4),
                null
        );
    }

    private SalesImportBatchDetail batchDetail() {
        SalesImportBatchRecord batch = new SalesImportBatchRecord(
                10001L,
                NoonSalesCsvImportService.SOURCE_SYSTEM,
                "one-bad-row.csv",
                10002L,
                245027L,
                "STR245027-SAU",
                "SA",
                LocalDate.of(2026, 5, 4),
                LocalDate.of(2026, 5, 5),
                2,
                1,
                1,
                "imported_with_exceptions",
                "1 row(s) excluded from sales facts",
                null
        );
        SalesImportExceptionRecord exception = new SalesImportExceptionRecord(
                30001L,
                10001L,
                "one-bad-row.csv",
                10002L,
                "STR245027-SAU",
                "SA",
                3,
                "malformed_number",
                "Gross_Units",
                "not-a-number",
                "2026-05-05,SAMPLE-SKU-002",
                "Invalid numeric value for Gross_Units: not-a-number",
                "确认该字段是整数或留空后重导。"
        );
        return new SalesImportBatchDetail(batch, List.of(exception));
    }

    private SalesActivityWindowRecord activityWindowRecord() {
        return new SalesActivityWindowRecord(
                40001L,
                10002L,
                "STR245027-SAU",
                "SA",
                "Ramadan Peak",
                "holiday",
                "stationery",
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 10),
                new java.math.BigDecimal("1.35"),
                true,
                1,
                10003L,
                10003L
        );
    }

    private BusinessAccessContext salesContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(10003L)
                .businessOwnerUserId(10002L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleId(3L)
                .roleLevel(2)
                .roleName("运营")
                .storeCodes(Set.of("STR245027-SAU"))
                .storeOwnerUserIds(Map.of("STR245027-SAU", 10002L))
                .menuPaths(Set.of("/data/sales-analysis"))
                .build();
    }
}
