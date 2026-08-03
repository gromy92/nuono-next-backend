package com.nuono.next.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.nuono.next.noon.NoonAuthenticationRequiredException;
import com.nuono.next.noonauth.NoonAuthResumePolicy;
import com.nuono.next.noonauth.NoonAuthWaitRequest;
import org.junit.jupiter.api.Test;

class SalesSyncTaskServiceTest {

    @Test
    void emptyReportDoesNotMarkSiteOffersAsNotListedWithoutConfirmedCoverage() {
        RecordingSalesFactRepository salesFactRepository = new RecordingSalesFactRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                new RecordingSalesSyncTaskRepository(),
                emptyReportProvider(),
                new NoonSalesCsvImportService(new NoonProductViewsSalesReportParser(), salesFactRepository),
                salesFactRepository
        );

        SalesSyncTaskRecord task = service.triggerAndRun(command(SalesListingCoverageMode.NONE));

        assertEquals("empty", task.getStatus());
        assertEquals(0, salesFactRepository.notListedMarks);
    }

    @Test
    void confirmedEmptySiteCoverageMarksSiteOffersAsNotListed() {
        RecordingSalesFactRepository salesFactRepository = new RecordingSalesFactRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                new RecordingSalesSyncTaskRepository(),
                emptyReportProvider(),
                new NoonSalesCsvImportService(new NoonProductViewsSalesReportParser(), salesFactRepository),
                salesFactRepository
        );

        SalesSyncTaskRecord task = service.triggerAndRun(command(SalesListingCoverageMode.CONFIRMED_EMPTY_SITE));

        assertEquals("empty", task.getStatus());
        assertEquals(1, salesFactRepository.notListedMarks);
        assertEquals(307L, salesFactRepository.lastOwnerUserId);
        assertEquals("STR245027-NSA", salesFactRepository.lastStoreCode);
        assertEquals("SA", salesFactRepository.lastSiteCode);
        assertEquals(10003L, salesFactRepository.lastUpdatedBy);
    }

    @Test
    void authenticationFailureWaitsAndPreservesExactTaskForAutomaticResume() {
        RecordingSalesSyncTaskRepository repository = new RecordingSalesSyncTaskRepository();
        SalesSyncTaskService service = new SalesSyncTaskService(
                repository,
                request -> {
                    throw new NoonAuthenticationRequiredException("Noon cookie expired");
                },
                new NoonSalesCsvImportService(
                        new NoonProductViewsSalesReportParser(),
                        new RecordingSalesFactRepository()
                ),
                new RecordingSalesFactRepository()
        );
        List<NoonAuthWaitRequest> requests = new ArrayList<>();
        service.setAuthWaitQueue(request -> {
            requests.add(request);
            return Optional.of(77001L);
        });

        SalesSyncTaskRecord task = service.triggerAndRun(
                command(SalesListingCoverageMode.CONFIRMED_EMPTY_SITE)
        );

        assertEquals("waiting_authorization", task.getStatus());
        assertEquals(77001L, task.getAuthRecoveryId());
        assertEquals(1, requests.size());
        NoonAuthWaitRequest request = requests.get(0);
        assertEquals("SALES_SYNC", request.getSourceDomain());
        assertEquals(9001L, request.getSourceTaskId());
        assertEquals("REPORT_EXPORT", request.getCheckpoint());
        assertEquals(NoonAuthResumePolicy.AUTO_RESUME, request.getResumePolicy());
        assertEquals("STR245027-NSA", request.getStoreCode());
        assertEquals("SA", request.getSiteCode());
    }

    private static SalesSyncTaskCommand command(SalesListingCoverageMode listingCoverageMode) {
        return new SalesSyncTaskCommand(
                307L,
                50001L,
                "STR245027-NSA",
                "SA",
                LocalDate.of(2026, 5, 18),
                LocalDate.of(2026, 5, 24),
                10003L,
                "manual",
                listingCoverageMode
        );
    }

    private static NoonSalesReportProvider emptyReportProvider() {
        return (request) -> new NoonSalesReportPayload("empty.csv", headerOnlyCsv());
    }

    private static String headerOnlyCsv() {
        return String.join(",", List.of(
                "Visit_Date",
                "Partner_SKU",
                "Mp_Code",
                "SKU_CONFIG",
                "SKU",
                "Family",
                "Product_Type",
                "Product_Subtype",
                "Brand",
                "Currency_Code",
                "Product_Title",
                "Country_Code",
                "Your_Visitors",
                "Total_Visitors",
                "Gross_Units",
                "Shipped_Units",
                "Cancelled_Units",
                "Revenue_Shipped",
                "Buy_Box_Visitor_Percentage",
                "Conversion_Visitors_Percentage",
                "ASP_shipped_Percentage"
        ));
    }

    private static final class RecordingSalesSyncTaskRepository implements SalesSyncTaskRepository {
        private SalesSyncTaskRecord task;

        @Override
        public SalesSyncTaskRecord createQueued(SalesSyncTaskCommand command) {
            task = SalesSyncTaskRecord.queued(9001L, command);
            return task;
        }

        @Override
        public boolean claimRunning(Long taskId) {
            if (task == null || !"queued".equals(task.getStatus())) {
                return false;
            }
            task = task.withStatus("running");
            return true;
        }

        @Override
        public SalesSyncTaskRecord markSucceeded(Long taskId, NoonSalesCsvImportResult result) {
            task = task.succeeded(result);
            return task;
        }

        @Override
        public SalesSyncTaskRecord markFailed(Long taskId, String failureReason) {
            task = task.failed(failureReason);
            return task;
        }

        @Override
        public SalesSyncTaskRecord markWaitingForAuthorization(Long taskId, Long recoveryId) {
            task = task.waitingForAuthorization(recoveryId);
            return task;
        }

        @Override
        public List<SalesSyncTaskRecord> listQueued(int limit) {
            return task != null && "queued".equals(task.getStatus()) ? List.of(task) : List.of();
        }

        @Override
        public SalesSyncTaskRecord findById(Long taskId) {
            return task;
        }
    }

    private static final class RecordingSalesFactRepository implements SalesFactRepository {
        private final List<SalesImportBatch> savedBatches = new ArrayList<>();
        private int notListedMarks;
        private Long lastOwnerUserId;
        private String lastStoreCode;
        private String lastSiteCode;
        private Long lastUpdatedBy;

        @Override
        public long saveBatch(SalesImportBatch batch) {
            savedBatches.add(batch);
            return 10012L;
        }

        @Override
        public void upsert(DailySalesFact fact) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DailySalesFact> list(SalesFactQuery query) {
            return List.of();
        }

        @Override
        public void markSiteOffersNotListedForEmptyReport(
                Long ownerUserId,
                String storeCode,
                String siteCode,
                Long updatedBy
        ) {
            notListedMarks++;
            lastOwnerUserId = ownerUserId;
            lastStoreCode = storeCode;
            lastSiteCode = siteCode;
            lastUpdatedBy = updatedBy;
        }
    }
}
