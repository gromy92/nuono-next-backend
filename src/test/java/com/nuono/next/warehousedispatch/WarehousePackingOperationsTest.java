package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.permission.access.BusinessAccessContext;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateDispatchPlanCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreatePackingListCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.DispatchPlanSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.MobileShippingDecisionPreviewCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.PackingBoxItemCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ReplacePackingBoxesCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.OutboundOrderRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxItemRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingBoxRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.PackingListRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchViews.PackingListView;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePackingOperationsTest extends WarehouseDispatchServiceTestSupport {

@Test
    void createPackingListDoesNotRequireProcurementQuoteSubmitStatus() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        CreatePackingListCommand command = new CreatePackingListCommand();
        command.remark = "仓库装箱";

        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.nextPackingListId()).thenReturn(830001L);
        when(mapper.markOutboundOrderPacking(800001L, 307L, 307L)).thenReturn(1);

        PackingListView view = service.createPackingList(access(), "800001", command);

        assertThat(view.id).isEqualTo("830001");
        assertThat(view.status).isEqualTo("DRAFT");
        verify(mapper, never()).countBlockingOutboundOrderLogisticsQuotes(800001L);
    }

@Test
    void createPackingListAllowsOutboundOrderAfterLogisticsQuoteIsSubmitted() {
        OutboundOrderRecord outboundOrder = outboundOrder();
        CreatePackingListCommand command = new CreatePackingListCommand();
        command.remark = "仓库装箱";

        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder);
        when(mapper.nextPackingListId()).thenReturn(830001L);
        when(mapper.markOutboundOrderPacking(800001L, 307L, 307L)).thenReturn(1);

        PackingListView view = service.createPackingList(access(), "800001", command);

        assertThat(view.id).isEqualTo("830001");
        assertThat(view.status).isEqualTo("DRAFT");
        verify(mapper).insertPackingList(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(830001L)
                        && row.outboundOrderId.equals(800001L)
                        && "仓库装箱".equals(row.remark)
        ), eq(307L));
    }

@Test
    void savePackingBoxAllowsDraftBoxWithoutSpecs() {
        PackingListRecord packingList = packingList();
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList);
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of());
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.nextPackingBoxId()).thenReturn(840001L);
        when(mapper.nextPackingBoxItemId()).thenReturn(850001L);

        PackingBoxCommand command = new PackingBoxCommand();
        command.boxNo = "箱1";
        command.lengthCm = "";
        command.widthCm = "";
        command.heightCm = "";
        command.grossWeightKg = "";
        PackingBoxItemCommand item = new PackingBoxItemCommand();
        item.outboundOrderLineId = 820001L;
        item.quantity = 1;
        command.items = List.of(item);

        PackingListView view = service.savePackingBox(access(), "830001", "箱1", command);

        assertThat(view.boxCount).isEqualTo(1);
        assertThat(view.packedQuantity).isEqualTo(1);
        assertThat(view.grossWeightKg).isEqualTo("0");
        assertThat(view.volumeCbm).isEqualTo("0.0000");
        assertThat(view.boxes).hasSize(1);
        assertThat(view.boxes.get(0).status).isEqualTo("DRAFT");
        assertThat(view.boxes.get(0).isSealed).isFalse();
        verify(mapper).insertPackingBox(org.mockito.ArgumentMatchers.argThat(row ->
                row.id.equals(840001L)
                        && "箱1".equals(row.boxNo)
                        && row.lengthCm == null
                        && row.widthCm == null
                        && row.heightCm == null
                        && row.grossWeightKg == null
                        && row.quantity == 1
        ), eq(307L));
        verify(mapper).updatePackingListTotals(
                eq(830001L),
                eq(307L),
                eq(1),
                eq(1),
                eq(BigDecimal.ZERO),
                eq(new BigDecimal("0.0000")),
                eq((String) null),
                eq(307L)
        );
    }

    @Test
    void savePackingBoxPersistsSealedStatus() {
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList());
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of());
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.nextPackingBoxId()).thenReturn(840001L);
        when(mapper.nextPackingBoxItemId()).thenReturn(850001L);

        PackingBoxCommand command = new PackingBoxCommand();
        command.boxNo = "箱1";
        command.status = "SEALED";
        PackingBoxItemCommand item = new PackingBoxItemCommand();
        item.outboundOrderLineId = 820001L;
        item.quantity = 1;
        command.items = List.of(item);

        PackingListView view = service.savePackingBox(access(), "830001", "箱1", command);

        assertThat(view.boxes).hasSize(1);
        assertThat(view.boxes.get(0).status).isEqualTo("SEALED");
        assertThat(view.boxes.get(0).isSealed).isTrue();
        verify(mapper).insertPackingBox(org.mockito.ArgumentMatchers.argThat(row ->
                "SEALED".equals(row.status)
        ), eq(307L));
    }

@Test
    void replacePackingBoxesAllowsClearingDraftBoxes() {
        PackingListRecord packingList = packingList();
        packingList.boxCount = 1;
        packingList.packedQuantity = 96;
        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        command.boxes = List.of();

        when(mapper.selectPackingListById(830001L)).thenReturn(packingList);
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));

        PackingListView view = service.replacePackingBoxes(access(), "830001", command);

        assertThat(view.boxCount).isEqualTo(0);
        assertThat(view.packedQuantity).isEqualTo(0);
        assertThat(view.boxes).isEmpty();
        verify(mapper).deletePackingBoxItems(830001L, 307L);
        verify(mapper).deletePackingBoxes(830001L, 307L);
        verify(mapper, never()).insertPackingBox(org.mockito.ArgumentMatchers.any(), eq(307L));
        verify(mapper).updatePackingListTotals(
                eq(830001L),
                eq(307L),
                eq(0),
                eq(0),
                eq(BigDecimal.ZERO),
                eq(new BigDecimal("0.0000")),
                eq((String) null),
                eq(307L)
        );
    }

@Test
    void confirmPackingListAllowsMissingGrossWeightWhenDimensionsAreComplete() {
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(packingBox(null)));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(packingBoxItem()));
        when(mapper.confirmPackingList(830001L, 307L, 307L)).thenReturn(1);
        when(mapper.markOutboundOrderPacked(800001L, 307L, 307L)).thenReturn(1);

        PackingListView view = service.confirmPackingList(access(), "830001");

        assertThat(view.status).isEqualTo("CONFIRMED");
        assertThat(view.boxCount).isEqualTo(1);
        assertThat(view.packedQuantity).isEqualTo(1);
        assertThat(view.boxes).hasSize(1);
        assertThat(view.boxes.get(0).status).isEqualTo("CONFIRMED");
        assertThat(view.boxes.get(0).isSealed).isTrue();
        verify(mapper).confirmPackingList(830001L, 307L, 307L);
    }

@Test
    void confirmPackingListRejectsMissingDimensions() {
        PackingBoxRecord box = packingBox(null);
        box.heightCm = null;
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(outboundOrderLine()));
        when(mapper.listPackingBoxes(830001L)).thenReturn(List.of(box));
        when(mapper.listPackingBoxItems(830001L)).thenReturn(List.of(packingBoxItem()));

        assertThatThrownBy(() -> service.confirmPackingList(access(), "830001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("确认装箱前必须填写箱规。");
        verify(mapper, never()).confirmPackingList(anyLong(), anyLong(), anyLong());
    }

    @Test
    void replacePackingBoxesRejectsMixedLogisticsPackingGroups() {
        OutboundOrderLineRecord firstLine = outboundOrderLine();
        firstLine.optionLineId = 720001L;
        OutboundOrderLineRecord secondLine = outboundOrderLine();
        secondLine.id = 820002L;
        secondLine.optionLineId = 720002L;
        secondLine.partnerSku = "SGGR175";
        ShippingSuggestionLineRecord firstSuggestion = shippingSuggestionLine();
        firstSuggestion.targetForwarderCode = "YT";
        firstSuggestion.routeCode = "YT-SAU-SEA-FBN-RUH";
        firstSuggestion.warningJson = "{\"quoteCargoCategoryCode\":\"YT-CAT-020\"}";
        ShippingSuggestionLineRecord secondSuggestion = shippingSuggestionLine();
        secondSuggestion.id = 720002L;
        secondSuggestion.targetForwarderCode = "ZD";
        secondSuggestion.routeCode = "ZD-SAU-SEA-FBN-RUH";
        secondSuggestion.warningJson = "{\"quoteCargoCategoryCode\":\"ZD-CAT-003\"}";
        PackingBoxCommand box = new PackingBoxCommand();
        box.boxNo = "箱1";
        PackingBoxItemCommand firstItem = new PackingBoxItemCommand();
        firstItem.outboundOrderLineId = 820001L;
        firstItem.quantity = 1;
        PackingBoxItemCommand secondItem = new PackingBoxItemCommand();
        secondItem.outboundOrderLineId = 820002L;
        secondItem.quantity = 1;
        box.items = List.of(firstItem, secondItem);
        ReplacePackingBoxesCommand command = new ReplacePackingBoxesCommand();
        command.boxes = List.of(box);
        when(mapper.selectPackingListById(830001L)).thenReturn(packingList());
        when(mapper.selectOutboundOrderById(800001L)).thenReturn(outboundOrder());
        when(mapper.listOutboundOrderLines(800001L)).thenReturn(List.of(firstLine, secondLine));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(firstSuggestion, secondSuggestion));

        assertThatThrownBy(() -> service.replacePackingBoxes(access(), "830001", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不同物流方案或报价类别不能装在同一箱");
        verify(mapper, never()).deletePackingBoxes(anyLong(), anyLong());
    }
}
