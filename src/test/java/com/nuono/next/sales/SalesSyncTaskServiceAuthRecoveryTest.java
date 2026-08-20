package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noonauth.NoonAuthWaitQueue;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SalesSyncTaskServiceAuthRecoveryTest {

    @Test
    void readOnlySalesTaskWaitsAndResumesFromItsExactCheckpoint() {
        SalesSyncTaskRepository repository = mock(SalesSyncTaskRepository.class);
        NoonSalesReportProvider reportProvider = mock(NoonSalesReportProvider.class);
        NoonSalesCsvImportService importService = mock(NoonSalesCsvImportService.class);
        NoonAuthWaitQueue queue = mock(NoonAuthWaitQueue.class);
        SalesSyncTaskCommand command = new SalesSyncTaskCommand(
                307L, 7001L, "STR7001-NAE", "AE",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), 307L, "MANUAL"
        );
        SalesSyncTaskRecord running = SalesSyncTaskRecord.queued(9001L, command).withStatus("running");
        SalesSyncTaskRecord waiting = running.waitingForAuthorization(91L);
        when(repository.claimRunning(9001L)).thenReturn(true);
        when(repository.findById(9001L)).thenReturn(running);
        when(reportProvider.fetch(any())).thenThrow(new NoonAuthenticationRequiredException("expired"));
        when(queue.enqueue(any())).thenReturn(Optional.of(91L));
        when(repository.markWaitingForAuthorization(9001L, 91L)).thenReturn(waiting);
        SalesSyncTaskService service = new SalesSyncTaskService(repository, reportProvider, importService);
        service.setAuthWaitQueue(queue);

        SalesSyncTaskRecord result = service.runQueued(9001L);

        assertEquals("waiting_authorization", result.getStatus());
        verify(queue).enqueue(NoonAuthWaitRequest.task(
                307L, null, "STR7001-NAE", "AE", "SALES_SYNC", 9001L,
                "REPORT_EXPORT", com.nuono.next.noonauth.NoonAuthResumePolicy.AUTO_RESUME
        ));
    }
}
