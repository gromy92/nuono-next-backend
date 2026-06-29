package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnReceivedImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.FbnExportCreateCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyMissingAsnSyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyRematchCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyImportCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportCreateView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportListView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnExportStatusView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.FbnReceivedImportResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyImportResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyMissingAsnSyncResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyRematchResultView;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.permission.access.BusinessAccessResolver;
import com.nuono.next.permission.access.BusinessCapability;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseStatisticsControllerTest {

    @Mock
    private ObjectProvider<LocalDbOfficialWarehouseStatisticsService> serviceProvider;

    @Mock
    private ObjectProvider<OfficialWarehouseInventorySyncService> inventorySyncServiceProvider;

    @Mock
    private ObjectProvider<OfficialWarehouseFbnExportQueryService> fbnExportQueryServiceProvider;

    @Mock
    private ObjectProvider<OfficialWarehouseFbnReceivedReportImportService> fbnReceivedReportImportServiceProvider;

    @Mock
    private ObjectProvider<OfficialWarehouseScheduledDeliveryAccuracyImportService> scheduledDeliveryAccuracyImportServiceProvider;

    @Mock
    private ObjectProvider<OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService> scheduledDeliveryAccuracyAsnSyncServiceProvider;

    @Mock
    private OfficialWarehouseFbnExportQueryService fbnExportQueryService;

    @Mock
    private OfficialWarehouseFbnReceivedReportImportService fbnReceivedReportImportService;

    @Mock
    private OfficialWarehouseScheduledDeliveryAccuracyImportService scheduledDeliveryAccuracyImportService;

    @Mock
    private OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService scheduledDeliveryAccuracyAsnSyncService;

    @Mock
    private BusinessAccessResolver accessResolver;

    private OfficialWarehouseStatisticsController controller;

    @BeforeEach
    void setUp() {
        controller = new OfficialWarehouseStatisticsController(
                serviceProvider,
                inventorySyncServiceProvider,
                fbnExportQueryServiceProvider,
                fbnReceivedReportImportServiceProvider,
                scheduledDeliveryAccuracyImportServiceProvider,
                scheduledDeliveryAccuracyAsnSyncServiceProvider,
                accessResolver
        );
    }

    @Test
    void listFbnReportExportsRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        FbnExportListView expected = new FbnExportListView();
        expected.page = 1;
        expected.perPage = 20;
        when(fbnExportQueryServiceProvider.getIfAvailable()).thenReturn(fbnExportQueryService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(fbnExportQueryService.listExports(access, "STR108065-NSA", "SA", 1, 20)).thenReturn(expected);

        FbnExportListView result = controller.listFbnReportExports("STR108065-NSA", "SA", 1, 20, request);

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    @Test
    void createFbnReportExportRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        FbnExportCreateCommand command = new FbnExportCreateCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        command.exportCategoryCode = "fbn_inbound_scheduleddeliveryaccuracy";
        command.fromDate = "2026-06-20";
        command.toDate = "2026-06-20";
        FbnExportCreateView expected = new FbnExportCreateView();
        expected.exportCode = "EXP-SDA";
        when(fbnExportQueryServiceProvider.getIfAvailable()).thenReturn(fbnExportQueryService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(fbnExportQueryService.createExport(access, command)).thenReturn(expected);

        FbnExportCreateView result = controller.createFbnReportExport(command, request);

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    @Test
    void importFbnReceivedReportRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        FbnReceivedImportCommand command = new FbnReceivedImportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        FbnReceivedImportResultView expected = new FbnReceivedImportResultView();
        expected.importId = "623001";
        when(fbnReceivedReportImportServiceProvider.getIfAvailable()).thenReturn(fbnReceivedReportImportService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(fbnReceivedReportImportService.importByExportCode(access, "EXP4URWS7NYN", command)).thenReturn(expected);

        FbnReceivedImportResultView result = controller.importFbnReceivedReport("EXP4URWS7NYN", command, request);

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    @Test
    void importScheduledDeliveryAccuracyRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        ScheduledDeliveryAccuracyImportCommand command = new ScheduledDeliveryAccuracyImportCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        ScheduledDeliveryAccuracyImportResultView expected = new ScheduledDeliveryAccuracyImportResultView();
        expected.importId = "623101";
        when(scheduledDeliveryAccuracyImportServiceProvider.getIfAvailable())
                .thenReturn(scheduledDeliveryAccuracyImportService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(scheduledDeliveryAccuracyImportService.importByExportCode(access, "EXP-SDA", command))
                .thenReturn(expected);

        ScheduledDeliveryAccuracyImportResultView result =
                controller.importScheduledDeliveryAccuracyReport("EXP-SDA", command, request);

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    @Test
    void rematchScheduledDeliveryAccuracyRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        ScheduledDeliveryAccuracyRematchCommand command = new ScheduledDeliveryAccuracyRematchCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        ScheduledDeliveryAccuracyRematchResultView expected = new ScheduledDeliveryAccuracyRematchResultView();
        expected.importId = "623003";
        LocalDbOfficialWarehouseStatisticsService statisticsService =
                org.mockito.Mockito.mock(LocalDbOfficialWarehouseStatisticsService.class);
        when(serviceProvider.getIfAvailable()).thenReturn(statisticsService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(statisticsService.rematchScheduledDeliveryAccuracy(access, "623003", command)).thenReturn(expected);

        ScheduledDeliveryAccuracyRematchResultView result =
                controller.rematchScheduledDeliveryAccuracy("623003", command, request);

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    @Test
    void syncMissingScheduledDeliveryAccuracyAsnsRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        ScheduledDeliveryAccuracyMissingAsnSyncCommand command =
                new ScheduledDeliveryAccuracyMissingAsnSyncCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        command.dryRun = true;
        ScheduledDeliveryAccuracyMissingAsnSyncResultView expected =
                new ScheduledDeliveryAccuracyMissingAsnSyncResultView();
        expected.importId = "623003";
        when(scheduledDeliveryAccuracyAsnSyncServiceProvider.getIfAvailable())
                .thenReturn(scheduledDeliveryAccuracyAsnSyncService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(scheduledDeliveryAccuracyAsnSyncService.syncMissingAsns(access, "623003", command)).thenReturn(expected);

        ScheduledDeliveryAccuracyMissingAsnSyncResultView result =
                controller.syncMissingScheduledDeliveryAccuracyAsns("623003", command, request);

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    @Test
    void fbnReportExportStatusRequiresOfficialWarehouseStoreAccess() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        BusinessAccessContext access = access();
        FbnExportStatusView expected = new FbnExportStatusView();
        expected.exportCode = "EXP4URWS7NYN";
        when(fbnExportQueryServiceProvider.getIfAvailable()).thenReturn(fbnExportQueryService);
        when(accessResolver.requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA"))
                .thenReturn(access);
        when(fbnExportQueryService.exportStatus(access, "STR108065-NSA", "SA", "EXP4URWS7NYN", true))
                .thenReturn(expected);

        FbnExportStatusView result = controller.fbnReportExportStatus(
                "EXP4URWS7NYN",
                "STR108065-NSA",
                "SA",
                true,
                request
        );

        assertThat(result).isSameAs(expected);
        verify(accessResolver).requireStoreAccess(request, BusinessCapability.OFFICIAL_WAREHOUSE, "STR108065-NSA");
    }

    private static BusinessAccessContext access() {
        return BusinessAccessContext.builder()
                .sessionUserId(307L)
                .businessOwnerUserId(307L)
                .storeCodes(Set.of("STR108065-NSA"))
                .storeOwnerUserIds(Map.of("STR108065-NSA", 307L))
                .menuPaths(Set.of("/warehouse/official-warehouse-stock"))
                .build();
    }
}
