package com.nuono.next.procurement.aliorder;

import static com.nuono.next.procurement.aliorder.Ali1688Dp10OrderHeaderIdentityTestSupport.activeIdentity;
import static com.nuono.next.procurement.aliorder.Ali1688Dp10OrderHeaderIdentityTestSupport.stubCanonicalOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Ali1688HistoricalOrderFactPersistenceTest {
    @Test
    void compatibilityLookupReusesCanonicalIdsWithoutAllocatingDuplicates() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper compatibility = mock(Ali1688Dp10FactLookupMapper.class);
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization(99_999L);
        stubCanonicalOrder(compatibility, activeIdentity(93_001L, "legacy:ORDER-1"));
        when(compatibility.selectCanonicalItemIdByStableTuple(
                93_001L, "OFFER-1", "SKU-1", "", "", 0)).thenReturn(94_001L);
        when(compatibility.selectCanonicalLogisticsId(93_001L, 94_001L))
                .thenReturn(95_001L);
        when(compatibility.countDp10ChildFinalizeFence(any(), any())).thenReturn(1);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, compatibility);
        DataPullTask task = task();
        Ali1688Dp10ApplySlice slice = slice(order(), 0);

        Ali1688HistoricalOrderFactPersistence.SegmentResult result =
                persistence.persistSegment(task, authorization, slice, 20);

        verify(mapper, never()).nextOrderId();
        verify(mapper, never()).nextOrderItemId();
        verify(mapper, never()).nextOrderLogisticsId();
        ArgumentCaptor<Ali1688HistoricalOrderRow> order =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderItemRow> item =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemRow.class);
        ArgumentCaptor<Ali1688HistoricalOrderLogisticsRow> logistics =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderLogisticsRow.class);
        verify(mapper).upsertOrder(order.capture());
        verify(mapper).upsertOrderItem(item.capture());
        verify(mapper).upsertOrderLogistics(logistics.capture());
        assertThat(order.getValue().getId()).isEqualTo(93_001L);
        assertThat(order.getValue().getAuthorizationId()).isEqualTo(99_999L);
        assertThat(item.getValue().getId()).isEqualTo(94_001L);
        assertThat(logistics.getValue().getId()).isEqualTo(95_001L);
        assertThat(result.getNextItemCursor()).isEqualTo(1);
        assertThat(result.getFactRows()).isEqualTo(2);
        assertThat(order.getValue().getOrderNaturalKey())
                .matches("ALI1688_ORDER-[0-9a-f]{64}");
        verify(compatibility).selectCanonicalItemIdByStableTuple(
                93_001L, "OFFER-1", "SKU-1", "", "", 0);
        verify(compatibility).softRetireDp10LogisticsMissingFromAuthoritativeSet(
                eq(task), eq(slice), eq(93_001L), anyList());
        verify(compatibility).softRetireDp10ItemsMissingFromAuthoritativeSet(
                eq(task), eq(slice), eq(93_001L), anyList());
    }
    @Test
    void providerIdentityFallsBackToStableTupleInsideExistingOrder() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper compatibility = mock(Ali1688Dp10FactLookupMapper.class);
        stubCanonicalOrder(compatibility, activeIdentity(93_001L, "legacy:ORDER-1"));
        when(compatibility.selectCanonicalItemIdByStableTuple(
                93_001L, "OFFER-1", "SKU-1", "", "", 0)).thenReturn(94_101L);
        when(compatibility.countDp10ChildFinalizeFence(any(), any())).thenReturn(1);
        Ali1688HistoricalOrderProvider.OrderSnapshot order = order();
        order.getItems().get(0).setProviderSubOrderId("SUBORDER-1");
        order.getItems().get(0).setLogisticsCompany(null);
        order.getItems().get(0).setTrackingNo(null);
        DataPullTask task = task();
        Ali1688Dp10ApplySlice slice = slice(order, 0);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, compatibility);

        persistence.persistSegment(task, authorization(99_999L), slice, 20);

        verify(compatibility).selectCanonicalItemIdByStableTuple(
                93_001L, "OFFER-1", "SKU-1", "", "", 0);
        verify(mapper, never()).nextOrderId();
        verify(mapper, never()).nextOrderItemId();
    }
    @Test
    void duplicateProviderIdentityKeepsFirstFactAndSkipsEveryLaterOccurrence() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper compatibility = mock(Ali1688Dp10FactLookupMapper.class);
        stubCanonicalOrder(compatibility, activeIdentity(93_001L, "legacy:ORDER-1"));
        when(mapper.nextOrderItemId()).thenReturn(94_001L);
        when(compatibility.countDp10ChildFinalizeFence(any(), any())).thenReturn(1);
        Ali1688HistoricalOrderProvider.OrderItemSnapshot first = item("OFFER-A", "SKU-A");
        first.setProviderSubOrderId("SUBORDER-1");
        first.setTitle("first-wins");
        Ali1688HistoricalOrderProvider.OrderItemSnapshot later = item("OFFER-B", "SKU-B");
        later.setProviderSubOrderId("SUBORDER-1");
        later.setTitle("must-not-overwrite");
        Ali1688HistoricalOrderProvider.OrderSnapshot order = order(first, later);
        DataPullTask task = task();
        Ali1688Dp10ApplySlice slice = slice(order, 0);
        Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();
        String providerKey = key(rows, authorization(91_001L), order, 0);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, compatibility);

        Ali1688HistoricalOrderFactPersistence.SegmentResult result =
                persistence.persistSegment(task, authorization(91_001L), slice, 20);

        assertThat(key(rows, authorization(91_001L), order, 1)).isEqualTo(providerKey);
        assertThat(result.getNextItemCursor()).isEqualTo(2);
        assertThat(result.getFactRows()).isEqualTo(1);
        ArgumentCaptor<Ali1688HistoricalOrderItemRow> written =
                ArgumentCaptor.forClass(Ali1688HistoricalOrderItemRow.class);
        verify(mapper).upsertOrderItem(written.capture());
        assertThat(written.getValue().getTitle()).isEqualTo("first-wins");
        verify(compatibility).softRetireDp10ItemsMissingFromAuthoritativeSet(
                task, slice, 93_001L, List.of(providerKey));
    }
    @Test
    void segmentStopsAtTwentyCombinedItemAndLogisticsRows() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        AtomicLong ids = new AtomicLong(100L);
        when(mapper.nextOrderId()).thenReturn(90L);
        when(mapper.nextOrderItemId()).thenAnswer(call -> ids.incrementAndGet());
        when(mapper.nextOrderLogisticsId()).thenAnswer(call -> ids.incrementAndGet());
        Ali1688HistoricalOrderProvider.OrderSnapshot order = order();
        List<Ali1688HistoricalOrderProvider.OrderItemSnapshot> items = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                    new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
            item.setOfferId("OFFER-" + index);
            item.setLogisticsCompany("ZTO");
            item.setTrackingNo("TRACK-" + index);
            items.add(item);
        }
        order.setItems(items);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(
                        mapper, mock(Ali1688Dp10FactLookupMapper.class));

        Ali1688HistoricalOrderFactPersistence.SegmentResult result =
                persistence.persistSegment(task(), authorization(91_001L), slice(order, 0), 20);

        assertThat(result.getNextItemCursor()).isEqualTo(10);
        assertThat(result.getFactRows()).isEqualTo(20);
        verify(mapper, times(10)).upsertOrderItem(org.mockito.ArgumentMatchers.any());
        verify(mapper, times(10)).upsertOrderLogistics(org.mockito.ArgumentMatchers.any());
    }
    @Test
    void dp10CompositionWithoutItsLookupMapperFailsBeforeWritingAnyFact() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, null);

        assertThatThrownBy(() -> persistence.persistSegment(
                task(), authorization(91_001L), slice(order(), 0), 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_FACT_LOOKUP_MAPPER_MISSING");

        verify(mapper, never()).upsertOrder(any());
    }
    @Test
    void finalSegmentUsesEmptyLogisticsSetWhenTheSealedOrderRemovedLogistics() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper compatibility = mock(Ali1688Dp10FactLookupMapper.class);
        stubCanonicalOrder(compatibility, activeIdentity(93_001L, "legacy:ORDER-1"));
        when(mapper.nextOrderItemId()).thenReturn(94_001L);
        when(compatibility.countDp10ChildFinalizeFence(any(), any())).thenReturn(1);
        Ali1688HistoricalOrderProvider.OrderSnapshot current = order();
        current.getItems().get(0).setLogisticsCompany(null);
        current.getItems().get(0).setTrackingNo(null);
        DataPullTask task = task();
        Ali1688Dp10ApplySlice slice = slice(current, 0);
        Ali1688HistoricalOrderFactRows rows = new Ali1688HistoricalOrderFactRows();
        String currentItemKey = key(rows, authorization(91_001L), current, 0);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, compatibility);

        persistence.persistSegment(task, authorization(91_001L), slice, 20);

        verify(compatibility).softRetireDp10LogisticsMissingFromAuthoritativeSet(
                task, slice, 93_001L, List.of());
        verify(compatibility).softRetireDp10ItemsMissingFromAuthoritativeSet(
                task, slice, 93_001L, List.of(currentItemKey));
    }

    @Test
    void staleFinalizationFenceFailsClosedBeforeAnyRetirement() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper compatibility = mock(Ali1688Dp10FactLookupMapper.class);
        stubCanonicalOrder(compatibility, activeIdentity(93_001L, "legacy:ORDER-1"));
        when(mapper.nextOrderItemId()).thenReturn(94_001L);
        when(mapper.nextOrderLogisticsId()).thenReturn(95_001L);
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, compatibility);
        DataPullTask task = task();
        Ali1688Dp10ApplySlice slice = slice(order(), 0);

        assertThatThrownBy(() -> persistence.persistSegment(
                task, authorization(91_001L), slice, 20))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("DP10_CHILD_FINALIZE_FENCE_STALE");

        verify(compatibility, never()).softRetireDp10LogisticsMissingFromAuthoritativeSet(
                any(), any(), any(), anyList());
        verify(compatibility, never()).softRetireDp10ItemsMissingFromAuthoritativeSet(
                any(), any(), any(), anyList());
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization(long id) {
        Ali1688HistoricalOrderAuthorizationRow row = new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(id);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setStatus("authorized");
        return row;
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot order() {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item = item("OFFER-1", "SKU-1");
        item.setLogisticsCompany("ZTO");
        item.setTrackingNo("TRACK-1");
        return order(item);
    }

    private Ali1688HistoricalOrderProvider.OrderSnapshot order(
            Ali1688HistoricalOrderProvider.OrderItemSnapshot... items
    ) {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("ORDER-1");
        order.setProviderModifiedAt(Instant.parse("2026-08-02T03:00:00Z"));
        order.setItems(List.of(items));
        return order;
    }

    private Ali1688HistoricalOrderProvider.OrderItemSnapshot item(
            String offerId,
            String skuId
    ) {
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setOfferId(offerId);
        item.setSkuId(skuId);
        return item;
    }

    private String key(
            Ali1688HistoricalOrderFactRows rows,
            Ali1688HistoricalOrderAuthorizationRow authorization,
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            int index
    ) {
        return rows.itemKey(
                307L, authorization, order, order.getItems().get(index),
                rows.identityOccurrence(order.getItems(), index));
    }

    private DataPullTask task() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 4, 0);
        DataPullTask task = DataPullTask.queued(
                10_001L, OperationCode.DP10, "ALI1688_OPEN_API", 307L, null,
                "member-307", null, null, null, null, "scope-307", now.minusHours(1),
                "DP10:incremental:2026-08-02", "DP10_APPLY", now.minusHours(1));
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(now.plusMinutes(5));
        task.setFenceEpoch(4L);
        return task;
    }

    private Ali1688Dp10ApplySlice slice(
            Ali1688HistoricalOrderProvider.OrderSnapshot order,
            int itemCursor
    ) {
        return new Ali1688Dp10ApplySlice(
                1L, "CURRENT", 1, 0, itemCursor, order);
    }
}
