package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.WarehouseDispatchMapper;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingTargetOptionCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseShippingBatchConcurrencyGuardTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void outboundCreationTreatsSelectedOptionChangeAsConflictWithoutSuccessAudit() {
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(shippingBatch());
        when(mapper.selectShippingSuggestionOptionById(710001L)).thenReturn(selectedOption());
        when(mapper.listShippingBatchSources(700001L)).thenReturn(List.of(shippingBatchSource()));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of(shippingSuggestionLine()));
        when(mapper.listShippingSuggestionLineSources(700001L))
                .thenReturn(List.of(shippingSuggestionLineSource()));
        when(mapper.nextOutboundOrderId()).thenReturn(800001L);
        when(mapper.nextOutboundOrderLineId()).thenReturn(820001L);
        when(mapper.nextOutboundOrderLineSourceId()).thenReturn(825001L);
        when(mapper.updateShippingBatchOutboundCreated(700001L, 307L, 710001L, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.createOutboundOrders(access(), "700001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("方案已变化");

        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), any(), any(), any(), any(), any()
        );
        InOrder order = inOrder(mapper);
        order.verify(mapper).selectShippingBatchByIdForUpdate(700001L);
        order.verify(mapper).selectShippingSuggestionOptionById(710001L);
        order.verify(mapper).insertOutboundOrder(any(), anyLong());
        order.verify(mapper).updateShippingBatchOutboundCreated(700001L, 307L, 710001L, 307L);
    }

    @Test
    void optionSelectionTreatsFinalStateChangeAsConflictWithoutSuccessAudit() {
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(shippingBatch());
        when(mapper.selectShippingSuggestionOptionById(710001L)).thenReturn(selectedOption());
        when(mapper.selectShippingSuggestionOption(700001L, 710001L, 307L)).thenReturn(1);
        when(mapper.updateShippingBatchSelectedOption(700001L, 307L, 710001L, 307L)).thenReturn(0);

        assertThatThrownBy(() -> service.selectShippingOption(access(), "700001", "710001"))
                .isInstanceOf(WarehouseInventoryStateConflictException.class)
                .hasMessageContaining("状态已变化");

        verify(mapper, never()).insertOperationLog(
                anyLong(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void optionSelectionLocksBatchBeforeChangingOptionChildren() {
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(shippingBatch());
        when(mapper.selectShippingSuggestionOptionById(710001L)).thenReturn(selectedOption());
        when(mapper.selectShippingSuggestionOption(700001L, 710001L, 307L)).thenReturn(1);
        when(mapper.updateShippingBatchSelectedOption(700001L, 307L, 710001L, 307L)).thenReturn(1);
        when(mapper.selectShippingBatchById(700001L)).thenReturn(shippingBatch());

        service.selectShippingOption(access(), "700001", "710001");

        InOrder order = inOrder(mapper);
        order.verify(mapper).selectShippingBatchByIdForUpdate(700001L);
        order.verify(mapper).selectShippingSuggestionOptionById(710001L);
        order.verify(mapper).clearSelectedShippingOptions(700001L, 307L);
        order.verify(mapper).selectShippingSuggestionOption(700001L, 710001L, 307L);
        order.verify(mapper).updateShippingBatchSelectedOption(700001L, 307L, 710001L, 307L);
    }

    @Test
    void targetOptionCreationLocksBatchBeforeCheckingTerminalState() {
        ShippingBatchRecord batch = shippingBatch();
        batch.status = "OUTBOUND_CREATED";
        when(mapper.selectShippingBatchByIdForUpdate(700001L)).thenReturn(batch);

        assertThatThrownBy(() -> service.createShippingTargetOption(
                access(),
                "700001",
                new CreateShippingTargetOptionCommand()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("草稿状态");

        verify(mapper).selectShippingBatchByIdForUpdate(700001L);
        verify(mapper, never()).listShippingBatchSources(700001L);
    }

    @Test
    void shippingBatchWriteInterfaceProvidesParentRowLock() {
        Method lockMethod = Arrays.stream(WarehouseDispatchMapper.class.getMethods())
                .filter(method -> method.getName().equals("selectShippingBatchByIdForUpdate"))
                .findFirst()
                .orElse(null);

        assertThat(lockMethod).as("shipping batch write lock method").isNotNull();
        assertThat(selectSql(lockMethod)).contains("FOR UPDATE");
    }

    @Test
    void outboundCompletionCasChecksExpectedSelectedOption() {
        Method updateMethod = Arrays.stream(WarehouseDispatchMapper.class.getMethods())
                .filter(method -> method.getName().equals("updateShippingBatchOutboundCreated"))
                .findFirst()
                .orElseThrow();

        assertThat(updateSql(updateMethod))
                .contains("status = 'OPTION_SELECTED'")
                .contains("selected_option_id = #{expectedOptionId}");
    }

    private String selectSql(Method method) {
        return String.join(" ", method.getAnnotation(Select.class).value()).replaceAll("\\s+", " ");
    }

    private String updateSql(Method method) {
        return String.join(" ", method.getAnnotation(Update.class).value()).replaceAll("\\s+", " ");
    }
}
