package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class Ali1688HistoricalOrderExcelImportControllerTest {

    @Mock
    private ObjectProvider<LocalDbAli1688HistoricalOrderService> serviceProvider;

    @Mock
    private LocalDbAli1688HistoricalOrderService service;

    @Mock
    private ObjectProvider<Ali1688HistoricalOrderOAuthService> oauthServiceProvider;

    @Mock
    private BusinessAccessResolver accessResolver;

    private Ali1688HistoricalOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new Ali1688HistoricalOrderController(serviceProvider, oauthServiceProvider, accessResolver);
    }

    @Test
    void bossCanPreviewExcelImportForBoundSourceAndStoreScope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = bossContext();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sanitized-1688-order-export.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Ali1688HistoricalOrderExcelFixtureSupport.sanitizedWorkbook()
        );
        Ali1688HistoricalOrderExcelImportView.SummaryView summary =
                new Ali1688HistoricalOrderExcelImportView.SummaryView();
        summary.setOrderHeaderRowCount(2);
        summary.setProductLineCount(3);
        summary.setLogisticsLineCount(2);
        summary.setValidRowCount(3);
        Ali1688HistoricalOrderExcelImportView.PreviewView expected =
                new Ali1688HistoricalOrderExcelImportView.PreviewView();
        expected.setBatchId(97001L);
        expected.setStatus("preview_ready");
        expected.setSummary(summary);
        ArgumentCaptor<Ali1688HistoricalOrderExcelImportView.PreviewRequest> requestCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderExcelImportView.PreviewRequest.class);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.previewExcelImport(eq(context), requestCaptor.capture(), eq(file))).thenReturn(expected);

        Ali1688HistoricalOrderExcelImportView.PreviewView view =
                controller.previewExcelImport(file, 91008L, "PRJ108065", "AE", request);

        assertThat(view.getBatchId()).isEqualTo(97001L);
        assertThat(view.getSummary().getProductLineCount()).isEqualTo(3);
        assertThat(requestCaptor.getValue().getAuthorizationId()).isEqualTo(91008L);
        assertThat(requestCaptor.getValue().getStoreCode()).isEqualTo("PRJ108065");
        assertThat(requestCaptor.getValue().getSiteCode()).isEqualTo("AE");
        verify(service).previewExcelImport(eq(context), any(), eq(file));
    }

    @Test
    void bossCanCommitPreviewBatchForCurrentStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderExcelImportView.CommitCountsView counts =
                new Ali1688HistoricalOrderExcelImportView.CommitCountsView();
        counts.setInsertedOrderCount(2);
        counts.setInsertedItemCount(3);
        counts.setInsertedLogisticsCount(2);
        Ali1688HistoricalOrderExcelImportView.CommitView expected =
                new Ali1688HistoricalOrderExcelImportView.CommitView();
        expected.setBatchId(97001L);
        expected.setStatus("committed");
        expected.setCounts(counts);
        ArgumentCaptor<Ali1688HistoricalOrderExcelImportView.CommitRequest> requestCaptor =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderExcelImportView.CommitRequest.class);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.commitExcelImport(eq(context), requestCaptor.capture())).thenReturn(expected);

        Ali1688HistoricalOrderExcelImportView.CommitView view =
                controller.commitExcelImport(97001L, "PRJ108065", "AE", request);

        assertThat(view.getStatus()).isEqualTo("committed");
        assertThat(view.getCounts().getInsertedItemCount()).isEqualTo(3);
        assertThat(requestCaptor.getValue().getBatchId()).isEqualTo(97001L);
        assertThat(requestCaptor.getValue().getStoreCode()).isEqualTo("PRJ108065");
        assertThat(requestCaptor.getValue().getSiteCode()).isEqualTo("AE");
    }

    @Test
    void operationsCannotPreviewOrCommitExcelImport() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sanitized-1688-order-export.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Ali1688HistoricalOrderExcelFixtureSupport.sanitizedWorkbook()
        );

        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.previewExcelImport(file, 91008L, "PRJ108065", "AE", request)
                )
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.commitExcelImport(97001L, "PRJ108065", "AE", request)
                )
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).previewExcelImport(eq(context), any(), eq(file));
        verify(service, never()).commitExcelImport(eq(context), any());
    }

    @Test
    void operationsCanReadSafeExcelImportBatchHistoryForCurrentStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = operatorContext();
        Ali1688HistoricalOrderExcelImportView.BatchView batch =
                new Ali1688HistoricalOrderExcelImportView.BatchView();
        batch.setBatchId(97001L);
        batch.setStatus("committed");
        batch.setFileName("sanitized-1688-order-export.xlsx");
        batch.setFileHash("safe-hash");
        batch.setAccountLabel("沁雪冰菏 Excel 导入");
        batch.setStoreCode("PRJ108065");
        batch.setSiteCode("AE");
        batch.setCreatedBy(307L);
        batch.setCreatedAt("2026-05-26 12:00:00");

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.listExcelImportBatches(context, "PRJ108065", "AE")).thenReturn(List.of(batch));

        List<Ali1688HistoricalOrderExcelImportView.BatchView> view =
                controller.listExcelImportBatches("PRJ108065", "AE", request);

        assertThat(view).hasSize(1);
        assertThat(view.get(0).getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        assertThat(view.get(0).getFileHash()).isEqualTo("safe-hash");
        verify(service).listExcelImportBatches(context, "PRJ108065", "AE");
    }

    @Test
    void bossCanReadSafeExcelImportBatchDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = bossContext();
        Ali1688HistoricalOrderExcelImportView.BatchDetailView detail =
                new Ali1688HistoricalOrderExcelImportView.BatchDetailView();
        detail.setBatchId(97001L);
        detail.setStatus("validation_failed");
        detail.setFailureCode("header_mismatch");
        detail.setFailureMessage("表头不匹配，请重新导出。");
        detail.setErrorSummaryJson("{\"rowErrors\":2,\"rowWarnings\":0}");

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.excelImportBatchDetail(context, 97001L, "PRJ108065", "AE")).thenReturn(detail);

        Ali1688HistoricalOrderExcelImportView.BatchDetailView view =
                controller.excelImportBatchDetail(97001L, "PRJ108065", "AE", request);

        assertThat(view.getBatchId()).isEqualTo(97001L);
        assertThat(view.getFailureCode()).isEqualTo("header_mismatch");
        assertThat(view.getErrorSummaryJson()).contains("rowErrors");
    }

    private BusinessAccessContext bossContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.BOSS)
                .roleName("老板")
                .roleLevel(1)
                .storeCodes(Set.of("PRJ108065"))
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }

    private BusinessAccessContext operatorContext() {
        return BusinessAccessContext.builder()
                .sessionUserId(409L)
                .businessOwnerUserId(307L)
                .accountType(BusinessAccountType.OPERATOR)
                .roleName("运营")
                .roleLevel(3)
                .storeCodes(Set.of("PRJ108065"))
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }
}
