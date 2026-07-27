package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.UpdateDispatchTargetCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionPreviewCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseDispatchTargetProjectionTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void updateReadyItemDispatchTargetPersistsAndReturnsOriginalAndTargetPartition() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        when(mapper.updateBalanceDispatchTarget(900001L, 307L, "AE", "SEA", 307L)).thenReturn(1);
        UpdateDispatchTargetCommand command = new UpdateDispatchTargetCommand();
        command.targetSiteCode = "AE";
        command.targetTransportMode = "SEA";

        var view = service.updateReadyItemDispatchTarget(access(), "900001", command);

        assertThat(view.siteCode).isEqualTo("SA");
        assertThat(view.plannedTransportMode).isEqualTo("AIR");
        assertThat(view.targetSiteCode).isEqualTo("AE");
        assertThat(view.targetTransportMode).isEqualTo("SEA");
        verify(mapper).updateBalanceDispatchTarget(900001L, 307L, "AE", "SEA", 307L);
    }

    @Test
    void readyItemsAreGroupedByEffectiveTargetPartition() {
        FulfillmentBalanceRecord air = balance("CONFIRMED", "SUBMITTED");
        FulfillmentBalanceRecord sea = balance("CONFIRMED", "SUBMITTED");
        sea.id = 900002L;
        sea.targetTransportMode = "SEA";
        when(mapper.listReadyBalances(307L, Set.of("STR69486-NSA"), null, null))
                .thenReturn(List.of(air, sea));

        var views = service.listReadyItems(access(), null, null, null);

        assertThat(views).hasSize(2);
        assertThat(views).extracting(view -> view.targetTransportMode)
                .containsExactly("AIR", "SEA");
    }

    @Test
    void shippingBatchListExposesHistoricalPartitionSummaries() {
        ShippingBatchRecord record = shippingBatch();
        record.siteSummaryJson = "{\"SA\":5,\"AE\":2}";
        record.transportSummaryJson = "{\"AIR\":5,\"SEA\":2}";
        when(mapper.listShippingBatches(307L)).thenReturn(List.of(record));

        var view = service.listShippingBatches(access()).get(0);

        assertThat(view.siteCodes).containsExactly("SA", "AE");
        assertThat(view.transportModes).containsExactly("AIR", "SEA");
    }

    @Test
    void mobileDecisionUsesEffectiveTargetPartitionAfterOverride() {
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");
        balance.targetSiteCode = "AE";
        balance.targetTransportMode = "SEA";
        when(mapper.selectBalancesForUpdate(List.of(900001L))).thenReturn(List.of(balance));
        MobileShippingDecisionPreviewCommand command = new MobileShippingDecisionPreviewCommand();
        command.siteCode = "AE";
        command.transportMode = "SEA";
        ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
        source.fulfillmentBalanceId = 900001L;
        source.quantity = 5;
        command.sources = List.of(source);

        var preview = service.previewMobileShippingDecision(access(), command);

        assertThat(preview).isNotNull();
    }
}
