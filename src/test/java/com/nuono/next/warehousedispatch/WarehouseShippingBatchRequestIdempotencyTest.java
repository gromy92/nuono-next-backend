package com.nuono.next.warehousedispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.CreateShippingBatchCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchCommands.ShippingBatchSourceCommand;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.FulfillmentBalanceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingBatchSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionLineSourceRecord;
import com.nuono.next.warehousedispatch.WarehouseDispatchRecords.ShippingSuggestionOptionRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehouseShippingBatchRequestIdempotencyTest extends WarehouseDispatchServiceTestSupport {

    @Test
    void sameCanonicalRequestReplaysFullBatchAndChangedPayloadConflictsWithoutWritingAgain() {
        AtomicReference<ShippingBatchRecord> persistedBatch = new AtomicReference<>();
        List<ShippingBatchSourceRecord> persistedSources = new ArrayList<>();
        List<ShippingSuggestionOptionRecord> persistedOptions = new ArrayList<>();
        AtomicInteger sourceReads = new AtomicInteger();
        FulfillmentBalanceRecord balance = balance("CONFIRMED", "SUBMITTED");

        when(mapper.selectBalanceScopes(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance));
        when(mapper.selectShippingBatchByClientRequestId(307L, "batch-request-1"))
                .thenAnswer(invocation -> persistedBatch.get());
        when(mapper.isShippingBatchSourceScopeAuthorized(
                700001L,
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(true);
        when(mapper.selectAuthorizedBalancesForUpdate(
                List.of(900001L),
                Map.of("STR69486-NSA", 307L)
        )).thenReturn(List.of(balance));
        when(mapper.reserveBalance(900001L, 307L, 5, 307L)).thenReturn(1);
        when(mapper.nextShippingBatchId()).thenReturn(700001L);
        when(mapper.nextShippingBatchSourceId()).thenReturn(760001L);
        when(mapper.nextShippingSuggestionOptionId())
                .thenReturn(710001L, 710002L, 710003L, 710004L, 710005L);
        when(mapper.insertShippingBatch(any(ShippingBatchRecord.class), anyLong()))
                .thenAnswer(invocation -> {
                    persistedBatch.set(invocation.getArgument(0));
                    return 1;
                });
        when(mapper.insertShippingBatchSource(any(ShippingBatchSourceRecord.class), anyLong()))
                .thenAnswer(invocation -> {
                    persistedSources.add(invocation.getArgument(0));
                    return 1;
                });
        when(mapper.insertShippingSuggestionOption(any(ShippingSuggestionOptionRecord.class), anyLong()))
                .thenAnswer(invocation -> {
                    persistedOptions.add(invocation.getArgument(0));
                    return 1;
                });
        when(mapper.listShippingBatchSources(700001L))
                .thenAnswer(invocation -> sourceReads.getAndIncrement() == 0
                        ? List.of()
                        : List.copyOf(persistedSources));
        when(mapper.listShippingSuggestionOptions(700001L))
                .thenAnswer(invocation -> List.copyOf(persistedOptions));
        when(mapper.listShippingSuggestionLines(700001L)).thenReturn(List.of());
        when(mapper.listShippingSuggestionLineSources(700001L)).thenReturn(List.of());

        var first = service.createShippingBatch(
                access(),
                batchCommand("batch-request-1", "  urgent  ", 2, 3)
        );
        clearInvocations(mapper);

        var replay = service.createShippingBatch(
                access(),
                batchCommand("batch-request-1", "urgent", 5)
        );

        assertThat(replay.id).isEqualTo(first.id);
        assertThat(replay.batchNo).isEqualTo(first.batchNo);
        assertThat(replay.sources).hasSize(1);
        assertThat(replay.options).hasSize(5);
        assertThat(first.optionCount).isEqualTo(5);
        assertThat(replay.optionCount).isEqualTo(5);
        assertThatThrownBy(() -> service.createShippingBatch(
                access(),
                batchCommand("batch-request-1", "urgent", 6)
        ))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");
        assertThatThrownBy(() -> service.createShippingBatch(
                access(),
                batchCommand("batch-request-1", "changed", 5)
        ))
                .isInstanceOf(WarehouseRequestConflictException.class)
                .hasMessageContaining("同一客户端请求号");

        verify(mapper, never()).selectAuthorizedBalancesForUpdate(any(), any());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), any(), anyLong());
        verify(mapper, never()).nextShippingBatchId();
        verify(mapper, never()).nextShippingBatchSourceId();
        verify(mapper, never()).nextShippingSuggestionOptionId();
        verify(mapper, never()).insertShippingBatch(any(ShippingBatchRecord.class), anyLong());
        verify(mapper, never()).insertShippingBatchSource(any(ShippingBatchSourceRecord.class), anyLong());
        verify(mapper, never()).insertShippingSuggestionOption(any(ShippingSuggestionOptionRecord.class), anyLong());
        verify(mapper, never()).insertShippingSuggestionLine(any(ShippingSuggestionLineRecord.class), anyLong());
        verify(mapper, never()).insertShippingSuggestionLineSource(
                any(ShippingSuggestionLineSourceRecord.class),
                anyLong()
        );
        verify(mapper, never()).nextOperationLogId();
    }

    @Test
    void missingOrOversizedRequestIdFailsBeforeReadingInventory() {
        assertThatThrownBy(() -> service.createShippingBatch(
                access(),
                batchCommand(" ", null, 5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("客户端请求号");
        assertThatThrownBy(() -> service.createShippingBatch(
                access(),
                batchCommand("x".repeat(101), null, 5)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100");

        verify(mapper, never()).selectBalanceScopes(any(), any());
        verify(mapper, never()).lockDispatchOwner(anyLong());
        verify(mapper, never()).reserveBalance(anyLong(), anyLong(), any(), anyLong());
    }

    private CreateShippingBatchCommand batchCommand(
            String clientRequestId,
            String remark,
            int... quantities
    ) {
        CreateShippingBatchCommand command = new CreateShippingBatchCommand();
        command.clientRequestId = clientRequestId;
        command.remark = remark;
        for (int quantity : quantities) {
            ShippingBatchSourceCommand source = new ShippingBatchSourceCommand();
            source.fulfillmentBalanceId = 900001L;
            source.quantity = quantity;
            command.sources.add(source);
        }
        return command;
    }
}
