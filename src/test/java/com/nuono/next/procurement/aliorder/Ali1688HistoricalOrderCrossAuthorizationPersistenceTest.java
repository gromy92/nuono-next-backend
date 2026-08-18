package com.nuono.next.procurement.aliorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.datapull.persistence.DataPullTask;
import com.nuono.next.datapull.runtime.OperationCode;
import com.nuono.next.datapull.runtime.TaskState;
import com.nuono.next.infrastructure.mapper.Ali1688Dp10FactLookupMapper;
import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10ApplySlice;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688HistoricalOrderCrossAuthorizationPersistenceTest {
    @Test
    void stableTupleOccurrenceIgnoresRepeatedProviderIdentityRows() {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot first = item("SUB-1");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot repeated = item("SUB-1");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot second = item("SUB-2");

        int occurrence = new Ali1688HistoricalOrderFactRows().stableTupleOccurrence(
                List.of(first, repeated, second), 2);

        assertThat(occurrence).isEqualTo(2);
    }

    @Test
    void runtimeReusesOrderAndProviderItemWhenAuthorizationAndSourceChange() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper facts = mock(Ali1688Dp10FactLookupMapper.class);
        when(facts.selectCanonicalOrderHeadersForUpdate(
                eq(307L), eq("ALI1688_OPEN_API"), eq("new-member"),
                eq("ORDER-1"), anyString())).thenReturn(List.of(identity(
                        "ALI1688_EXCEL_UPLOAD", "old-member")));
        when(facts.selectCanonicalItemIdByStableTuple(
                93_001L, "OFFER-1", "SKU-1", "", "", 0)).thenReturn(94_001L);
        when(facts.countDp10ChildFinalizeFence(any(), any())).thenReturn(1);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, facts);

        Ali1688HistoricalOrderFactPersistence.SegmentResult result =
                persistence.persistSegment(task(), authorization(), slice(), 20);

        assertThat(result.isBusinessSkipped()).isFalse();
        verify(mapper, never()).nextOrderId();
        verify(mapper, never()).nextOrderItemId();
        verify(mapper).upsertOrder(any());
        verify(mapper).upsertOrderItem(any());
        verify(facts).activateCanonicalItemIdentity(any(), eq(93_001L), anyString());
        verify(facts).softRetireDp10ItemsMissingFromAuthoritativeSet(
                any(), any(), eq(93_001L), anyList());
    }

    @Test
    void legacyReusesOrderAndProviderItemWhenAuthorizationAndSourceChange() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper facts = mock(Ali1688Dp10FactLookupMapper.class);
        when(facts.selectCanonicalOrderHeadersForUpdate(
                eq(307L), eq("ALI1688_OPEN_API"), eq("new-member"),
                eq("ORDER-1"), anyString())).thenReturn(List.of(identity(
                        "ALI1688_EXCEL_UPLOAD", "old-member")));
        when(facts.selectCanonicalItemIdByStableTuple(
                93_001L, "OFFER-1", "SKU-1", "", "", 0)).thenReturn(94_001L);
        LegacyAli1688HistoricalOrderFactWriter writer =
                new LegacyAli1688HistoricalOrderFactWriter(mapper, facts);

        LegacyAli1688HistoricalOrderFactWriter.WriteResult result =
                writer.write(307L, authorization(), slice().getOrder());

        assertThat(result.isSkipped()).isFalse();
        assertThat(result.getItemCount()).isEqualTo(1);
        verify(mapper, never()).nextOrderId();
        verify(mapper, never()).nextOrderItemId();
        verify(mapper).upsertOrder(any());
        verify(mapper).upsertOrderItem(any());
        verify(facts).activateCanonicalItemIdentity(any(), eq(93_001L), anyString());
    }

    private Ali1688Dp10OrderHeaderIdentityRow identity(
            String providerCode,
            String providerAccountId
    ) {
        Ali1688Dp10OrderHeaderIdentityRow row = new Ali1688Dp10OrderHeaderIdentityRow();
        row.setId(93_001L);
        row.setAuthorizationId(91_001L);
        row.setOrderNaturalKey("legacy-order-key");
        row.setProviderCode(providerCode);
        row.setProviderAccountId(providerAccountId);
        row.setProviderOrderNo("ORDER-1");
        row.setDeleted(false);
        return row;
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row =
                new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91_005L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("new-member");
        row.setStatus("authorized");
        return row;
    }

    private Ali1688Dp10ApplySlice slice() {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = item("SUB-1");
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("ORDER-1");
        order.setProviderModifiedAt(Instant.parse("2026-08-18T03:00:00Z"));
        order.setItems(List.of(item));
        return new Ali1688Dp10ApplySlice(1L, "CURRENT", 1, 0, 0, order);
    }

    private Ali1688HistoricalOrderProvider.OrderItemSnapshot item(String subOrderId) {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setProviderSubOrderId(subOrderId);
        item.setOfferId("OFFER-1");
        item.setSkuId("SKU-1");
        return item;
    }

    private DataPullTask task() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 18, 12, 0);
        DataPullTask task = DataPullTask.queued(
                10_001L, OperationCode.DP10, "ALI1688_OPEN_API", 307L, null,
                "new-member", null, null, null, null, "scope-307",
                now.minusHours(1), "DP10:2026-08-18", "DP10_APPLY", now.minusHours(1));
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(now.plusMinutes(5));
        task.setFenceEpoch(4L);
        return task;
    }
}
