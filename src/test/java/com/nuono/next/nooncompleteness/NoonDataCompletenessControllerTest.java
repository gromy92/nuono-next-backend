package com.nuono.next.nooncompleteness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.nuono.next.permission.access.BusinessAccessDeniedException;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessAccountType;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class NoonDataCompletenessControllerTest {

    @Mock
    private NoonDataCompletenessOverviewService overviewService;

    @Mock
    private NoonDataGapPatrolViewService gapPatrolViewService;

    @Mock
    private BusinessAccessResolver businessAccessResolver;

    @Mock
    private NoonGapPatrolActionService actionService;

    @Mock
    private NoonDataCompletenessBootstrapService bootstrapService;

    @Mock
    private NoonCallStoreDataService noonCallStoreDataService;

    private NoonDataCompletenessController controller;
    private NoonCallStoreDataController noonCallController;

    @BeforeEach
    void setUp() {
        controller = new NoonDataCompletenessController(
                overviewService,
                gapPatrolViewService,
                businessAccessResolver,
                actionService,
                bootstrapService,
                new NoonDataCompletenessPermissionGuard()
        );
        noonCallController = new NoonCallStoreDataController(
                noonCallStoreDataService,
                businessAccessResolver,
                actionService,
                new NoonDataCompletenessPermissionGuard()
        );
    }

    @Test
    void overviewShouldRequireSystemReportCapabilityAndReturnView() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = reportContext();
        NoonDataCompletenessOverviewView expected = new NoonDataCompletenessOverviewView();
        expected.setRows(List.of());
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)).thenReturn(context);
        when(overviewService.overview(NoonDataCompletenessQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                null
        ))).thenReturn(expected);

        NoonDataCompletenessOverviewView actual = controller.overview(
                null,
                null,
                null,
                null,
                null,
                null,
                request
        );

        assertEquals(expected, actual);
    }

    @Test
    void gapsShouldRequireSystemReportCapabilityAndReturnEmptyState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = reportContext();
        NoonDataGapPatrolView expected = new NoonDataGapPatrolView();
        expected.setRows(List.of());
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)).thenReturn(context);
        when(gapPatrolViewService.gaps(NoonDataGapQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ))).thenReturn(expected);

        NoonDataGapPatrolView actual = controller.gaps(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                request
        );

        assertEquals(expected, actual);
    }

    @Test
    void retryActionShouldRequireStricterBossOrSystemAdminAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(reportContext(BusinessAccountType.OPERATOR));

        assertThrows(BusinessAccessDeniedException.class, () -> controller.retryGap(910001L, request));
    }

    @Test
    void retryActionShouldDelegateForBossAccount() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NoonGapPatrolActionResult expected = new NoonGapPatrolActionResult();
        expected.setGapId(910001L);
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(reportContext(BusinessAccountType.BOSS));
        when(actionService.retry(910001L)).thenReturn(expected);

        NoonGapPatrolActionResult actual = controller.retryGap(910001L, request);

        assertEquals(expected, actual);
    }

    @Test
    void auditExistingScopesShouldRequireActionAccessAndDelegateBootstrap() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NoonDataCompletenessBootstrapResult expected = new NoonDataCompletenessBootstrapResult(
                2,
                8,
                "已完成存量店铺数据完整性审计。"
        );
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(reportContext(BusinessAccountType.BOSS));
        when(bootstrapService.auditExistingScopes()).thenReturn(expected);

        NoonDataCompletenessBootstrapResult actual = controller.auditExistingScopes(request);

        assertEquals(expected, actual);
    }

    @Test
    void noonCallStoreDataShouldRequireSystemReportCapabilityAndReturnView() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext context = reportContext();
        NoonCallStoreDataView expected = new NoonCallStoreDataView();
        expected.setRows(List.of());
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS)).thenReturn(context);
        when(noonCallStoreDataService.view(NoonDataCompletenessQuery.fromRequest(
                null,
                null,
                null,
                null,
                null,
                null
        ))).thenReturn(expected);

        NoonCallStoreDataView actual = noonCallController.storeData(
                null,
                null,
                null,
                null,
                null,
                null,
                request
        );

        assertEquals(expected, actual);
    }

    @Test
    void noonCallStoreDataSyncShouldRequireBossOrSystemAdminAndDelegateCategory() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        NoonGapPatrolActionResult expected = new NoonGapPatrolActionResult();
        expected.setPlannedTaskCount(1);
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(reportContext(BusinessAccountType.BOSS));
        when(actionService.syncCategory(307L, "STR108065-NAE", "AE", NoonDataCategory.SALES_PRODUCT_VIEWS))
                .thenReturn(expected);

        NoonGapPatrolActionResult actual = noonCallController.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                "SALES_PRODUCT_VIEWS",
                request
        );

        assertEquals(expected, actual);
    }

    @Test
    void noonCallStoreDataSyncShouldRejectOperatorAction() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(businessAccessResolver.requireBusinessContext(request, BusinessCapability.SYSTEM_REPORTS))
                .thenReturn(reportContext(BusinessAccountType.OPERATOR));

        assertThrows(BusinessAccessDeniedException.class, () -> noonCallController.syncCategory(
                307L,
                "STR108065-NAE",
                "AE",
                "SALES_PRODUCT_VIEWS",
                request
        ));
    }

    private BusinessAccessContext reportContext() {
        return reportContext(BusinessAccountType.BOSS);
    }

    private BusinessAccessContext reportContext(BusinessAccountType accountType) {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .accountType(accountType)
                .menuPaths(java.util.Set.of("/system/reports/noon-data-completeness"))
                .build();
    }
}
