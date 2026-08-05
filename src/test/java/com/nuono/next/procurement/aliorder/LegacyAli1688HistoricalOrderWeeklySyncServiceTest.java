package com.nuono.next.procurement.aliorder;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.infrastructure.mapper.Ali1688HistoricalOrderMapper;
import com.nuono.next.infrastructure.mapper.LegacyAli1688HistoricalOrderSyncMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegacyAli1688HistoricalOrderWeeklySyncServiceTest {
    @Mock
    private Ali1688HistoricalOrderMapper facts;
    @Mock
    private LegacyAli1688HistoricalOrderSyncMapper syncTasks;
    @Mock
    private Ali1688HistoricalOrderProvider provider;
    @Mock
    private LegacyAli1688HistoricalOrderFactWriter writer;

    @Test
    void oneBusinessInvalidOrderIsSkippedWithoutAbortingLaterOrders() {
        Ali1688HistoricalOrderAuthorizationRow authorization = authorization();
        Ali1688HistoricalOrderProvider.OrderSnapshot invalid =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        Ali1688HistoricalOrderProvider.OrderSnapshot valid =
                new Ali1688HistoricalOrderProvider.OrderSnapshot();
        Ali1688HistoricalOrderProvider.Page page =
                new Ali1688HistoricalOrderProvider.Page(List.of(invalid, valid));
        page.setHasMore(false);
        when(facts.selectAuthorizationById(307L, 501L)).thenReturn(authorization);
        when(facts.nextId("procurement_ali1688_order_sync_task", 92000L))
                .thenReturn(92001L);
        when(provider.fetchPage(authorization, null)).thenReturn(page);
        when(writer.write(307L, authorization, invalid)).thenReturn(
                LegacyAli1688HistoricalOrderFactWriter.WriteResult.skipped(
                        "INVALID_ORDER_FACT"
                )
        );
        when(writer.write(307L, authorization, valid)).thenReturn(
                LegacyAli1688HistoricalOrderFactWriter.WriteResult.written(1)
        );

        new LegacyAli1688HistoricalOrderWeeklySyncService(
                facts, syncTasks, provider, writer
        ).runScheduledWeekly(307L, 501L, 10003L);

        verify(writer).write(307L, authorization, invalid);
        verify(writer).write(307L, authorization, valid);
        verify(syncTasks).markSyncTaskPartialSuccess(
                eq(92001L), eq(2), eq(1), eq(1), eq("INVALID_ORDER_FACT"),
                anyString(), eq("{\"nextCursor\":null}"), eq(false), eq(false)
        );
    }

    private Ali1688HistoricalOrderAuthorizationRow authorization() {
        Ali1688HistoricalOrderAuthorizationRow row =
                new Ali1688HistoricalOrderAuthorizationRow();
        row.setId(501L);
        row.setOwnerUserId(307L);
        row.setProviderCode(
                LegacyAli1688HistoricalOrderWeeklySyncService.OPEN_API_PROVIDER_CODE
        );
        return row;
    }
}
