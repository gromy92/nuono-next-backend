package com.nuono.next.officialwarehouse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.OfficialWarehouseStatisticsMapper;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyMissingAsnSyncCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsCommands.ScheduledDeliveryAccuracyRematchCommand;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyMissingAsnSyncResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseStatisticsViews.ScheduledDeliveryAccuracyRematchResultView;
import com.nuono.next.officialwarehouse.OfficialWarehouseViews.AsnListSyncView;
import com.nuono.next.permission.access.BusinessAccessContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OfficialWarehouseScheduledDeliveryAccuracyAsnSyncServiceTest {

    @Mock
    private OfficialWarehouseStatisticsMapper mapper;

    @Mock
    private OfficialWarehouseAsnNumberSyncer asnNumberSyncer;

    @Mock
    private LocalDbOfficialWarehouseStatisticsService statisticsService;

    private OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService service;

    @BeforeEach
    void setUp() {
        service = new OfficialWarehouseScheduledDeliveryAccuracyAsnSyncService(
                mapper,
                asnNumberSyncer,
                statisticsService
        );
    }

    @Test
    void defaultsToDryRunWhenSyncingMissingScheduledDeliveryAsns() {
        BusinessAccessContext access = access();
        when(mapper.listMissingDeliveryAccuracyNoonAsnNumbers(307L, "STR108065-NSA", "SA", 623003L, 100))
                .thenReturn(List.of("A04540991PN", "A04544806PN"));
        AsnListSyncView sync = new AsnListSyncView();
        sync.fetched = 1;
        sync.skipped = 1;
        when(asnNumberSyncer.syncNoonAsnNumbers(
                eq(access),
                eq("STR108065-NSA"),
                eq("SA"),
                eq(List.of("A04540991PN", "A04544806PN")),
                eq(true)
        )).thenReturn(sync);
        ScheduledDeliveryAccuracyMissingAsnSyncCommand command =
                new ScheduledDeliveryAccuracyMissingAsnSyncCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";

        ScheduledDeliveryAccuracyMissingAsnSyncResultView result =
                service.syncMissingAsns(access, "623003", command);

        assertThat(result.importId).isEqualTo("623003");
        assertThat(result.dryRun).isTrue();
        assertThat(result.missingAsnCount).isEqualTo(2);
        assertThat(result.requestedAsnCount).isEqualTo(2);
        assertThat(result.foundAsnCount).isEqualTo(1);
        assertThat(result.notFoundAsnCount).isEqualTo(1);
        assertThat(result.skipped).isEqualTo(1);
        verify(statisticsService, never()).rematchScheduledDeliveryAccuracy(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void applyModeCanRematchAfterSync() {
        BusinessAccessContext access = access();
        when(mapper.listMissingDeliveryAccuracyNoonAsnNumbers(307L, "STR108065-NSA", "SA", 623003L, 50))
                .thenReturn(List.of("A04540991PN", "A04544806PN"));
        AsnListSyncView sync = new AsnListSyncView();
        sync.fetched = 2;
        sync.created = 1;
        sync.updated = 1;
        when(asnNumberSyncer.syncNoonAsnNumbers(
                eq(access),
                eq("STR108065-NSA"),
                eq("SA"),
                eq(List.of("A04540991PN", "A04544806PN")),
                eq(false)
        )).thenReturn(sync);
        ScheduledDeliveryAccuracyRematchResultView rematch = new ScheduledDeliveryAccuracyRematchResultView();
        rematch.importId = "623003";
        rematch.rematchedRows = 2;
        rematch.noLocalAsnRowsAfter = 76;
        when(statisticsService.rematchScheduledDeliveryAccuracy(
                eq(access),
                eq("623003"),
                org.mockito.ArgumentMatchers.any(ScheduledDeliveryAccuracyRematchCommand.class)
        )).thenReturn(rematch);

        ScheduledDeliveryAccuracyMissingAsnSyncCommand command =
                new ScheduledDeliveryAccuracyMissingAsnSyncCommand();
        command.storeCode = "STR108065-NSA";
        command.siteCode = "SA";
        command.limit = 50;
        command.dryRun = false;
        command.rematchAfterSync = true;

        ScheduledDeliveryAccuracyMissingAsnSyncResultView result =
                service.syncMissingAsns(access, "623003", command);

        assertThat(result.dryRun).isFalse();
        assertThat(result.foundAsnCount).isEqualTo(2);
        assertThat(result.created).isEqualTo(1);
        assertThat(result.updated).isEqualTo(1);
        assertThat(result.rematch).isSameAs(rematch);
        ArgumentCaptor<ScheduledDeliveryAccuracyRematchCommand> rematchCommand =
                ArgumentCaptor.forClass(ScheduledDeliveryAccuracyRematchCommand.class);
        verify(statisticsService).rematchScheduledDeliveryAccuracy(eq(access), eq("623003"), rematchCommand.capture());
        assertThat(rematchCommand.getValue().storeCode).isEqualTo("STR108065-NSA");
        assertThat(rematchCommand.getValue().siteCode).isEqualTo("SA");
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
