package com.nuono.next.procurement.aliorder;

import static com.nuono.next.procurement.aliorder.Ali1688Dp10OrderHeaderIdentityTestSupport.activeIdentity;
import static com.nuono.next.procurement.aliorder.Ali1688Dp10OrderHeaderIdentityTestSupport.deletedIdentity;
import static com.nuono.next.procurement.aliorder.Ali1688Dp10OrderHeaderIdentityTestSupport.stubCanonicalOrder;
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
import com.nuono.next.procurement.aliorder.datapull.Ali1688Dp10FactSegmentResult;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class Ali1688Dp10OrderHeaderTombstoneTest {
    @Test
    void legacyKeyManualTombstoneSkipsBeforeAnyFactWrite() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper facts = mock(Ali1688Dp10FactLookupMapper.class);
        stubCanonicalOrder(facts, deletedIdentity(93_001L, "91001:ORDER-1"));
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, facts);

        Ali1688Dp10FactSegmentResult result = persistence.applySegment(
                task(), authorization(), slice(), 20);

        assertThat(result.isBusinessSkipped()).isTrue();
        assertThat(result.getBusinessSkipCode())
                .isEqualTo("DP10_ORDER_HEADER_MANUALLY_DELETED");
        verify(mapper, never()).nextOrderId();
        verify(mapper, never()).nextOrderItemId();
        verify(mapper, never()).nextOrderLogisticsId();
        verify(mapper, never()).upsertOrder(any());
        verify(mapper, never()).upsertOrderItem(any());
        verify(mapper, never()).upsertOrderLogistics(any());
        verify(facts, never()).activateCanonicalItemIdentity(any(), any(), any());
        verify(facts, never()).activateCanonicalLogisticsIdentity(
                any(), any(), any(), any());
        verify(facts, never()).softRetireDp10ItemsMissingFromAuthoritativeSet(
                any(), any(), any(), anyList());
        verify(facts, never()).softRetireDp10LogisticsMissingFromAuthoritativeSet(
                any(), any(), any(), anyList());
    }

    @Test
    void duplicateExternalIdentitySkipsWithoutPickingTheActiveRow() {
        Ali1688HistoricalOrderMapper mapper = mock(Ali1688HistoricalOrderMapper.class);
        Ali1688Dp10FactLookupMapper facts = mock(Ali1688Dp10FactLookupMapper.class);
        when(facts.selectCanonicalOrderHeadersForUpdate(
                eq(307L), eq("ALI1688_OPEN_API"), eq("member-307"),
                eq("ORDER-1"), anyString())).thenReturn(List.of(
                        activeIdentity(93_001L, "legacy:ORDER-1"),
                        deletedIdentity(93_002L, "91001:ORDER-1")));
        Ali1688HistoricalOrderFactPersistence persistence =
                new Ali1688HistoricalOrderFactPersistence(mapper, facts);

        Ali1688Dp10FactSegmentResult result = persistence.applySegment(
                task(), authorization(), slice(), 20);

        assertThat(result.isBusinessSkipped()).isTrue();
        assertThat(result.getBusinessSkipCode())
                .isEqualTo("DP10_ORDER_HEADER_IDENTITY_AMBIGUOUS");
        verify(mapper, never()).upsertOrder(any());
        verify(mapper, never()).upsertOrderItem(any());
        verify(mapper, never()).upsertOrderLogistics(any());
        verify(facts, never()).activateCanonicalItemIdentity(any(), any(), any());
    }

    private DataPullTask task() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 4, 4, 0);
        DataPullTask task = DataPullTask.queued(
                10_001L, OperationCode.DP10, "ALI1688_OPEN_API", 307L,
                null, "account", null, null, null, null, "scope",
                now.minusHours(1), "DP10:2026-08-04", "DP10_APPLY",
                now.minusHours(1));
        task.setState(TaskState.RUNNING);
        task.setLeaseOwner("worker-1");
        task.setLeaseUntil(now.plusMinutes(5));
        task.setFenceEpoch(4L);
        return task;
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row =
                new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(91_001L);
        row.setOwnerUserId(307L);
        row.setProviderCode("ALI1688_OPEN_API");
        row.setProviderAccountId("member-307");
        row.setStatus("authorized");
        return row;
    }

    private Ali1688Dp10ApplySlice slice() {
        Ali1688HistoricalOrderProvider.OrderSnapshot order =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        order.setProviderOrderNo("ORDER-1");
        order.setProviderModifiedAt(Instant.parse("2026-08-04T03:00:00Z"));
        Ali1688HistoricalOrderProvider.OrderItemSnapshot item =
                new Ali1688HistoricalOrderProvider.OrderItemSnapshot();
        item.setProviderSubOrderId("SUB-1");
        order.setItems(List.of(item));
        return new Ali1688Dp10ApplySlice(1L, "CURRENT", 1, 0, 0, order);
    }
}
