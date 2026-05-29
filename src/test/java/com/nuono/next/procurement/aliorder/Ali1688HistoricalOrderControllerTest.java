package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class Ali1688HistoricalOrderControllerTest {

    @Mock
    private ObjectProvider<LocalDbAli1688HistoricalOrderService> serviceProvider;

    @Mock
    private LocalDbAli1688HistoricalOrderService service;

    @Mock
    private BusinessAccessResolver accessResolver;

    private Ali1688HistoricalOrderController controller;

    @BeforeEach
    void setUp() {
        controller = new Ali1688HistoricalOrderController(serviceProvider, accessResolver);
    }

    @Test
    void bossCanReadWorkbenchWithAuthorizationEntryVisible() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.buildWorkbench(context)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.workbench(request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("not_authorized");
        assertThat(view.getRoleCapabilities().isCanAuthorize()).isTrue();
        assertThat(view.getOrders()).isEmpty();
        verify(service).buildWorkbench(context);
    }

    @Test
    void operationsCanReadWorkbenchWithoutAuthorizationMutation() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(408L, BusinessAccountType.OPERATOR, "运营", 3);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.buildWorkbench(context)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.workbench(request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("not_authorized");
        assertThat(view.getRoleCapabilities().isCanAuthorize()).isFalse();
        assertThat(view.getOrders()).isEmpty();
    }

    @Test
    void workbenchPassesFilterAndPaginationQueryToService() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderQuery query = Ali1688HistoricalOrderQuery.fromRequest(
                "2026-05-01 00:00:00",
                "2026-05-25 23:59:59",
                "已付款",
                "义乌",
                "745612345678",
                "PRJ108065",
                "AE",
                "unassigned",
                "PRJ108065",
                "AE",
                2,
                10
        );
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.buildWorkbench(context, query)).thenReturn(expected);

        controller.workbench(
                "2026-05-01 00:00:00",
                "2026-05-25 23:59:59",
                "已付款",
                "义乌",
                "745612345678",
                "PRJ108065",
                "AE",
                "unassigned",
                "PRJ108065",
                "AE",
                2,
                10,
                request
        );

        verify(service).buildWorkbench(context, query);
    }

    @Test
    void unauthorizedRoleIsRejectedBeforeServiceReadsData() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有对应业务菜单权限。"));

        assertThatThrownBy(() -> controller.workbench(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void bossCanCreateDevAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.authorizedDev(context, 91001L);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.createDevAuthorization(context)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.createDevAuthorization(request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("authorized");
        assertThat(view.getAuthorization().getAuthorizationId()).isEqualTo(91001L);
        assertThat(view.getRoleCapabilities().isCanAuthorize()).isTrue();
        assertThat(view.getRoleCapabilities().isCanTriggerSync()).isTrue();
        verify(service).createDevAuthorization(context);
    }

    @Test
    void operationsCannotCreateDevAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(408L, BusinessAccountType.OPERATOR, "运营", 3);

        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);

        assertThatThrownBy(() -> controller.createDevAuthorization(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).createDevAuthorization(context);
    }

    @Test
    void bossCanCreateExcelUploadSourceForCurrentStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderExcelImportView.SourceCreateRequest body =
                new Ali1688HistoricalOrderExcelImportView.SourceCreateRequest();
        body.setAccountLabel("沁雪冰菏 Excel 导入");
        body.setStoreCode("PRJ108065");
        body.setSiteCode("AE");
        Ali1688HistoricalOrderExcelImportView.SourceView expected =
                Ali1688HistoricalOrderExcelImportView.SourceView.excelUpload(
                        91008L,
                        "沁雪冰菏 Excel 导入",
                        "PRJ108065",
                        "AE"
                );

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.createExcelUploadSource(context, body)).thenReturn(expected);

        Ali1688HistoricalOrderExcelImportView.SourceView view =
                controller.createExcelImportSource(body, request);

        assertThat(view.getAuthorizationId()).isEqualTo(91008L);
        assertThat(view.getProviderCode()).isEqualTo(LocalDbAli1688HistoricalOrderService.EXCEL_UPLOAD_PROVIDER_CODE);
        assertThat(view.getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        assertThat(view.getStoreCode()).isEqualTo("PRJ108065");
        assertThat(view.getSiteCode()).isEqualTo("AE");
        verify(service).createExcelUploadSource(context, body);
    }

    @Test
    void bossCanListExcelUploadSourcesForCurrentStoreScope() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        List<Ali1688HistoricalOrderExcelImportView.SourceView> expected = List.of(
                Ali1688HistoricalOrderExcelImportView.SourceView.excelUpload(
                        91008L,
                        "沁雪冰菏 Excel 导入",
                        "PRJ108065",
                        "AE"
                )
        );

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.listExcelUploadSources(context, "PRJ108065", "AE")).thenReturn(expected);

        List<Ali1688HistoricalOrderExcelImportView.SourceView> sources =
                controller.listExcelImportSources("PRJ108065", "AE", request);

        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).getAuthorizationId()).isEqualTo(91008L);
        assertThat(sources.get(0).getAccountLabel()).isEqualTo("沁雪冰菏 Excel 导入");
        verify(service).listExcelUploadSources(context, "PRJ108065", "AE");
    }

    @Test
    void operationsCannotCreateExcelUploadSource() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(408L, BusinessAccountType.OPERATOR, "运营", 3);
        Ali1688HistoricalOrderExcelImportView.SourceCreateRequest body =
                new Ali1688HistoricalOrderExcelImportView.SourceCreateRequest();
        body.setAccountLabel("运营误操作");
        body.setStoreCode("PRJ108065");
        body.setSiteCode("AE");

        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);

        assertThatThrownBy(() -> controller.createExcelImportSource(body, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).createExcelUploadSource(context, body);
    }

    @Test
    void bossCanRevokeAuthorization() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.noAuthorization(context);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.revokeAuthorization(context, 91001L)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.revokeAuthorization(91001L, request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("not_authorized");
        verify(service).revokeAuthorization(context, 91001L);
    }

    @Test
    void bossCanStartInitialBackfill() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.authorizedDev(context, 91001L);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.runInitialBackfill(context)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.runInitialBackfill(request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("authorized");
        verify(service).runInitialBackfill(context);
    }

    @Test
    void operationsCannotStartInitialBackfill() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(408L, BusinessAccountType.OPERATOR, "运营", 3);

        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);

        assertThatThrownBy(() -> controller.runInitialBackfill(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).runInitialBackfill(context);
    }

    @Test
    void bossCanRunManualRefresh() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(307L, BusinessAccountType.BOSS, "老板", 1);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.authorizedDev(context, 91001L);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.runManualRefresh(context)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.runManualRefresh(request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("authorized");
        verify(service).runManualRefresh(context);
    }

    @Test
    void operationsManagerCanRunManualRefresh() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(408L, BusinessAccountType.OPERATOR, "运营管理", 2);
        Ali1688HistoricalOrderWorkbenchView expected = Ali1688HistoricalOrderWorkbenchView.authorizedDev(context, 91001L);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.runManualRefresh(context)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView view = controller.runManualRefresh(request);

        assertThat(view.getAuthorization().getStatus()).isEqualTo("authorized");
        verify(service).runManualRefresh(context);
    }

    @Test
    void operationsCannotRunManualRefresh() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(409L, BusinessAccountType.OPERATOR, "运营", 3);

        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);

        assertThatThrownBy(() -> controller.runManualRefresh(request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).runManualRefresh(context);
    }

    @Test
    void orderDetailDelegatesThroughReadPermissionAndKeepsSafeRedactionModel() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(409L, BusinessAccountType.OPERATOR, "运营", 3);
        Ali1688HistoricalOrderWorkbenchView.OrderDetailView expected =
                new Ali1688HistoricalOrderWorkbenchView.OrderDetailView();
        Ali1688HistoricalOrderQuery detailQuery = Ali1688HistoricalOrderQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                "PRJ108065",
                "AE",
                null,
                null
        );
        Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView sensitiveFields =
                new Ali1688HistoricalOrderWorkbenchView.SensitiveFieldsView();
        sensitiveFields.setRedactionLevel("hidden");
        sensitiveFields.setReceiverPhone("已隐藏");
        expected.setSensitiveFields(sensitiveFields);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.orderDetail(context, 93001L, detailQuery)).thenReturn(expected);

        Ali1688HistoricalOrderWorkbenchView.OrderDetailView view =
                controller.orderDetail(93001L, "PRJ108065", "AE", request);

        assertThat(view.getSensitiveFields().getRedactionLevel()).isEqualTo("hidden");
        assertThat(view.getSensitiveFields().getReceiverPhone()).isEqualTo("已隐藏");
        verify(service).orderDetail(context, 93001L, detailQuery);
    }

    @Test
    void operationsCanLinkAssignedProductLineThroughHistoricalOrderApi() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(409L, BusinessAccountType.OPERATOR, "运营", 3);
        Ali1688HistoricalOrderProductLinkView.LinkRequest body =
                new Ali1688HistoricalOrderProductLinkView.LinkRequest();
        body.setAssignmentId(99001L);
        body.setSkuParent("CANMAN-AE-SKU-001");
        Ali1688HistoricalOrderProductLinkRow row = new Ali1688HistoricalOrderProductLinkRow();
        row.setAssignmentId(99001L);
        row.setSkuParent("CANMAN-AE-SKU-001");
        Ali1688HistoricalOrderProductLinkView.LinkResult expected =
                Ali1688HistoricalOrderProductLinkView.LinkResult.linked(row);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.linkProductLine(context, body)).thenReturn(expected);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                controller.linkProductLine(body, request);

        assertThat(result.getStatus()).isEqualTo("linked");
        assertThat(result.getAssignmentId()).isEqualTo(99001L);
        assertThat(result.getDisplayText()).isEqualTo("已关联: CANMAN-AE-SKU-001");
        verify(service).linkProductLine(context, body);
    }

    @Test
    void operationsCanUnlinkAssignedProductLineThroughHistoricalOrderApi() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(409L, BusinessAccountType.OPERATOR, "运营", 3);
        Ali1688HistoricalOrderProductLinkView.LinkResult expected =
                Ali1688HistoricalOrderProductLinkView.LinkResult.unlinked(99001L);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.unlinkProductLine(context, 99001L)).thenReturn(expected);

        Ali1688HistoricalOrderProductLinkView.LinkResult result =
                controller.unlinkProductLine(99001L, request);

        assertThat(result.getStatus()).isEqualTo("unlinked");
        assertThat(result.getAssignmentId()).isEqualTo(99001L);
        verify(service).unlinkProductLine(context, 99001L);
    }

    @Test
    void operationsCanReadProductLinkAuditRecordsThroughHistoricalOrderApi() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(409L, BusinessAccountType.OPERATOR, "运营", 3);
        Ali1688HistoricalOrderProductLinkAuditRow auditRow = new Ali1688HistoricalOrderProductLinkAuditRow();
        auditRow.setId(101001L);
        auditRow.setAssignmentId(99001L);
        auditRow.setActionType("relink");
        auditRow.setOldSkuParent("OLD-SKU");
        auditRow.setNewSkuParent("NEW-SKU");
        List<Ali1688HistoricalOrderProductLinkView.AuditView> expected =
                List.of(Ali1688HistoricalOrderProductLinkView.AuditView.fromRow(auditRow));

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.listProductLinkAudits(context, 99001L)).thenReturn(expected);

        List<Ali1688HistoricalOrderProductLinkView.AuditView> result =
                controller.listProductLinkAudits(99001L, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getActionType()).isEqualTo("relink");
        assertThat(result.get(0).getOldSkuParent()).isEqualTo("OLD-SKU");
        assertThat(result.get(0).getNewSkuParent()).isEqualTo("NEW-SKU");
        verify(service).listProductLinkAudits(context, 99001L);
    }

    @Test
    void procurementCanReadSkuPurchaseHistoryThroughHistoricalOrderApi() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = context(409L, BusinessAccountType.OPERATOR, "采购", 3);
        Ali1688SkuPurchaseHistoryView.ItemView item = new Ali1688SkuPurchaseHistoryView.ItemView();
        item.setStoreCode("PRJ108065");
        item.setSiteCode("AE");
        item.setSkuParent("CANMAN-AE-SKU-001");
        item.setPurchaseCount(2);
        Ali1688SkuPurchaseHistoryView expected =
                Ali1688SkuPurchaseHistoryView.of(List.of(item), 1, 20, 1);

        when(serviceProvider.getIfAvailable()).thenReturn(service);
        when(accessResolver.requireBusinessContext(request, BusinessCapability.ALI1688_HISTORICAL_ORDERS))
                .thenReturn(context);
        when(service.listSkuPurchaseHistory(
                org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.argThat(argument ->
                        "PRJ108065".equals(argument.getStoreCode())
                                && "AE".equals(argument.getSiteCode())
                                && "CANMAN".equals(argument.getKeyword())
                                && "linked".equals(argument.getLinkStatus())
                                && "2026-05-01".equals(argument.getPurchaseTimeFrom())
                                && "2026-05-28".equals(argument.getPurchaseTimeTo())
                                && argument.getPage() == 1
                                && argument.getPageSize() == 20
                )
        )).thenReturn(expected);

        Ali1688SkuPurchaseHistoryView result =
                controller.skuPurchaseHistory(
                        "PRJ108065",
                        "AE",
                        "CANMAN",
                        "linked",
                        "2026-05-01",
                        "2026-05-28",
                        1,
                        20,
                        request
                );

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getSkuParent()).isEqualTo("CANMAN-AE-SKU-001");
        assertThat(result.getItems().get(0).getPurchaseCount()).isEqualTo(2);
        verify(service).listSkuPurchaseHistory(
                org.mockito.ArgumentMatchers.eq(context),
                org.mockito.ArgumentMatchers.any(Ali1688SkuPurchaseHistoryQuery.class)
        );
    }

    @Test
    void serviceUnavailableIsReportedExplicitly() {
        when(serviceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> controller.workbench(new MockHttpServletRequest()))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    private BusinessAccessContext context(Long userId, BusinessAccountType accountType, String roleName, int level) {
        return BusinessAccessContext.builder()
                .sessionUserId(userId)
                .businessOwnerUserId(accountType == BusinessAccountType.BOSS ? userId : 307L)
                .accountType(accountType)
                .roleName(roleName)
                .roleLevel(level)
                .menuPaths(Set.of("/purchase/ali1688-orders"))
                .build();
    }
}
